"""Render A/B variants of the trained JARVIS samples through voice_polish and measure them. CPU only.

Run with C:\\jarvis-apps\\GPT-SoVITS-v2pro-20250604\\runtime\\python.exe
"""
import glob
import json
import os
import sys
import time

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
import pyloudnorm
import soundfile as sf
from scipy.signal import welch

import voice_polish as vp

SAMPLES = r"P:\Coding\App Development\MyProjects\JARVIS\audio\samples"
SOURCE_DIR = os.path.join(SAMPLES, "gptsovits_trained_sovits-e8_gpt-e15")
OUT_ROOT = os.path.join(SAMPLES, "polish_ab")

VARIANTS = [
    ("A_original", dict(skip=True)),
    ("B_louder_only", dict(loud_only=True)),
    ("C_eq_compressed", dict()),
    ("D_bwe48k_eq_compressed", dict(extender=True)),
    ("E_bwe48k_exciter_eq_compressed", dict(extender=True, exciter=True)),
]


def measure(x, sr):
    f, p = welch(x, sr, nperseg=4096)
    total = p.sum()
    pdb = 10 * np.log10(p + 1e-20)
    return {"sr": sr, "lufs": round(pyloudnorm.Meter(sr).integrated_loudness(x), 1),
            "peak_dbfs": round(20 * np.log10(np.abs(x).max() + 1e-12), 1),
            "presence_2_5k_db": round(10 * np.log10(p[(f >= 2000) & (f < 5000)].sum() / total + 1e-12), 1),
            "air_above_8k_db": round(10 * np.log10(p[f >= 8000].sum() / total + 1e-12), 1),
            "bandwidth_50db_hz": int(f[np.where(pdb > pdb.max() - 50)[0].max()]),
            "centroid_hz": int((f * p).sum() / total)}


def main():
    files = sorted(glob.glob(os.path.join(SOURCE_DIR, "*.wav")))
    assert files, f"no samples in {SOURCE_DIR}"
    t = time.perf_counter()
    extender = vp.BandwidthExtender(device="cpu")
    print(f"AP-BWE loaded on CPU in {time.perf_counter() - t:.1f} s")

    report = {"source": SOURCE_DIR, "variants": {}}
    for name, opts in VARIANTS:
        out_dir = os.path.join(OUT_ROOT, name)
        os.makedirs(out_dir, exist_ok=True)
        metrics, timing = [], []
        for path in files:
            audio, sr = sf.read(path, dtype="int16")
            audio_s = len(audio) / sr
            t = time.perf_counter()
            if opts.get("skip"):
                y, out_sr = audio.astype(np.float64) / 32768.0, sr
            elif opts.get("loud_only"):
                x = audio.astype(np.float64) / 32768.0
                y, out_sr = vp.limit(vp.to_loudness(x, sr, -16.0), sr, -1.0), sr
            else:
                y, out_sr = vp.polish(audio, sr, extender=extender if opts.get("extender") else None,
                                      exciter=opts.get("exciter", False))
            timing.append((time.perf_counter() - t) * 1000 / audio_s)
            sf.write(os.path.join(out_dir, os.path.basename(path)), y.astype(np.float32), out_sr, subtype="PCM_16")
            metrics.append(measure(y, out_sr))
        mean = {k: round(float(np.mean([m[k] for m in metrics])), 1) for k in metrics[0]}
        mean["cpu_ms_per_audio_second"] = round(float(np.median(timing)), 1)
        report["variants"][name] = mean
        print(f"{name:34s} {mean}")

    with open(os.path.join(OUT_ROOT, "polish_ab_report.json"), "w", encoding="utf-8") as fh:
        json.dump(report, fh, indent=2)
    print("wrote", OUT_ROOT)


if __name__ == "__main__":
    main()
