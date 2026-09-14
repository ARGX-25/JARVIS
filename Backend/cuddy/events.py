"""Append-only JSONL event log. One JSON object per line, each with an ISO-8601 timestamp and a `type`."""
from __future__ import annotations

import json
import threading
from datetime import datetime
from pathlib import Path


class EventLog:
    def __init__(self, path: str | Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()

    def write(self, type_: str, **fields) -> dict:
        event = {"ts": datetime.now().astimezone().isoformat(timespec="milliseconds"), "type": type_, **fields}
        line = json.dumps(event, ensure_ascii=False)
        with self._lock, self.path.open("a", encoding="utf-8") as f:
            f.write(line + "\n")
        return event
