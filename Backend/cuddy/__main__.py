"""Run Cuddy:  C:\\jarvis-venvs\\cuddy\\Scripts\\python.exe -m cuddy   (from Backend/)"""
import socket

import uvicorn

from .config import Settings, load_or_create_token
from .server import create_app


def lan_addresses() -> list[str]:
    try:
        return sorted({ip for ip in socket.gethostbyname_ex(socket.gethostname())[2] if not ip.startswith("127.")})
    except OSError:
        return []


def main() -> None:
    settings = Settings.from_env()
    token = load_or_create_token(settings.token_path)
    print(f"Cuddy listening on {settings.host}:{settings.port}")
    print(f"  USB (after `adb reverse tcp:{settings.port} tcp:{settings.port}`): http://127.0.0.1:{settings.port}")
    for ip in lan_addresses():
        print(f"  Wi-Fi: http://{ip}:{settings.port}")
    print(f"  Cameron (llama-server): {settings.llama_url}")
    print(f"  token file: {settings.token_path}   event log: {settings.event_log_path}")
    uvicorn.run(create_app(settings, token=token), host=settings.host, port=settings.port, log_level="info")


if __name__ == "__main__":
    main()
