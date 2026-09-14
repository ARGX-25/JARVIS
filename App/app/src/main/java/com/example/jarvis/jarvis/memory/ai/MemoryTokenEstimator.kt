package com.example.jarvis.jarvis.memory.ai

object MemoryTokenEstimator {
    fun estimate(text: String): Int = (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)

    private const val CHARS_PER_TOKEN = 4
}
