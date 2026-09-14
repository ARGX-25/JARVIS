package com.example.jarvis.jarvis.memory.ai

object MemoryPrompts {
    const val COMPRESSION_PROMPT_VERSION = "memory-compression-v1"

    const val COMPRESSION_PROMPT = """
You are a memory compression system for a personal AI assistant.
Compress this conversation into <=150 words.

Extract only:
- Active projects and their status
- Decisions made
- Tasks assigned or completed
- Preferences stated
- Important names, systems, references

No small talk. No greetings. Output the summary only.
"""

    const val BASE_SYSTEM_PROMPT = "You are JARVIS, a personal AI assistant for Arghya Dutta."
}
