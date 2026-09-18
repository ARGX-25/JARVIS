"""Cuddy server settings. Environment variables override the defaults; secrets live outside the repo."""
from __future__ import annotations

import os
import secrets
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    host: str = "0.0.0.0"                          # reachable over Wi-Fi and, via adb reverse, over USB
    port: int = 8765
    llama_url: str = "http://127.0.0.1:8081"       # llama-server; bound to localhost only
    data_dir: Path = Path(r"C:\jarvis-data\cuddy")  # token + event log; never inside the git repo
    max_chat_chars: int = 4000
    max_summary_chars: int = 20000                 # memory-summary transcripts are longer than a chat message
    max_tokens: int = 1024
    request_timeout_s: float = 180.0
    connect_timeout_s: float = 3.0

    @property
    def token_path(self) -> Path:
        return self.data_dir / "token.txt"

    @property
    def event_log_path(self) -> Path:
        return self.data_dir / "events.jsonl"

    @classmethod
    def from_env(cls) -> Settings:
        return cls(host=os.environ.get("CUDDY_HOST", cls.host),
                   port=int(os.environ.get("CUDDY_PORT", cls.port)),
                   llama_url=os.environ.get("CUDDY_LLAMA_URL", cls.llama_url),
                   data_dir=Path(os.environ.get("CUDDY_DATA_DIR", str(cls.data_dir))))


def load_or_create_token(path: Path) -> str:
    """The shared secret the phone sends as `Authorization: Bearer <token>`. Created once, then reused."""
    if path.exists():
        token = path.read_text(encoding="utf-8").strip()
        if token:
            return token
    path.parent.mkdir(parents=True, exist_ok=True)
    token = secrets.token_urlsafe(32)
    path.write_text(token + "\n", encoding="utf-8")
    return token
