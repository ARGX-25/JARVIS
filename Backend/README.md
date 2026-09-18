# Cuddy — JARVIS backend

Cuddy is the orchestration layer: the phone sends text, Cuddy routes it to a role, the role answers, and every reply
passes through the JARVIS personality layer. Today only **Cameron** (Qwen3-8B on llama.cpp) is live; every other role
answers with a flagged `unavailable` response that is never counted as a success.

```
Phone app ──HTTP + bearer token──▶ Cuddy :8765 (all interfaces) ──▶ llama-server :8081 (localhost only) ──▶ RTX 4060
```

## Run

1. **Cameron** — `scripts\start_cameron.ps1`. Ready when `http://127.0.0.1:8081/health` says `ok`.
2. **Cuddy** — `scripts\start_cuddy.ps1`. Prints the USB and Wi-Fi addresses it can be reached on.
3. **Phone** — one of:
   - **USB:** plug in with USB debugging on, run `scripts\usb_forward.ps1`. No firewall change needed.
   - **Wi-Fi:** phone and laptop on the same network. Windows blocks incoming connections on a *Public* network,
     so once, in an **Administrator** PowerShell:
     ```
     Set-NetConnectionProfile -InterfaceAlias Wi-Fi -NetworkCategory Private
     New-NetFirewallRule -DisplayName "JARVIS Cuddy 8765" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -Profile Private -RemoteAddress LocalSubnet
     ```
     Only do this on your home network. Traffic over Wi-Fi is plain HTTP: the token stops strangers from using
     Cuddy, but anyone on the same Wi-Fi could read the messages.

The app tries its URLs in order (`CUDDY_URLS` in `App/local.properties`): USB first, then Wi-Fi.

Check it from the laptop:
```
$token = (Get-Content C:\jarvis-data\cuddy\token.txt -Raw).Trim()
Invoke-RestMethod http://127.0.0.1:8765/health
Invoke-RestMethod http://127.0.0.1:8765/v1/chat -Method Post -ContentType application/json -Headers @{Authorization="Bearer $token"} -Body '{"message":"Good evening"}'
```

## API

| | |
|---|---|
| `GET /health` | No auth. `{"cuddy": "ok", "cameron": "ok" \| "loading" \| "down"}` |
| `GET /v1/roles` | The Secretariat and who is available |
| `POST /v1/chat` | `{"message", "memory_context"?, "purpose": "chat" \| "memory_summary", "role"?}` → `{"id", "role", "status", "text", "latency_ms", "truncated", "error"}` |

`status` is `success` (HTTP 200), `unavailable` (503, role has no model yet) or `error` (503/502, Cameron down,
loading or empty). Non-success replies still carry an in-character `text` the phone shows as-is.

## Files

| | |
|---|---|
| `cuddy/server.py` | HTTP API, auth, input limits, event logging |
| `cuddy/roles.py` | The Secretariat and the router (always `cameron` for now) |
| `cuddy/personality.py` | JARVIS system prompt; strips Qwen3 `<think>` blocks; in-character failure lines |
| `cuddy/cameron.py` | llama-server client (OpenAI-compatible API, thinking off, Qwen3 sampling) |
| `cuddy/config.py` | Settings (`CUDDY_HOST`, `CUDDY_PORT`, `CUDDY_LLAMA_URL`, `CUDDY_DATA_DIR` env overrides) |
| `cuddy/fillers.py` | Filler clip playback for the voice path (not wired in yet) |

Secrets and logs live in `C:\jarvis-data\cuddy\`, outside the repo: `token.txt`, `events.jsonl` (metadata only —
conversation text is never written to disk), `llama-server.log`.

## Tests

```
C:\jarvis-venvs\cuddy\Scripts\python.exe -m unittest discover -s tests -v
```
llama-server is mocked, so no GPU or model is needed.
