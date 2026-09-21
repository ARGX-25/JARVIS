"""Step 2-3: diarize each vocal stem (after UVR5), export 30 s per speaker, score speakers against JARVIS.

Run with C:\\jarvis-venvs\\diarize\\Scripts\\python.exe. Writes C:\\jarvis-data\\voice\\diarization.json.
"""
import json
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("HF_HOME", r"C:\jarvis-models\hf")
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import numpy as np
import soundfile as sf
import torch
import torchaudio.functional as AF
from huggingface_hub import get_token
from pyannote.audio import Inference, Model, Pipeline

WORK = Path(r"C:\jarvis-data\voice")
PIPELINE_ID = "pyannote/speaker-diarization-community-1"
EMBEDDING_ID = "pyannote/wespeaker-voxceleb-resnet34-LM"
SOURCES = {
    "jarvis_source": WORK / "01_uvr" / "jarvis_source" / "vocal",
    "morevoice": WORK / "01_uvr" / "morevoice" / "vocal",
}
SAMPLE_SECONDS = 30.0
SR = 16_000
SAMPLES_DIR = WORK / "speaker_samples"
DEVICE = torch.device("cuda")


def load_mono(path, sr=None):
    audio, native_sr = sf.read(str(path), dtype="float32", always_2d=True)
    mono = torch.from_numpy(audio.mean(axis=1)).unsqueeze(0)
    if sr and native_sr != sr:
        mono = AF.resample(mono, native_sr, sr)
        native_sr = sr
    return mono, native_sr


def from_pretrained(cls, repo, token):
    try:
        return cls.from_pretrained(repo, token=token)
    except TypeError:                                   # pyannote < 4 used use_auth_token
        return cls.from_pretrained(repo, use_auth_token=token)


def subtract(segments, blocked):
    """Remove blocked (start, end) intervals from each (start, end) segment."""
    out = []
    for start, end in segments:
        pieces = [(start, end)]
        for b0, b1 in blocked:
            nxt = []
            for p0, p1 in pieces:
                if b1 <= p0 or b0 >= p1:
                    nxt.append((p0, p1))
                    continue
                if b0 > p0:
                    nxt.append((p0, b0))
                if b1 < p1:
                    nxt.append((b1, p1))
            pieces = nxt
        out.extend(p for p in pieces if p[1] - p[0] > 0.05)
    return out


def main():
    token = get_token()
    assert token, "no Hugging Face token in HF_HOME"
    t0 = time.perf_counter()
    pipeline = from_pretrained(Pipeline, PIPELINE_ID, token)
    pipeline.to(DEVICE)
    embedder = Inference(from_pretrained(Model, EMBEDDING_ID, token), window="whole")
    embedder.to(DEVICE)
    print(f"models loaded in {time.perf_counter() - t0:.1f} s", flush=True)

    def embed(wave16k, start, end):
        crop = wave16k[:, int(start * SR):int(end * SR)]
        vec = np.asarray(embedder({"waveform": crop, "sample_rate": SR}), dtype=np.float64).reshape(-1)
        return vec / (np.linalg.norm(vec) + 1e-9)

    # Clean JARVIS reference: the top-scoring clips selected from the first recording last session.
    selection = json.loads((WORK / "selection.json").read_text(encoding="utf-8"))
    refs = sorted([r for r in selection if not r["reject"] and 3.0 <= r["dur_s"] <= 10.0], key=lambda r: -r["score"])[:6]
    ref_vecs = []
    for r in refs:
        wave, _ = load_mono(WORK / "02_slices" / r["name"], SR)
        ref_vecs.append(embed(wave, 0, wave.shape[1] / SR))
    centroid = np.mean(ref_vecs, axis=0)
    centroid /= np.linalg.norm(centroid)
    print("reference clips:", [r["name"] for r in refs], flush=True)

    SAMPLES_DIR.mkdir(parents=True, exist_ok=True)
    report_path = WORK / "diarization.json"
    # Merge into an earlier run so diarizing one source doesn't erase the others.
    previous = json.loads(report_path.read_text(encoding="utf-8")) if report_path.exists() else {}
    report = {"pipeline": PIPELINE_ID, "embedding": EMBEDDING_ID,
              "reference_clips": [r["name"] for r in refs], "sources": previous.get("sources", {})}

    wanted = sys.argv[1:] or list(SOURCES)                # optional: diarize only the named sources
    for source, vocal_dir in SOURCES.items():
        if source not in wanted:
            continue
        vocal_path = max(vocal_dir.glob("*.wav"), key=os.path.getmtime)
        wave16k, _ = load_mono(vocal_path, SR)
        duration = wave16k.shape[1] / SR
        torch.cuda.reset_peak_memory_stats()
        t = time.perf_counter()
        output = pipeline({"waveform": wave16k, "sample_rate": SR})
        took = time.perf_counter() - t
        annotation = getattr(output, "speaker_diarization", output)
        overlaps = [(s.start, s.end) for s in annotation.get_overlap()]

        turns = [{"start": round(seg.start, 3), "end": round(seg.end, 3), "speaker": spk}
                 for seg, _, spk in annotation.itertracks(yield_label=True)]
        full_wave, full_sr = load_mono(vocal_path)          # native rate for listening samples
        speakers = {}
        for spk in sorted({t["speaker"] for t in turns}):
            spans = subtract([(t["start"], t["end"]) for t in turns if t["speaker"] == spk], overlaps)
            total = sum(e - s for s, e in spans)
            # speaker embedding: mean over the longest clean turns (>= 1.5 s)
            longest = sorted([sp for sp in spans if sp[1] - sp[0] >= 1.5], key=lambda sp: sp[0] - sp[1])[:20]
            vecs = [embed(wave16k, s, e) for s, e in longest]
            sim = float(np.mean(vecs, axis=0) @ centroid / (np.linalg.norm(np.mean(vecs, axis=0)) + 1e-9)) if vecs else None
            # 30 s listening sample from clean (non-overlapping) turns, in time order, 0.25 s silence between
            chunks, got = [], 0.0
            gap = torch.zeros(1, int(0.25 * full_sr))
            for s, e in sorted(spans):
                if got >= SAMPLE_SECONDS:
                    break
                take = min(e - s, SAMPLE_SECONDS - got)
                chunks += [full_wave[:, int(s * full_sr):int((s + take) * full_sr)], gap]
                got += take
            sample_path = SAMPLES_DIR / f"{source}_{spk}.wav"
            if chunks:
                sf.write(str(sample_path), torch.cat(chunks, dim=1).squeeze(0).numpy(), full_sr)
            speakers[spk] = {"clean_seconds": round(total, 1), "turns": len(spans),
                             "similarity_to_jarvis": None if sim is None else round(sim, 3),
                             "sample": sample_path.name if chunks else None}
        report["sources"][source] = {
            "vocal_stem": str(vocal_path), "duration_s": round(duration, 1), "diarization_s": round(took, 1),
            "peak_vram_mb": round(torch.cuda.max_memory_allocated() / 2**20),
            "overlap_seconds": round(sum(e - s for s, e in overlaps), 1),
            "overlaps": [[round(s, 3), round(e, 3)] for s, e in overlaps],
            "turns": turns, "speakers": speakers,
        }
        print(f"\n== {source}: {duration / 60:.1f} min diarized in {took:.0f} s, "
              f"overlap {report['sources'][source]['overlap_seconds']} s", flush=True)
        for spk, info in sorted(speakers.items(), key=lambda kv: -(kv[1]["similarity_to_jarvis"] or -1)):
            print(f"   {spk}: {info['clean_seconds']:6.1f} s clean in {info['turns']:3d} turns | "
                  f"similarity to JARVIS {info['similarity_to_jarvis']} | {info['sample']}", flush=True)

    (WORK / "diarization.json").write_text(json.dumps(report, indent=1), encoding="utf-8")
    print("\nwrote", WORK / "diarization.json")


if __name__ == "__main__":
    main()
