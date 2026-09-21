# Cuddy: the server the phone talks to. Port 8765 on all interfaces, bearer-token protected.
# Start Cameron first (start_cameron.ps1); Cuddy reports "cameron": "down" until llama-server is up.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
& C:\jarvis-venvs\cuddy\Scripts\python.exe -m cuddy
