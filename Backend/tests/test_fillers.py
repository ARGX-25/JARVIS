"""Run from Backend/:  python -m unittest discover -s tests -v

Real asyncio timing, scaled so Windows' ~15 ms timer resolution can't flip a result.
"""
import asyncio
import json
import random
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from cuddy.events import EventLog  # noqa: E402
from cuddy.fillers import Activity, FillerController, FillerPolicy, PhraseLibrary  # noqa: E402

CLIP_S = 0.20
POLICIES = {
    "thinking": FillerPolicy(trigger_ms=100, repeat_gap_ms=60, max_clips=3),
    "working": FillerPolicy(trigger_ms=100, repeat_gap_ms=60, max_clips=1),
    "loading": FillerPolicy(trigger_ms=100, repeat_gap_ms=60, max_clips=1),
}


def write_library(root: Path, counts: dict, missing: int = 0) -> Path:
    categories = {}
    for cat, n in counts.items():
        (root / cat).mkdir(parents=True)
        categories[cat] = []
        for i in range(1, n + missing + 1):
            name = f"{cat}_{i:03d}.wav"
            if i <= n:
                (root / cat / name).write_bytes(b"RIFF")
            categories[cat].append({"id": f"{cat}_{i:03d}", "file": f"{cat}/{name}", "text": f"{cat} line {i}",
                                    "duration_s": CLIP_S})
    manifest = root / "manifest.json"
    manifest.write_text(json.dumps({"categories": categories}), encoding="utf-8")
    return manifest


class FakeSink:
    def __init__(self):
        self.played = []

    async def play(self, clip):
        self.played.append(clip.id)
        await asyncio.sleep(clip.duration_s)


class FillerTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        manifest = write_library(root / "library", {"thinking": 3, "working": 2, "loading": 2, "errors": 2})
        self.log_path = root / "events.jsonl"
        self.sink = FakeSink()
        self.fillers = FillerController(PhraseLibrary(manifest, rng=random.Random(7)), self.sink,
                                        EventLog(self.log_path), policies=POLICIES)

    def tearDown(self):
        self.tmp.cleanup()

    def events(self):
        if not self.log_path.exists():
            return []
        return [json.loads(line) for line in self.log_path.read_text(encoding="utf-8").splitlines()]

    async def test_fast_answer_plays_nothing(self):
        t0 = time.monotonic()
        async with self.fillers.cover(Activity.GENERATING):
            await asyncio.sleep(0.02)
        self.assertEqual(self.sink.played, [])
        self.assertEqual(self.events(), [])
        self.assertLess(time.monotonic() - t0, 0.08, "a fast answer must not be delayed")

    async def test_response_ready_mid_clip_waits_for_clip_to_finish(self):
        t0 = time.monotonic()
        async with self.fillers.cover(Activity.GENERATING):
            await asyncio.sleep(0.20)          # clip runs 0.10 -> 0.30; response ready at 0.20
        released = time.monotonic() - t0
        self.assertEqual(len(self.sink.played), 1)
        self.assertGreaterEqual(released, 0.28, "response released before the filler finished")
        [event] = self.events()
        self.assertEqual((event["type"], event["category"], event["outcome"]), ("filler", "thinking", "ready"))
        self.assertTrue(event["clip_id"].startswith("thinking_"))
        self.assertTrue(170 <= event["wait_ms"] <= 260, event)
        self.assertTrue(60 <= event["held_response_ms"] <= 140, event)
        self.assertTrue(80 <= event["started_after_ms"] <= 160, event)

    async def test_long_wait_rotates_clips_without_consecutive_repeats(self):
        async with self.fillers.cover(Activity.GENERATING):
            await asyncio.sleep(1.0)
        played = self.sink.played
        self.assertEqual(len(played), 3, "max_clips should cap fillers per wait")
        self.assertTrue(all(a != b for a, b in zip(played, played[1:])), played)
        self.assertEqual([e["clip_number"] for e in self.events()], [1, 2, 3])

    async def test_category_follows_activity(self):
        for activity, category in [(Activity.TOOL_CALL, "working"), (Activity.LOADING_MODEL, "loading")]:
            async with self.fillers.cover(activity):
                await asyncio.sleep(0.15)
            self.assertTrue(self.sink.played[-1].startswith(category + "_"), self.sink.played)
        self.assertEqual([e["category"] for e in self.events()], ["working", "loading"])

    async def test_unreachable_service_speaks_error_immediately(self):
        t0 = time.monotonic()
        clip = await self.fillers.report_unreachable(waited_ms=1234)
        self.assertEqual(clip.category, "errors")
        self.assertLess(time.monotonic() - t0, CLIP_S + 0.08)
        [event] = self.events()
        self.assertEqual((event["category"], event["wait_ms"], event["outcome"]), ("errors", 1234, "error"))
        with self.assertRaises(ValueError):
            self.fillers.cover(Activity.SERVICE_UNREACHABLE)

    async def test_failure_inside_wait_still_finishes_clip_and_propagates(self):
        with self.assertRaises(ConnectionError):
            async with self.fillers.cover(Activity.GENERATING):
                await asyncio.sleep(0.15)
                raise ConnectionError("cameron down")
        [event] = self.events()
        self.assertEqual(event["outcome"], "error")
        self.assertGreaterEqual(event["clip_ms"], 180)


class LibraryTests(unittest.TestCase):
    def test_pick_never_repeats_consecutively(self):
        with tempfile.TemporaryDirectory() as tmp:
            lib = PhraseLibrary(write_library(Path(tmp), {"thinking": 3, "wake": 1}), rng=random.Random(1))
            picks = [lib.pick("thinking").id for _ in range(500)]
            self.assertTrue(all(a != b for a, b in zip(picks, picks[1:])))
            self.assertEqual(len(set(picks)), 3)
            self.assertEqual({lib.pick("wake").id for _ in range(5)}, {"wake_001"}, "a one-clip category still plays")

    def test_clips_missing_on_disk_are_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            lib = PhraseLibrary(write_library(Path(tmp), {"thinking": 1, "nudges": 0}, missing=1))
            self.assertEqual(lib.categories(), ["thinking"])
            self.assertEqual(len(lib.missing), 2)
            with self.assertRaises(KeyError):
                lib.pick("nudges")


if __name__ == "__main__":
    unittest.main()
