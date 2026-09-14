# JARVIS

A local-first personal assistant: an Android app today, plus a speech stack (speech-to-text, voice, memory) being built to run entirely on one laptop.

> Fan project, not affiliated with Marvel or Disney. The voice-cloning experiments stay on the author's machine: no film audio, cloned-voice audio or model weights are in this repository.

**Status:** early and experimental. Paths and setup are Windows-specific.

## The idea

JARVIS is a system, not a single model. A small orchestration layer, **Cuddy**, decides who answers and what happens next. The models are interchangeable specialists behind fixed roles:

| Role | Job |
|---|---|
| **Cuddy** | Orchestration: routing, memory, tools, permissions, personality. Not a model. |
| **Cameron** | General assistant, always loaded |
| **Wilson** | Stronger general model |
| **Foreman** | Deep reasoning |
| **Chase** | Documents and vision |
| **House** | Escalation tier |

Around them: Whisper for listening, a cloned voice (GPT-SoVITS, with Kokoro as a fast fallback) for speaking, and embeddings for memory. One personality sits over everything, so the voice is the same whichever model answered. Privacy is architectural: the brain runs locally, and cloud use is a rare, announced exception.

## What exists today

| Part | Where | State |
|---|---|---|
| Android app | `App/` | Kotlin + Jetpack Compose. Cuddy → Cameron request pipeline; Cameron is currently Gemini 2.0 Flash-Lite, the only cloud dependency. Daily compressed memory in Room (last 7 days injected into the prompt). Dark-blue chat UI with a voice mode that shows a live microphone waveform. Speech-to-text is not wired up yet, and Wilson, Foreman, Chase and House are stubs. |
| Speech stack | `notebooks/` | One notebook per tool, each with its own venv: Whisper speech-to-text, Kokoro TTS, GPT-SoVITS voice-clone training, GPT-SoVITS TTS engine with sentence streaming, and a pre-rendered phrase library. |
| Cuddy backend | `Backend/cuddy/` | First Python module: latency-triggered filler lines (for example a "thinking" clip only after a 700 ms wait), never interrupting a clip, logged to an append-only JSONL event log. Unit tested. |
| Specs | `.codex/specs/` | Phase 0 (request loop) and Phase 1 (memory spine). |
| Setup and measurements | `Models/SETUP.md` | Environments, exact versions, VRAM and latency figures. |

## Measured on the target laptop

RTX 4060 Laptop (8 GB, 35–45 W), Core Ultra 7 155H, 16 GB RAM. Full tables are in [`Models/SETUP.md`](Models/SETUP.md).

| Component | Peak VRAM | Speed |
|---|---|---|
| Whisper large-v3-turbo (GPU, int8_float16) | 1,327 MB | 15 s of audio in 1.34 s |
| Whisper small (CPU, int8, 8 threads) | 0 | 15 s of audio in 5.5 s |
| Kokoro-82M | 823 MB | ~210 ms to synthesise a short sentence |
| GPT-SoVITS v2ProPlus (trained voice) | 2,063 MB | ~1.7 s for a 2.3 s sentence; decoding is CPU-bound |

Whisper on the GPU plus GPT-SoVITS plus a 6 GB LLM does not fit in 8 GB (about 1.6 GB short). The plan is Whisper `small` on the CPU, GPT-SoVITS on the GPU, and an LLM sized to about 5.5 GB.

## Repository layout

```
App/                 Android app (Jetpack Compose)
Backend/cuddy/       Cuddy orchestration layer (Python)
Backend/tests/       unit tests
notebooks/           speech stack experiments and measurements (01-05)
notebooks/outputs/   measured results as JSON
audio/samples/kokoro_fallback/   Kokoro fallback voice samples
.codex/specs/        phase specs
Models/SETUP.md      environment setup and full measurements
Claude outputs/      task prompts used during development
```

## Getting started

### Android app

1. Open `App/` in Android Studio.
2. Copy `App/local.properties.example` to `App/local.properties`, then set `sdk.dir` and `GEMINI_API_KEY`. That file is git-ignored; never commit it.
3. Run on an emulator or a device (minSdk 24). Voice mode asks for microphone permission the first time.

### Speech notebooks

Each notebook runs in its own virtual environment, because GPT-SoVITS pins versions that conflict with the others. Venv locations, versions and kernel names are in [`Models/SETUP.md`](Models/SETUP.md). Close Android emulators before GPU measurements, and run only one GPU job at a time.

### Backend tests

```
cd Backend
python -m unittest discover -s tests -v
```

## Not in this repository

- `Models/`: GGUF LLMs, vision projectors and Piper source, tens of GB.
- `audio/source/` and `audio/training/`: film audio used for voice-cloning experiments.
- `audio/samples/gptsovits_*` and `audio/library/`: cloned-voice renders. Only Kokoro's built-in voices are published.
- Notebook outputs: stripped automatically on commit (`.gitattributes` plus `tools/git/nbstrip.py`), because they can embed audio and local paths.
- `App/local.properties`: API keys.

## Roadmap

1. **Voice and conversation loop:** wake word, Whisper, Cameron, personality prompt, streamed voice, under 3 s to first audio.
2. **Personal context:** timetable, an append-only SQLite event log and an embedding index, so "what's my day?" gets a correct answer.
3. **Proactive JARVIS:** scheduler daemon, morning brief, missed-alarm handling, spoken without being asked.
4. **Hands:** Android companion, deep links first, then accessibility automation for low-risk flows.
5. **Full specialist set** once Cameron is genuinely the bottleneck.

No licence has been chosen yet.
