# JARVIS speech stack — Day 1 setup

Everything is local on the MSI Cyborg 15 AI (RTX 4060 Laptop 8 GB, 35 W default / 45 W max; Core Ultra 7 155H; 16 GB).
The Intel Arc iGPU drives the display, so the 4060 is otherwise idle apart from ~0.1–0.4 GB used by other apps.

## Where things live

| What | Path | Size |
|---|---|---|
| Whisper venv (Python 3.11.9) | `C:\jarvis-venvs\whisper` | 2.1 GB |
| Kokoro venv (Python 3.11.9) | `C:\jarvis-venvs\kokoro` | 4.8 GB |
| GPT-SoVITS Windows package (bundled **Python 3.9.13** runtime) | `C:\jarvis-apps\GPT-SoVITS-v2pro-20250604` | 16.7 GB |
| Hugging Face model cache (`HF_HOME`) | `C:\jarvis-models\hf` | 2.3 GB |
| Voice-clone working data (stems, slices, selection, `ref.json`) | `C:\jarvis-data\voice` | |
| Trained voice weights | `…\GPT-SoVITS-v2pro-20250604\SoVITS_weights_v2ProPlus\jarvis_v2pp_e8_s192.pth`, `…\GPT_weights_v2ProPlus\jarvis_v2pp-e15.ckpt` (also `-e5`, `-e10`) | |
| Notebooks + results (`outputs/*.json`, sample WAVs) | `notebooks/` in this project | |
| Install logs | `C:\jarvis-venvs\_logs` | |

Nothing was installed into the project folder. The Android app in `App/` was not touched.

## Installed versions

- **Whisper venv:** faster-whisper 1.2.1, ctranslate2 4.8.2, nvidia-cublas-cu12 12.9.2.10, webrtcvad-wheels 2.0.14, sounddevice 0.5.6, nvidia-ml-py 13.610.43, ipykernel 7.3.0.
  cuDNN 9 DLLs are **copied from the local torch 2.11.0+cu128 wheel** into `C:\jarvis-venvs\whisper\cudnn9` (the `nvidia-cudnn-cu12` download kept stalling). Notebook 01 adds that folder to the DLL path.
- **Kokoro venv:** kokoro 0.9.4, misaki 0.9.4, torch 2.11.0+cu128, spacy 3.8.16 (+ `en_core_web_sm`, auto-installed on first run), transformers 5.17.0, sounddevice 0.5.6.
- **GPT-SoVITS package** `GPT-SoVITS-v2pro-20250604.7z` (8,185,086,602 bytes, SHA-256 `bd60d079…6215cd6`, from `huggingface.co/lj1995/GPT-SoVITS-windows-package`): torch 2.0.0+cu118, transformers 4.43.0, librosa 0.9.2, gradio 4.44.1. Added: ipykernel 6.31.0, nvidia-ml-py, sounddevice 0.4.6, nbclient.
  Chosen over a manual install because three dependencies (`jieba_fast`, `pyopenjtalk`, `pyworld`) need a C++ compiler and this machine has none.
- **Jupyter kernels:** `jarvis-whisper`, `jarvis-kokoro`, `jarvis-gptsovits`.

## How to run

Open the notebook in VS Code, pick its kernel, then **Restart kernel → Run All** (VRAM numbers are only valid from a fresh kernel). Close Android emulators first — they reserved ~3.6 GB on the 4060.

| Notebook | Kernel | What it does |
|---|---|---|
| `01_whisper_stt.ipynb` | JARVIS - whisper | Live mic → webrtcvad endpointing (600 ms silence) → large-v3-turbo on CUDA int8_float16; per-stage times + peak VRAM; GPU vs CPU (int8, 8 threads) benchmark for turbo and small |
| `02_kokoro_tts.ipynb` | JARVIS - kokoro | `bm_george` says "Good morning, Sir."; time-to-first-audio + peak VRAM; audition of all `bm_` voices |
| `03_gptsovits_train.ipynb` | JARVIS - GPT-SoVITS | UVR5 (bs_roformer) → slice → speaker/ASR/music-bleed scoring → selection + listening review → dataset formatting → SoVITS then GPT training |
| `04_gptsovits_tts.ipynb` | JARVIS - GPT-SoVITS | `JarvisVoice.speak()` / `speak_stream()`, TTFA, streaming timeline, peak VRAM, 5 sample lines, 8 GB verdict |

Notebook 03 rewrites the package's `runtime\Lib\site-packages\users.pth` on every run: the embedded runtime ignores `PYTHONPATH`, and the shipped file pointed at the packager's `D:\` drive.

## Measured VRAM (per-process dedicated memory on the 4060, Windows GPU counters — includes each process's CUDA context)

| Component (GPU) | Peak VRAM |
|---|---|
| Whisper large-v3-turbo, int8_float16 | **1,327 MB** |
| GPT-SoVITS v2ProPlus inference, all models resident (trained voice; zero-shot identical) | **2,063 MB** (torch reserved 1,922 MB) |
| Kokoro-82M `bm_george` | **823 MB** (torch reserved 696 MB) |
| GPT-SoVITS SoVITS training, batch 4 (for reference) | 6,937 MB device total |
| GPT-SoVITS GPT training, batch 4 (for reference) | 5,927 MB device total |

## Measured latency

| | Result |
|---|---|
| Whisper GPU turbo, 15 s clip | 1,343 ms warm (RTF 0.09); load 6.8 s |
| Whisper CPU turbo int8 ×8, same clip | 23,487 ms (RTF 1.57) — **slower than real time, not a usable fallback** |
| Whisper CPU small int8 ×8, same clip | 5,457 ms (RTF 0.36) — usable fallback |
| Kokoro, "Good morning, Sir." | 210 ms synthesis (median of 3); 481 ms until heard (includes opening the audio device) |
| GPT-SoVITS trained, "Sir, your class begins in thirty minutes." (2.3 s audio) | 2,011 ms synthesis (median of 3); heard at 2,057 ms; cold first call 9.7 s |
| GPT-SoVITS trained, streaming 4 sentences | first audio 2,038 ms; 7.8 s of speech took 14.8 s, 5.1 s of it silent gaps waiting for synthesis |
| GPT-SoVITS zero-shot (pretrained), same sentence | 2,620 ms synthesis; heard at 4,651 ms (measured while the GPU was thermally throttling) |

### Why GPT-SoVITS is slow: the CPU, not the GPU

Standalone process, trained voice, same 2.26 s sentence, 3 runs each, after the MSI Center change (`outputs/gptsovits_speed_experiment.json`):

| Config | Total | GPT decoding (50 new tokens) | GPU busy during decoding | RTF | VRAM (torch reserved) |
|---|---|---|---|---|---|
| fp16, `parallel_infer=True` (notebook default) | 1,703 ms | 1,214 ms (~41 tok/s) | 24% | 0.75 | 1,922 MB |
| fp16, `parallel_infer=False` | 1,652 ms | 1,077 ms | 28% | 0.73 | same |
| fp32, `parallel_infer=False` | 1,453 ms | 935 ms (~53 tok/s) | 45% | 0.64 | **4,326 MB** |

The GPU sits at 24–45% while GPT decodes one token at a time in Python, so the per-token CPU overhead is the bottleneck. fp32 is ~15% faster but costs ~2.4 GB more VRAM, so the notebooks stay on fp16. The parallel on/off difference is within run-to-run noise (945–1,286 ms).

## Can Whisper + GPT-SoVITS + a 6 GB LLM share 8 GB?

**No, not as specified.** On measured numbers:

| | MB |
|---|---|
| RTX 4060 total | 8,188 |
| Other apps on the 4060 at measurement (browser etc.) | −420 |
| Whisper large-v3-turbo GPU | −1,327 |
| GPT-SoVITS inference | −2,063 |
| LLM | −6,000 |
| **Headroom** | **−1,622** |

Each figure already includes that process's own CUDA context. Over budget, Windows won't crash; it spills into shared system RAM and everything slows down sharply.

Configurations that fit, from the same measurements (the LLM itself is not measured yet):

| Option | GPU use without LLM | Room left for the LLM | Cost |
|---|---|---|---|
| **Whisper `small` on CPU + GPT-SoVITS on GPU** (recommended) | 2,063 + 420 | **~5,700 MB** | Transcription ~0.36× real time on CPU (≈1 s for a 3 s command); LLM must fit in ~5.7 GB, i.e. cut its context from the 6 GB plan |
| Whisper turbo GPU + Kokoro GPU | 1,327 + 823 + 420 | ~5,600 MB | Loses the cloned voice |
| Whisper `small` CPU + Kokoro GPU | 823 + 420 | ~6,900 MB | Loses the cloned voice; the only option that fits the full 6 GB LLM |
| Whisper turbo GPU + GPT-SoVITS GPU | 1,327 + 2,063 + 420 | ~4,400 MB | Too small for an 8B model at a useful context |

**Recommendation.** Move Whisper to CPU with `small` (turbo on CPU is slower than real time) and keep GPT-SoVITS on the GPU. Plan the LLM for ~5.5 GB, not 6. Distilling the voice into Piper is not needed for memory.

The bigger risk is latency, not VRAM. A short sentence takes ~1.7 s to synthesise and the decoding is CPU-bound, so it will also compete with the LLM for CPU time. Kokoro gets first audio in ~0.2–0.5 s. Keep it as the fast fallback until the full loop is measured against the 3-second first-audio target.

## What didn't work or is weaker than it looks

- **GPT-SoVITS is slower than real time on this laptop** (RTF ~0.9–1.3 on single lines). Streaming works mechanically — sentence one plays while sentence two synthesises — but playback stalls between sentences.
- **Training data is compromised by the source.** The score runs under 366 of 416 s of speech, typically only ~5.5 dB below the voice. 46 of 88 segments were rejected for music; 31 segments / 155 s were used. The selection was automatic — nobody has listened to it yet (notebook 03 has the listening cell).
- **Training labels contain ASR errors** ("80 hours a day", "Mr. Starr"). Hand-correcting `C:\jarvis-data\voice\jarvis.list` and retraining would help.
- **GPT may be overfitting:** train top-3 accuracy jumped 0.51 → 0.86 over the last five of 15 epochs, with no held-out set. `gptsovits_trained_gpt-e10_*.wav` are the same lines from epoch 10 for an A/B by ear.
- **Python constraint broken for GPT-SoVITS:** the prebuilt package ships Python 3.9.13 / torch 2.0. It is fully isolated, but it is not 3.11/3.12.
- **Not verified by me:** the live-mic path in notebook 01 (I can't speak into the mic — it timed out waiting for speech) and English/Bengali/Hindi code-switching. Both need you.
- **Thermals:** during the Whisper, Kokoro and zero-shot measurements the 4060 was at 78–82 °C with "SW Thermal Slowdown" active. After switching the MSI Center profile the target temperature rose to 87 °C and the slowdown cleared; the trained GPT-SoVITS numbers were taken after that. The Windows power plan is still Balanced.
- **Windows Defender exclusion:** not added by me (security setting). Run as Administrator:
  `Add-MpPreference -ExclusionPath "C:\jarvis-models", "C:\jarvis-apps", "C:\jarvis-venvs"`
