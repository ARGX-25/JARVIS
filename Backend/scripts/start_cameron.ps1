# Cameron: Qwen3-8B on the RTX 4060 via llama.cpp's llama-server.
# Listens on localhost only - the phone talks to Cuddy, never to this directly.
# Ready when http://127.0.0.1:8081/health returns {"status":"ok"} (the model takes ~10-60 s to load).
$ErrorActionPreference = "Stop"
$llama = "C:\jarvis-apps\llama.cpp\b10964\llama-server.exe"
$model = (Resolve-Path (Join-Path $PSScriptRoot "..\..\Models\Qwen3-8B-Q4_K_M.gguf")).Path
$log = "C:\jarvis-data\cuddy\llama-server.log"
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null

# -ngl all            every layer on the GPU (~4.7 GB of weights)
# -c 8192 -np 1       one conversation slot with an 8K context (fits the longest memory-summary request)
# -fa on -ctk/-ctv    flash attention + 8-bit KV cache: ~0.6 GB for the full 8K context instead of ~1.2 GB
# Thinking is switched off per request by Cuddy (chat_template_kwargs), not here.
& $llama -m $model --alias cameron --host 127.0.0.1 --port 8081 `
    -ngl all -c 8192 -np 1 -fa on -ctk q8_0 -ctv q8_0 2>&1 | Tee-Object -FilePath $log
