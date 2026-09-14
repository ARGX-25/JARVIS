"""Filler playback: short pre-rendered lines that cover a wait, and only a wait that is actually happening.

Clips come from audio/library/manifest.json and are looked up by category, never by filename.

Contract:
- Latency-triggered. Nothing plays unless the wait outlasts the category's trigger (thinking: 700 ms), so fast
  answers are never slowed down.
- Never the same clip twice in a row within a category.
- The category follows what is happening: generating -> thinking, VRAM lock / model load -> loading,
  tool call -> working, downstream service unreachable -> errors.
- Barge-in: a clip that has started always finishes. When the response is ready mid-clip, the response waits for
  the clip to end instead of cutting it off mid-word.
- Every clip played is logged as {"type": "filler"} with its category and the measured wait it covered.

Usage:
    async with fillers.cover(Activity.GENERATING):
        first_token = await cameron.first_token()
    # any filler has finished here; start speaking the response
"""
from __future__ import annotations

import asyncio
import json
import random
import time
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Callable, Protocol

from .events import EventLog


class Activity(Enum):
    GENERATING = "thinking"
    LOADING_MODEL = "loading"          # acquiring the VRAM lock, loading or swapping a model
    TOOL_CALL = "working"
    SERVICE_UNREACHABLE = "errors"


@dataclass(frozen=True)
class Clip:
    id: str
    category: str
    path: Path
    text: str
    duration_s: float


@dataclass(frozen=True)
class FillerPolicy:
    trigger_ms: int        # wait this long before the first clip
    repeat_gap_ms: int     # silence after a clip before the next one, if the wait goes on
    max_clips: int         # clips per wait at most


DEFAULT_POLICIES = {
    "thinking": FillerPolicy(trigger_ms=700, repeat_gap_ms=6000, max_clips=2),
    "working": FillerPolicy(trigger_ms=700, repeat_gap_ms=6000, max_clips=2),
    "loading": FillerPolicy(trigger_ms=700, repeat_gap_ms=5000, max_clips=3),
}


class PhraseLibrary:
    def __init__(self, manifest_path: str | Path, rng: random.Random | None = None):
        manifest_path = Path(manifest_path)
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.sample_rate = data.get("audio", {}).get("sample_rate")
        self._clips: dict[str, list[Clip]] = {}
        self.missing: list[Path] = []
        for category, entries in data["categories"].items():
            clips = []
            for e in entries:
                clip = Clip(e["id"], category, manifest_path.parent / e["file"], e["text"], float(e["duration_s"]))
                (clips if clip.path.is_file() else self.missing).append(clip if clip.path.is_file() else clip.path)
            self._clips[category] = clips
        self._last: dict[str, str] = {}
        self._rng = rng or random.Random()

    def categories(self) -> list[str]:
        return sorted(c for c, clips in self._clips.items() if clips)

    def pick(self, category: str) -> Clip:
        """Random clip from the category, excluding the one played last in that category."""
        clips = self._clips.get(category)
        if not clips:
            raise KeyError(f"no clips for category {category!r}")
        choices = [c for c in clips if c.id != self._last.get(category)] or clips
        clip = self._rng.choice(choices)
        self._last[category] = clip.id
        return clip


class AudioSink(Protocol):
    async def play(self, clip: Clip) -> None:
        """Play the clip and return once it has finished."""


def _ms(seconds: float) -> int:
    return round(seconds * 1000)


class FillerController:
    def __init__(self, library: PhraseLibrary, sink: AudioSink, events: EventLog,
                 policies: dict[str, FillerPolicy] | None = None, clock: Callable[[], float] = time.monotonic):
        self.library, self.sink, self.events = library, sink, events
        self.policies = {**DEFAULT_POLICIES, **(policies or {})}
        self.clock = clock

    def cover(self, activity: Activity) -> Cover:
        if activity is Activity.SERVICE_UNREACHABLE:
            raise ValueError("an unreachable service is not a wait to cover - use report_unreachable()")
        return Cover(self, activity.value, self.policies[activity.value])

    async def report_unreachable(self, waited_ms: int | None = None) -> Clip:
        """Say the honest failure line now and return once it has been said.
        `waited_ms` is how long the user was kept waiting before the failure was detected."""
        clip = self.library.pick(Activity.SERVICE_UNREACHABLE.value)
        await self.sink.play(clip)
        self.events.write("filler", category=clip.category, clip_id=clip.id, text=clip.text, wait_ms=waited_ms,
                          trigger_ms=0, started_after_ms=waited_ms, clip_ms=_ms(clip.duration_s),
                          held_response_ms=0, outcome="error")
        return clip


class Cover:
    """One wait. Entering starts the trigger timer; exiting means the response (or a failure) is ready."""

    def __init__(self, controller: FillerController, category: str, policy: FillerPolicy):
        self._c, self.category, self._policy = controller, category, policy
        self._ready = asyncio.Event()
        self._task: asyncio.Task | None = None
        self._t0 = 0.0
        self.played: list[tuple[Clip, float, float]] = []    # (clip, started, finished) on the controller clock

    async def __aenter__(self) -> Cover:
        self._t0 = self._c.clock()
        self._task = asyncio.create_task(self._run())
        return self

    async def _run(self) -> None:
        if await self._ready_within(self._policy.trigger_ms):
            return                                           # answered before the trigger: stay silent
        while True:
            clip = self._c.library.pick(self.category)
            started = self._c.clock()
            await self._c.sink.play(clip)                    # never interrupted, even if the response arrives
            self.played.append((clip, started, self._c.clock()))
            if self._ready.is_set() or len(self.played) >= self._policy.max_clips:
                return
            if await self._ready_within(self._policy.repeat_gap_ms):
                return

    async def _ready_within(self, ms: int) -> bool:
        try:
            await asyncio.wait_for(self._ready.wait(), ms / 1000)
            return True
        except asyncio.TimeoutError:
            return False

    async def __aexit__(self, exc_type, exc, tb) -> bool:
        ready_at = self._c.clock()
        self._ready.set()
        failure = None
        try:
            await self._task                                 # lets a clip in progress finish
        except Exception as e:                               # a broken speaker must not break the response
            failure = e
        outcome = "ready" if exc_type is None else ("cancelled" if issubclass(exc_type, asyncio.CancelledError) else "error")
        wait_ms = _ms(ready_at - self._t0)
        for n, (clip, started, finished) in enumerate(self.played, 1):
            self._c.events.write("filler", category=self.category, clip_id=clip.id, text=clip.text, wait_ms=wait_ms,
                                 trigger_ms=self._policy.trigger_ms, started_after_ms=_ms(started - self._t0),
                                 clip_ms=_ms(finished - started), held_response_ms=max(0, _ms(finished - ready_at)),
                                 clip_number=n, outcome=outcome)
        if failure is not None:
            self._c.events.write("filler_failed", category=self.category, wait_ms=wait_ms, error=repr(failure))
        return False
