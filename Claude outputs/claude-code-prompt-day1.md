# JARVIS — Day 1: speech stack + voice cloning

## Context

I'm building a local-first AI assistant called JARVIS. No cloud LLMs are available to me
(my card has no international billing), so every component runs on this machine.

Project root: P:\Coding\App Development\MyProjects\JARVIS
NOTE: that path contains spaces. Quote it everywhere, and create virtualenvs somewhere
without spaces (e.g. C:\jarvis-venvs\) to avoid tooling breakage.

Hardware: MSI Cyborg 15 AI — RTX 4060 Laptop 8GB VRAM, Core Ultra 7 155H (16 cores),
16GB DDR5, Windows 11, ~300GB free. Hybrid graphics: Intel Arc iGPU + RTX 4060.

Already in the project folder: Qwen3-Embedding-0.6B, two mmproj files (vision projectors
with no main models yet), piper1-gpl-main.zip (Piper SOURCE, not a voice), and
"JARVIS voice clone.mp3" — 8.6MB of reference audio for the cloning task below.

**Everything runs on the GPU today.** A Qwen3-8B LLM will move in later and needs ~6GB of
the 8GB, so I need to know the real VRAM cost of each component — see the reporting
section. If something turns out to bottleneck badly we'll move it to CPU then, not now.

**The goal is that JARVIS speaks in the cloned voice.** GPT-SoVITS is therefore the primary
TTS engine, not just a training tool — it does inference too. Kokoro is only a fallback so
I have a working voice while the clone is being trained and evaluated.

## First, before installing anything

Check in Task Manager whether the display is being driven by the Intel Arc iGPU or the
RTX 4060, and tell me. If the desktop runs on the iGPU, nearly all 8GB of the 4060 is free,
which changes the budget for everything below.

## Scope for today — three things

### 1. Speech-to-text: faster-whisper (GPU)

- Install faster-whisper plus sounddevice and a VAD (webrtcvad or silero).
- Run on GPU: device="cuda", compute_type="int8_float16" — roughly half the VRAM of
  float16 with near-identical accuracy.
- Use **large-v3-turbo**. Do NOT use an .en-only variant: I code-switch between English
  and Bengali/Hindi and English-only models mangle that.
- Deliverable: verify_whisper.py — records from the mic with VAD endpointing (stop after
  ~600ms of silence), transcribes, and prints per-stage elapsed times plus peak VRAM.
- Also record CPU-path numbers (device="cpu", compute_type="int8", cpu_threads=8) for
  both turbo and small, so I have measured fallback figures rather than guesses. Just
  benchmark them — GPU stays the default.

### 2. Voice cloning AND text-to-speech: GPT-SoVITS (GPU) — the main task

GPT-SoVITS (MIT) is both the cloning trainer and the TTS engine. Use the Windows
prebuilt/integration package if it's less painful than a manual install — tell me which
you chose.

**a. Train the clone.** Notebook or script pipeline over "JARVIS voice clone.mp3":
  1. Separate vocals from background music (UVR5 bundled with GPT-SoVITS, or Demucs
     htdemucs as fallback). The source has score under the dialogue — this step matters
     most for final quality.
  2. Slice into segments; drop anything with music bleed, overlapping speech or heavy
     reverb. Aim for the cleanest 1–3 minutes total.
  3. Auto-label with Faster-Whisper.
  4. Fine-tune. Keep batch size small — 8GB VRAM. Nothing else should be on the GPU
     during training.

**b. Wrap inference as the TTS engine.** This is the part I actually need working:
  - A `speak(text)` function that synthesises in the cloned voice and plays it.
  - A **streaming path**: given an iterable of sentences, begin speaking sentence one
    while sentence two is still synthesising. This is essential for the conversation loop.
  - Keep models loaded between calls — no reloading per utterance.

**c. Measure, because a decision depends on it:**
  - Time-to-first-audio for a single sentence.
  - Peak VRAM with all inference models resident.
  - Tell me plainly whether this can coexist with a ~6GB LLM in 8GB, or whether I'll need
    to cut the LLM's context, or distil this voice into Piper for CPU inference later.

**d. Samples.** 3–5 wavs using **ordinary assistant lines**, not lines from the source
material — e.g. "Sir, your class begins in thirty minutes." and "I'm afraid I can't reach
your calendar at the moment, Sir." Sentences resembling the training audio will sound
misleadingly good and I need to judge it on real usage.

### 3. Fallback TTS: Kokoro-82M (GPU) — keep this small

Ten minutes of work, no more. I need a working voice while the clone is being trained and
judged, and a baseline to compare latency against.

- Install Kokoro (Apache 2.0, ~82M params), torch-based `kokoro` package on CUDA.
- Use a **British male** voice — IDs are likely prefixed `bm_` (e.g. bm_george, bm_lewis),
  but verify against the actual voice list.
- Deliverable: verify_tts_kokoro.py "Good morning, Sir." — speaks it, prints
  time-to-first-audio and peak VRAM, so I can compare directly against GPT-SoVITS.
- Do not build a streaming path for this one. It's a fallback.

## Hard constraints

- **SEPARATE VIRTUALENV PER TOOL.** GPT-SoVITS pins specific torch/transformers versions
  that will collide with faster-whisper and Kokoro. Do not put them in one env.
- Python 3.11 or 3.12. Not 3.13 — audio packages still lag.
- Native Windows, NOT WSL2. WSL2 claims up to half of RAM by default and I can't spare 8GB.
- Add a Windows Defender exclusion for the models folder — it scans multi-GB files on every
  load and adds seconds to startup.
- Do NOT touch the Android app in App/.
- Do NOT download GPT-OSS-20B or DeepSeek-R1-14B. They can't run on 16GB RAM and are shelved.
- Don't install anything into the project folder that belongs in a venv.
- GPT-SoVITS training wants the whole GPU — don't run the other two alongside it.

## Reporting

Write a short SETUP.md recording what you installed, exact versions, where each venv lives,
and how to run each verify script.

Include a **VRAM table**: peak usage for Whisper (GPU), GPT-SoVITS inference (GPU) and
Kokoro (GPU), measured not estimated, so I can plan against the ~6GB the LLM will need.

End with a straight recommendation: can I run Whisper + GPT-SoVITS + a 6GB LLM in 8GB of
VRAM, or not?

Be straight with me about what didn't work. If a step fails or a result is mediocre, say so
plainly rather than reporting success — I'd rather know now than discover it next week.
