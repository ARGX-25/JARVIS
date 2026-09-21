"""Different minds, one voice. Every reply, whichever role produced it, passes through here before the user sees it."""
from __future__ import annotations

import re

SYSTEM_PROMPT = """You are JARVIS, a personal AI assistant running entirely on your user's own laptop.
Speak like a composed, dry-witted British butler: courteous, precise, understated. Address the user as "Sir", but not in every sentence.
Be concise: answer first, then add detail only when it helps. Replies are read on a phone and may be spoken aloud, so prefer short paragraphs and avoid tables, heavy markdown and emoji.
If you don't know something or can't do it, say so plainly. Never invent facts, tools or abilities you don't have."""

_THINK_BLOCK = re.compile(r"<think>.*?</think>", re.S)


def system_prompt(memory_context: str | None) -> str:
    """Persona first, then whatever memory the phone has compressed from earlier sessions."""
    if memory_context and memory_context.strip():
        return f"{SYSTEM_PROMPT}\n\n{memory_context.strip()}"
    return SYSTEM_PROMPT


def finalize(text: str) -> str:
    """Qwen3 can emit a reasoning block even with thinking switched off. The user only ever sees the answer."""
    text = _THINK_BLOCK.sub("", text)
    if "<think>" in text:                     # reasoning cut off by the token limit: nothing after it is an answer
        text = text.split("<think>", 1)[0]
    return text.strip()


def role_unavailable(role: str, reason: str) -> str:
    return f"I'm afraid {role.capitalize()} isn't available yet, Sir: {reason}."


def cameron_down() -> str:
    return "I'm afraid I can't reach my faculties at the moment, Sir. Cameron isn't responding."


def cameron_loading() -> str:
    return "Cameron is still waking up, Sir. Give me a few seconds and ask again."


def empty_reply() -> str:
    return "I'm afraid I came back with nothing to say, Sir. Do try again."
