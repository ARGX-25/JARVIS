"""Steps 4-5: keep only JARVIS turns, discard overlap / bad length / music / reverb / noise, normalise loudness.

Run with C:\\jarvis-venvs\\diarize\\Scripts\\python.exe after diarize_sources.py.
Speaker choice comes from C:\\jarvis-data\\voice\\jarvis_speakers.json, e.g. {"jarvis_source": ["SPEAKER_00"], "morevoice": ["SPEAKER_02"]}.
If that file is missing, speakers whose similarity to the JARVIS reference is >= --auto-similarity are used and listed.
"""
import argparse
import json
import os
import sys
from pathlib import Path

os.environ.setdefault("HF_HOME", r"C:\jarvis-models\hf")
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import numpy as np
import pyloudnorm
import soundfile as sf
import torch
import torchaudio.functional as AF
from torchaudio.pipelines import SQUIM_OBJECTIVE

WORK = Path(r"C:\jarvis-data\voice")
OUT_SR = 32_000                       # GPT-SoVITS training rate
MERGE_GAP_S = 0.4                     # join same-speaker turns separated by a short pause
PAD_S = 0.05                          # keep word onsets/offsets, never reaching into another speaker's turn


def load_mono(path, sr):
    audio, native = sf.read(str(path), dtype="float32", always_2d=True)
    mono = torch.from_numpy(audio.mean(axis=1)).unsqueeze(0)
    return AF.resample(mono, native, sr) if native != sr else mono


def rms_db(x):
    return float(20 * np.log10(np.sqrt(np.mean(np.square(x))) + 1e-9))


def intersects(a0, a1, spans):
    return any(s < a1 and e > a0 for s, e in spans)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--min-s", type=float, default=2.0)
    ap.add_argument("--max-s", type=float, default=12.0)
    ap.add_argument("--bleed-db-max", type=float, default=-10.0, help="score level vs voice in the original mix")
    ap.add_argument("--sisdr-min", type=float, default=15.0, help="SQUIM SI-SDR estimate (dB)")
    ap.add_argument("--pesq-min", type=float, default=2.5, help="SQUIM wideband PESQ estimate")
    ap.add_argument("--stoi-min", type=float, default=0.90, help="SQUIM STOI estimate")
    ap.add_argument("--lufs", type=float, default=-20.0)
    ap.add_argument("--peak-dbfs", type=float, default=-1.0)
    ap.add_argument("--auto-similarity", type=float, default=0.55)
    args = ap.parse_args()

    diar = json.loads((WORK / "diarization.json").read_text(encoding="utf-8"))
    choice_file = WORK / "jarvis_speakers.json"
    if choice_file.exists():
        chosen = json.loads(choice_file.read_text(encoding="utf-8"))
        how = f"confirmed in {choice_file.name}"
    else:
        chosen = {src: [spk for spk, info in d["speakers"].items()
                        if (info["similarity_to_jarvis"] or -1) >= args.auto_similarity]
                  for src, d in diar["sources"].items()}
        how = f"automatic (similarity >= {args.auto_similarity}) - not yet confirmed by listening"
    print(f"JARVIS speakers ({how}): {chosen}")

    squim = SQUIM_OBJECTIVE.get_model().cuda().eval()
    meter = pyloudnorm.Meter(OUT_SR)
    clean_dir = WORK / "clean"
    clean_dir.mkdir(exist_ok=True)
    for old in clean_dir.glob("*.wav"):
        old.unlink()

    manifest, reasons_s = [], {}
    for src, d in diar["sources"].items():
        keep_speakers = set(chosen.get(src, []))
        if not keep_speakers:
            print(f"{src}: no JARVIS speaker chosen - skipped")
            continue
        vocal = load_mono(d["vocal_stem"], OUT_SR)[0].numpy()
        inst_path = max((Path(d["vocal_stem"]).parent.parent / "instrumental").glob("*.wav"), key=os.path.getmtime)
        inst = load_mono(inst_path, OUT_SR)[0].numpy()
        overlaps = [tuple(o) for o in d["overlaps"]]
        others = [(t["start"], t["end"]) for t in d["turns"] if t["speaker"] not in keep_speakers]

        # merge short same-speaker pauses, but never across another speaker or an overlap
        mine = sorted((t["start"], t["end"]) for t in d["turns"] if t["speaker"] in keep_speakers)
        merged = []
        for s, e in mine:
            if merged and s - merged[-1][1] <= MERGE_GAP_S and not intersects(merged[-1][1], s, others + overlaps):
                merged[-1] = (merged[-1][0], max(merged[-1][1], e))
            else:
                merged.append((s, e))

        def reject(reason, dur):
            reasons_s[reason] = reasons_s.get(reason, 0.0) + dur

        for s, e in merged:
            dur = e - s
            if intersects(s, e, overlaps):
                reject("overlapping speech (discarded whole)", dur); continue
            if dur < args.min_s:
                reject(f"shorter than {args.min_s:g} s", dur); continue
            if dur > args.max_s:
                reject(f"longer than {args.max_s:g} s", dur); continue
            p0 = s - PAD_S if not intersects(s - PAD_S, s, others) else s
            p1 = e + PAD_S if not intersects(e, e + PAD_S, others) else e
            a, b = max(0, int(p0 * OUT_SR)), min(len(vocal), int(p1 * OUT_SR))
            seg = vocal[a:b]
            bleed = rms_db(inst[a:b]) - rms_db(seg)
            with torch.no_grad():
                w16 = AF.resample(torch.from_numpy(seg).unsqueeze(0), OUT_SR, 16_000).cuda()
                stoi, pesq, sisdr = (float(v) for v in squim(w16))
            row = {"source": src, "start": round(p0, 3), "end": round(p1, 3), "dur_s": round((b - a) / OUT_SR, 2),
                   "bleed_db": round(bleed, 1), "squim_stoi": round(stoi, 3), "squim_pesq": round(pesq, 2),
                   "squim_sisdr": round(sisdr, 1)}
            if bleed > args.bleed_db_max:
                reject("music bleed", dur); row["kept"] = False
            elif sisdr < args.sisdr_min or pesq < args.pesq_min or stoi < args.stoi_min:
                reject("reverb / noise / effects (SQUIM)", dur); row["kept"] = False
            else:
                loud = meter.integrated_loudness(seg)
                out = pyloudnorm.normalize.loudness(seg, loud, args.lufs)
                peak = np.abs(out).max()
                limit = 10 ** (args.peak_dbfs / 20)
                if peak > limit:                      # scale down rather than clip
                    out = out * (limit / peak)
                name = f"{src}_{int(p0 * 1000):08d}_{int(p1 * 1000):08d}.wav"
                sf.write(str(clean_dir / name), out.astype(np.float32), OUT_SR, subtype="PCM_16")
                row.update(kept=True, file=name, lufs_before=round(loud, 1),
                           lufs_after=round(meter.integrated_loudness(out), 1))
            manifest.append(row)

    kept = [r for r in manifest if r.get("kept")]
    (WORK / "clean_manifest.json").write_text(json.dumps({"thresholds": vars(args), "speakers": chosen,
                                                          "speaker_choice": how, "rejected_seconds": reasons_s,
                                                          "segments": manifest}, indent=1), encoding="utf-8")
    print("\nrejected (seconds of JARVIS speech):")
    for reason, secs in sorted(reasons_s.items(), key=lambda kv: -kv[1]):
        print(f"  {secs:7.1f} s  {reason}")
    for src in diar["sources"]:
        secs = sum(r["dur_s"] for r in kept if r["source"] == src)
        print(f"{src}: {len([r for r in kept if r['source'] == src])} clean segments, {secs / 60:.2f} min")
    total = sum(r["dur_s"] for r in kept)
    print(f"\nCLEAN JARVIS-ONLY AUDIO: {len(kept)} segments, {total / 60:.2f} min -> {clean_dir}")
    if kept:
        for key in ("squim_sisdr", "squim_pesq", "squim_stoi", "bleed_db"):
            vals = [r[key] for r in manifest if key in r]
            print(f"  {key:12s} all measured p10/p50/p90: {np.percentile(vals, [10, 50, 90]).round(2)}")
    if total < 180:
        print("\nWARNING: under 3 minutes of clean JARVIS audio survived.")


if __name__ == "__main__":
    main()
