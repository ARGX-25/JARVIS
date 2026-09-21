"""Run from Backend/ with the cuddy venv:  C:\\jarvis-venvs\\cuddy\\Scripts\\python.exe -m unittest discover -s tests -v

llama-server is replaced by an httpx MockTransport, so these run without a GPU or a model.
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    import httpx
    from fastapi.testclient import TestClient
except ImportError:                                   # the stdlib-only filler tests still run without the venv
    httpx = None

TOKEN = "test-token"
AUTH = {"Authorization": f"Bearer {TOKEN}"}


@unittest.skipIf(httpx is None, "needs the cuddy venv (fastapi, httpx)")
class ServerTests(unittest.TestCase):
    def setUp(self):
        from cuddy.config import Settings
        from cuddy.server import create_app

        self.tmp = tempfile.TemporaryDirectory()
        self.settings = Settings(data_dir=Path(self.tmp.name), llama_url="http://llama.test")
        self.requests = []
        self.llama = {"status": 200, "content": "Good evening, Sir.", "error": None}

        def handler(request):
            self.requests.append(request)
            if self.llama["error"]:
                raise self.llama["error"]
            if request.url.path == "/health":
                return httpx.Response(self.llama["status"], json={"status": "ok"})
            if self.llama["status"] != 200:
                return httpx.Response(self.llama["status"], json={"error": "loading"})
            return httpx.Response(200, json={
                "choices": [{"message": {"content": self.llama["content"]}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 50, "completion_tokens": 6},
                "timings": {"predicted_per_second": 38.24}})

        http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        self.client = TestClient(create_app(self.settings, token=TOKEN, http=http))
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.tmp.cleanup()

    def events(self, type_="exchange"):
        lines = self.settings.event_log_path.read_text(encoding="utf-8").splitlines()
        return [e for e in map(json.loads, lines) if e["type"] == type_]

    def sent_payload(self):
        return json.loads(self.requests[-1].content)

    def test_requires_token(self):
        self.assertEqual(self.client.post("/v1/chat", json={"message": "hi"}).status_code, 401)
        bad = {"Authorization": "Bearer nope"}
        self.assertEqual(self.client.post("/v1/chat", json={"message": "hi"}, headers=bad).status_code, 401)
        self.assertEqual(self.requests, [], "an unauthorised request must never reach Cameron")

    def test_health_needs_no_token_and_reports_cameron(self):
        self.assertEqual(self.client.get("/health").json(), {"cuddy": "ok", "cameron": "ok"})
        self.llama["status"] = 503
        self.assertEqual(self.client.get("/health").json()["cameron"], "loading")
        self.llama["error"] = httpx.ConnectError("refused")
        self.assertEqual(self.client.get("/health").json()["cameron"], "down")

    def test_chat_routes_to_cameron_with_persona_and_memory(self):
        r = self.client.post("/v1/chat", headers=AUTH,
                             json={"message": "  good   evening  ", "memory_context": "[2026-09-14]: built the audio library"})
        self.assertEqual(r.status_code, 200)
        body = r.json()
        self.assertEqual((body["status"], body["role"], body["text"]), ("success", "cameron", "Good evening, Sir."))
        payload = self.sent_payload()
        system, user = payload["messages"]
        self.assertEqual(system["role"], "system")
        self.assertIn("You are JARVIS", system["content"])
        self.assertIn("built the audio library", system["content"])
        self.assertEqual(user, {"role": "user", "content": "good evening"})
        self.assertEqual(payload["chat_template_kwargs"], {"enable_thinking": False})

    def test_reasoning_block_is_never_shown(self):
        self.llama["content"] = "<think>The user greets me.</think>\n\nGood evening, Sir."
        self.assertEqual(self.client.post("/v1/chat", headers=AUTH, json={"message": "hi"}).json()["text"],
                         "Good evening, Sir.")

    def test_unavailable_role_is_flagged_and_not_a_success(self):
        r = self.client.post("/v1/chat", headers=AUTH, json={"message": "prove P != NP", "role": "Foreman"})
        self.assertEqual(r.status_code, 503)
        body = r.json()
        self.assertEqual((body["status"], body["role"]), ("unavailable", "foreman"))
        self.assertIn("Foreman isn't available", body["text"])
        self.assertEqual(self.requests, [], "an unavailable role must not silently fall through to Cameron")
        self.assertEqual(self.events()[-1]["status"], "unavailable")
        self.assertEqual(self.client.post("/v1/chat", headers=AUTH, json={"message": "x", "role": "moriarty"}).status_code, 400)

    def test_cameron_down_or_loading_is_an_error_in_character(self):
        self.llama["error"] = httpx.ConnectError("refused")
        r = self.client.post("/v1/chat", headers=AUTH, json={"message": "hi"})
        self.assertEqual((r.status_code, r.json()["status"]), (503, "error"))
        self.assertIn("can't reach my faculties", r.json()["text"])
        self.llama.update(error=None, status=503)
        r = self.client.post("/v1/chat", headers=AUTH, json={"message": "hi"})
        self.assertIn("still waking up", r.json()["text"])

    def test_memory_summary_has_no_persona_and_keeps_lines(self):
        transcript = "Compress this.\n\n[t1] user: hello\n[t2] assistant: hi"
        r = self.client.post("/v1/chat", headers=AUTH, json={"message": transcript, "purpose": "memory_summary"})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(self.sent_payload()["messages"], [{"role": "user", "content": transcript}])

    def test_oversized_memory_transcript_keeps_instruction_and_newest_lines(self):
        transcript = "INSTRUCTION " + "x" * 30000 + " NEWEST"
        r = self.client.post("/v1/chat", headers=AUTH, json={"message": transcript, "purpose": "memory_summary"})
        self.assertTrue(r.json()["truncated"])
        sent = self.sent_payload()["messages"][0]["content"]
        self.assertTrue(sent.startswith("INSTRUCTION"))
        self.assertTrue(sent.endswith("NEWEST"))
        self.assertEqual(len(sent), self.settings.max_summary_chars)

    def test_empty_and_oversized_input(self):
        self.assertEqual(self.client.post("/v1/chat", headers=AUTH, json={"message": "   "}).status_code, 400)
        r = self.client.post("/v1/chat", headers=AUTH, json={"message": "a" * 5000})
        self.assertTrue(r.json()["truncated"])
        self.assertEqual(len(self.sent_payload()["messages"][1]["content"]), 4000)

    def test_event_log_has_metadata_but_no_conversation_text(self):
        self.client.post("/v1/chat", headers=AUTH, json={"message": "my secret plan", "memory_context": "private memory"})
        raw = self.settings.event_log_path.read_text(encoding="utf-8")
        self.assertNotIn("secret plan", raw)
        self.assertNotIn("private memory", raw)
        self.assertNotIn("Good evening", raw)
        event = self.events()[-1]
        self.assertEqual((event["status"], event["completion_tokens"], event["tokens_per_s"]), ("success", 6, 38.2))


if __name__ == "__main__":
    unittest.main()
