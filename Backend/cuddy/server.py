"""Cuddy HTTP server: the phone sends text, Cuddy routes it to a role, the role answers, the personality layer speaks.

POST /v1/chat   (Authorization: Bearer <token>)
    {"message": "...", "memory_context": "...optional...", "purpose": "chat" | "memory_summary", "role": "optional"}
    200 -> {"status": "success", "role": "cameron", "text": "...", ...}
    503 -> {"status": "unavailable"}   the requested role has no model yet. NOT a success.
    503 -> {"status": "error"}         Cameron (llama-server) is down or still loading. NOT a success.
    Any non-200 carries an in-character `text` the phone can show as-is.
GET /health     no auth; says whether Cuddy and Cameron are up, nothing else.
GET /v1/roles   the Secretariat and who is available.

The event log records metadata only (ids, timings, sizes, status). Conversation text never touches disk.
"""
from __future__ import annotations

import secrets
import time
import uuid
from contextlib import asynccontextmanager
from typing import Literal

import httpx
from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel

from . import personality
from .cameron import CameronClient, CameronUnavailable
from .config import Settings, load_or_create_token
from .events import EventLog
from .roles import ROLES, route


class ChatRequest(BaseModel):
    message: str
    memory_context: str | None = None
    purpose: Literal["chat", "memory_summary"] = "chat"
    role: str | None = None            # address a role directly; omit and the router decides


class ChatResponse(BaseModel):
    id: str
    role: str
    status: Literal["success", "unavailable", "error"]
    text: str
    latency_ms: int
    truncated: bool = False
    error: str | None = None


def create_app(settings: Settings, token: str | None = None, http: httpx.AsyncClient | None = None) -> FastAPI:
    token = token or load_or_create_token(settings.token_path)
    events = EventLog(settings.event_log_path)
    state: dict = {}

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        client = http or httpx.AsyncClient(timeout=httpx.Timeout(settings.request_timeout_s,
                                                                 connect=settings.connect_timeout_s))
        state["cameron"] = CameronClient(settings.llama_url, client, settings.max_tokens)
        events.write("server_start", host=settings.host, port=settings.port, llama_url=settings.llama_url)
        try:
            yield
        finally:
            if http is None:
                await client.aclose()

    app = FastAPI(title="Cuddy", lifespan=lifespan)
    bearer = HTTPBearer(auto_error=False)

    def authorised(creds: HTTPAuthorizationCredentials | None = Depends(bearer)) -> None:
        if creds is None or not secrets.compare_digest(creds.credentials, token):
            raise HTTPException(401, "UNAUTHORIZED", headers={"WWW-Authenticate": "Bearer"})

    @app.get("/health")
    async def health():
        return {"cuddy": "ok", "cameron": await state["cameron"].health()}

    @app.get("/v1/roles", dependencies=[Depends(authorised)])
    async def roles():
        return [{"role": r.name, "purpose": r.purpose, "model": r.model, "available": r.available, "reason": r.reason}
                for r in ROLES.values()]

    @app.post("/v1/chat", response_model=ChatResponse, dependencies=[Depends(authorised)])
    async def chat(req: ChatRequest):
        t0 = time.perf_counter()
        exchange_id = str(uuid.uuid4())
        if req.purpose == "chat":
            message, limit = " ".join(req.message.split()), settings.max_chat_chars
        else:                                        # a transcript: keep its line structure
            message, limit = req.message.strip(), settings.max_summary_chars
        if not message:
            raise HTTPException(400, "INPUT_EMPTY")
        truncated = len(message) > limit
        if truncated and req.purpose == "memory_summary":
            # keep the compression instruction at the top and the newest end of the transcript
            message = message[:1000] + "\n[...]\n" + message[-(limit - 1007):]
        else:
            message = message[:limit]

        role_name = (req.role or route(message)).strip().lower()
        role = ROLES.get(role_name)
        if role is None:
            raise HTTPException(400, f"UNKNOWN_ROLE: {req.role}")

        def reply(status_code, status, text, error=None, meta=None):
            body = ChatResponse(id=exchange_id, role=role.name, status=status, text=personality.finalize(text),
                                latency_ms=round((time.perf_counter() - t0) * 1000), truncated=truncated, error=error)
            events.write("exchange", id=exchange_id, role=role.name, purpose=req.purpose, status=status,
                         latency_ms=body.latency_ms, input_chars=len(message), output_chars=len(body.text),
                         truncated=truncated, memory_context=bool(req.memory_context), error=error, **(meta or {}))
            return JSONResponse(body.model_dump(), status_code=status_code)

        if not role.available:
            return reply(503, "unavailable", personality.role_unavailable(role.name, role.reason),
                         error=f"ROLE_UNAVAILABLE: {role.name}")

        if req.purpose == "memory_summary":          # the compression prompt is the whole instruction: no persona
            messages = [{"role": "user", "content": message}]
        else:
            messages = [{"role": "system", "content": personality.system_prompt(req.memory_context)},
                        {"role": "user", "content": message}]
        try:
            raw, meta = await state["cameron"].complete(messages)
        except CameronUnavailable as e:
            text = personality.cameron_loading() if e.loading else personality.cameron_down()
            return reply(503, "error", text, error=f"CAMERON_UNAVAILABLE: {e}")
        if not personality.finalize(raw):
            return reply(502, "error", personality.empty_reply(), error="EMPTY_RESPONSE", meta=meta)
        return reply(200, "success", raw, meta=meta)

    return app
