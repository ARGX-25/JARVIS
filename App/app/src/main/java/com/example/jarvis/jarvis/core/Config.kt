package com.example.jarvis.jarvis.core

object Config {
    const val CAMERON_MODEL = "gemini-2.0-flash-lite"
    const val CAMERON_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$CAMERON_MODEL:generateContent"
    const val CAMERON_MAX_TOKENS = 1024
    const val CAMERON_TEMPERATURE = 0.7
    const val LOG_FILENAME = "jarvis_exchanges.jsonl"
}
