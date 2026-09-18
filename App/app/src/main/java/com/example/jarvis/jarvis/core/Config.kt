package com.example.jarvis.jarvis.core

object Config {
    /** Cameron's current model, served by llama.cpp on the laptop behind Cuddy. Recorded with memory summaries. */
    const val CAMERON_MODEL = "qwen3-8b-q4_k_m"

    const val CUDDY_CHAT_PATH = "/v1/chat"
    const val CUDDY_CONNECT_TIMEOUT_MS = 2_000L     // short: an unreachable address should fall through to the next quickly
    const val CUDDY_READ_TIMEOUT_MS = 180_000L      // long: an 8B model on a laptop GPU can take a while on a long answer

    const val LOG_FILENAME = "jarvis_exchanges.jsonl"

    // Legacy Gemini settings, referenced only by the unused agents/CameronAgent.kt. Delete together with agents/.
    const val CAMERON_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent"
    const val CAMERON_MAX_TOKENS = 1024
    const val CAMERON_TEMPERATURE = 0.7
}
