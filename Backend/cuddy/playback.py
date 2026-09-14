"""Speaker output for filler clips (sounddevice + soundfile, imported lazily so the rest of Cuddy runs without them)."""
from __future__ import annotations

import asyncio

from .fillers import Clip


class SoundDeviceSink:
    """Plays one clip at a time on the default output device; play() returns when the clip has finished."""

    def __init__(self):
        self._lock = asyncio.Lock()

    async def play(self, clip: Clip) -> None:
        async with self._lock:
            await asyncio.to_thread(self._play_blocking, str(clip.path))

    @staticmethod
    def _play_blocking(path: str) -> None:
        import sounddevice as sd
        import soundfile as sf

        data, sr = sf.read(path, dtype="int16")
        sd.play(data, sr)
        sd.wait()
