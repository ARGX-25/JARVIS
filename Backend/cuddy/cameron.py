"""Cameron: the general-conversation role, currently Qwen3-8B served by llama.cpp's llama-server."""
from __future__ import annotations

import httpx

# Qwen3 non-thinking sampling as recommended by the Qwen team.
SAMPLING = {"temperature": 0.7, "top_p": 0.8, "top_k": 20, "min_p": 0.0}


class CameronUnavailable(Exception):
    def __init__(self, detail: str, loading: bool = False):
        super().__init__(detail)
        self.loading = loading


class CameronClient:
    def __init__(self, base_url: str, http: httpx.AsyncClient, max_tokens: int):
        self.base_url = base_url.rstrip("/")
        self.http = http
        self.max_tokens = max_tokens

    async def health(self) -> str:
        """"ok", "loading" (model still loading) or "down"."""
        try:
            r = await self.http.get(f"{self.base_url}/health")
        except httpx.HTTPError:
            return "down"
        if r.status_code == 200:
            return "ok"
        return "loading" if r.status_code == 503 else "down"

    async def complete(self, messages: list[dict]) -> tuple[str, dict]:
        payload = {"messages": messages, "max_tokens": self.max_tokens, "stream": False,
                   "chat_template_kwargs": {"enable_thinking": False}, **SAMPLING}
        try:
            r = await self.http.post(f"{self.base_url}/v1/chat/completions", json=payload)
        except httpx.HTTPError as e:
            raise CameronUnavailable(f"{type(e).__name__}: {e}") from e
        if r.status_code == 503:
            raise CameronUnavailable("llama-server is loading the model", loading=True)
        if r.status_code != 200:
            raise CameronUnavailable(f"llama-server HTTP {r.status_code}: {r.text[:200]}")
        data = r.json()
        choice = data["choices"][0]
        usage, timings = data.get("usage") or {}, data.get("timings") or {}
        meta = {"prompt_tokens": usage.get("prompt_tokens"), "completion_tokens": usage.get("completion_tokens"),
                "finish_reason": choice.get("finish_reason"),
                "tokens_per_s": round(timings["predicted_per_second"], 1) if "predicted_per_second" in timings else None}
        return choice["message"].get("content") or "", meta
