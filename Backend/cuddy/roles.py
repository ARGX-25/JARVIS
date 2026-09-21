"""The Secretariat. Roles are cognitive functions, not models: code references the role, and the model behind a
role can be swapped without renaming anything."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Role:
    name: str
    purpose: str
    model: str | None
    available: bool
    reason: str = ""


ROLES = {r.name: r for r in [
    Role("cameron", "General conversation; the default mind", "Qwen3-8B-Q4_K_M (llama.cpp, GPU)", True),
    Role("wilson", "Strong general reasoning", "GPT-OSS-20B-MXFP4", False, "shelved until the machine has 32 GB of RAM"),
    Role("foreman", "Deep reasoning, maths, algorithms, debugging, architecture", None, False,
         "shelved until the model bake-off is done"),
    Role("chase", "Documents and vision", "olmOCR-2-7B + Qwen3-VL-8B", False, "not built yet"),
    Role("house", "Final authority, arbitration, escalation", None, False,
         "there is no model for that role on this machine"),
]}


def route(message: str) -> str:
    """Only Cameron is live, so the router has exactly one answer. No selection logic until there is a choice."""
    return "cameron"
