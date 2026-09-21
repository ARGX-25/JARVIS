"""JARVIS voice polish: louder, crisper GPT-SoVITS output.

numpy/scipy only (plus pyloudnorm, and torch for the optional AP-BWE bandwidth extension), so the same code runs in
the GPT-SoVITS runtime (Python 3.9) that synthesises the live voice. Written for whole-sentence buffers, which is
how JarvisVoice.synth() returns audio.
"""
import os
import sys

import numpy as np
import pyloudnorm
from scipy.ndimage import minimum_filter1d, uniform_filter1d
from scipy.signal import butter, resample_poly, sosfilt


# ----------------------------------------------------------------------------- filters (RBJ audio EQ cookbook)
def _normalise(b0, b1, b2, a0, a1, a2):
    return np.array([[b0 / a0, b1 / a0, b2 / a0, 1.0, a1 / a0, a2 / a0]])


def peaking(f0, gain_db, q, sr):
    a = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f0 / sr
    alpha = np.sin(w0) / (2 * q)
    cos = np.cos(w0)
    return _normalise(1 + alpha * a, -2 * cos, 1 - alpha * a, 1 + alpha / a, -2 * cos, 1 - alpha / a)


def high_shelf(f0, gain_db, sr, slope=1.0):
    a = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f0 / sr
    cos = np.cos(w0)
    alpha = np.sin(w0) / 2 * np.sqrt((a + 1 / a) * (1 / slope - 1) + 2)
    sa = 2 * np.sqrt(a) * alpha
    return _normalise(a * ((a + 1) + (a - 1) * cos + sa), -2 * a * ((a - 1) + (a + 1) * cos),
                      a * ((a + 1) + (a - 1) * cos - sa), (a + 1) - (a - 1) * cos + sa,
                      2 * ((a - 1) - (a + 1) * cos), (a + 1) - (a - 1) * cos - sa)


# ----------------------------------------------------------------------------- dynamics
def _frame_gain(x, sr, threshold_db, ratio, attack_ms, release_ms, detector=None, hop_ms=2.5):
    """Feed-forward compressor gain (linear, per sample). `detector` is the signal the level is measured on."""
    det = x if detector is None else detector
    hop = max(1, int(sr * hop_ms / 1000))
    frames = len(det) // hop + 1
    padded = np.pad(det, (0, frames * hop - len(det)))
    rms = np.sqrt(np.mean(padded.reshape(frames, hop) ** 2, axis=1) + 1e-12)
    level_db = 20 * np.log10(rms)
    over = np.maximum(level_db - threshold_db, 0.0)
    target_db = -over * (1 - 1 / ratio)
    att = np.exp(-hop_ms / attack_ms)
    rel = np.exp(-hop_ms / release_ms)
    smoothed = np.empty(frames)
    g = 0.0
    for i, t in enumerate(target_db):            # asymmetric one-pole: fast down, slow back up
        coef = att if t < g else rel
        g = coef * g + (1 - coef) * t
        smoothed[i] = g
    gain_db = np.interp(np.arange(len(det)), np.arange(frames) * hop + hop / 2, smoothed)
    return 10 ** (gain_db / 20)


def compress(x, sr, threshold_db=-20.0, ratio=3.0, attack_ms=5.0, release_ms=80.0):
    return x * _frame_gain(x, sr, threshold_db, ratio, attack_ms, release_ms)


def deess(x, sr, freq=6500.0, threshold_db=-30.0, ratio=4.0):
    """Split-band de-esser: only the sibilance band is turned down when it gets loud."""
    band_sos = butter(2, [freq * 0.75, min(freq * 1.5, sr / 2 * 0.95)], btype="bandpass", fs=sr, output="sos")
    band = sosfilt(band_sos, x)
    gain = _frame_gain(band, sr, threshold_db, ratio, attack_ms=1.0, release_ms=40.0)
    return x - band + band * gain


def limit(x, sr, ceiling_db=-1.0, lookahead_ms=5.0):
    """Look-ahead peak limiter: gain dips ahead of each peak instead of clipping it."""
    ceiling = 10 ** (ceiling_db / 20)
    need = np.minimum(1.0, ceiling / (np.abs(x) + 1e-12))
    width = max(1, int(sr * lookahead_ms / 1000))
    gain = uniform_filter1d(minimum_filter1d(need, size=2 * width + 1), size=2 * width + 1)
    return np.clip(x * gain, -ceiling, ceiling)


def to_loudness(x, sr, lufs):
    measured = pyloudnorm.Meter(sr).integrated_loudness(x)
    if not np.isfinite(measured):
        return x
    return x * 10 ** ((lufs - measured) / 20)


# ----------------------------------------------------------------------------- tone
def excite(x, sr, from_hz=2500.0, keep_above_hz=5000.0, drive=3.0, mix=0.2):
    """Harmonic exciter: saturate the upper-mid band and add back only the new harmonics above keep_above_hz."""
    up = resample_poly(x, 2, 1)                  # 2x oversampling keeps the new harmonics from aliasing
    sr2 = sr * 2
    band = sosfilt(butter(2, from_hz, btype="highpass", fs=sr2, output="sos"), up)
    peak = np.max(np.abs(band)) + 1e-9
    harmonics = np.tanh(drive * band / peak) * peak / np.tanh(drive)
    harmonics = sosfilt(butter(4, keep_above_hz, btype="highpass", fs=sr2, output="sos"), harmonics)
    return x + mix * resample_poly(harmonics, 1, 2)[: len(x)]


def eq(x, sr, highpass_hz=80.0, mud_hz=300.0, mud_db=-3.0, presence_hz=3500.0, presence_db=4.0,
       air_hz=9000.0, air_db=5.0):
    sos = np.vstack([
        butter(2, highpass_hz, btype="highpass", fs=sr, output="sos"),
        peaking(mud_hz, mud_db, 1.0, sr),
        peaking(presence_hz, presence_db, 0.9, sr),
        high_shelf(min(air_hz, sr * 0.42), air_db, sr),
    ])
    return sosfilt(sos, x)


# ----------------------------------------------------------------------------- bandwidth extension (AP-BWE)
class _AttrDict(dict):
    def __init__(self, data):
        super().__init__({k: _AttrDict(v) if isinstance(v, dict) else v for k, v in data.items()})

    def __getattr__(self, name):
        return self[name]


class BandwidthExtender:
    """Wraps GPT-SoVITS's bundled AP-BWE model: regenerates high frequencies, output at 48 kHz."""

    def __init__(self, gsv_root=r"C:\jarvis-apps\GPT-SoVITS-v2pro-20250604", device="cpu"):
        tools = os.path.join(gsv_root, "tools")
        if tools not in sys.path:
            sys.path.insert(0, tools)
        import torch
        from audio_sr import AP_BWE

        self.torch = torch
        self.model = AP_BWE(device, _AttrDict)
        self.device = device

    def __call__(self, x, sr):
        with self.torch.no_grad():
            tensor = self.torch.from_numpy(x.astype(np.float32)).unsqueeze(0).to(self.device)
            out, out_sr = self.model(tensor, sr)
        return np.asarray(out, dtype=np.float64), out_sr


# ----------------------------------------------------------------------------- full chain
def polish(audio, sr, extender=None, exciter=False, lufs=-16.0, ceiling_db=-1.0, **eq_args):
    """int16 or float mono in -> (float64 in [-1, 1], sample rate). Order: BWE, exciter, EQ, de-ess, compress, loudness, limit."""
    x = audio.astype(np.float64)
    if audio.dtype == np.int16:
        x /= 32768.0
    if extender is not None:
        x, sr = extender(x, sr)
    if exciter:
        x = excite(x, sr)
    x = eq(x, sr, **eq_args)
    x = deess(x, sr)
    x = to_loudness(x, sr, lufs - 4.0)              # set a known level so the compressor threshold means something
    x = compress(x, sr)
    x = to_loudness(x, sr, lufs)
    return limit(x, sr, ceiling_db), sr
