package com.example.jarvis.jarvis.memory.ai

internal fun String.normalizeMemoryWhitespace(): String =
    replace(Regex("\\s+"), " ").trim()
