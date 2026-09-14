package com.example.jarvis.jarvis.memory.model

data class MemorySummaryRecord(
    val sessionId: String,
    val timestamp: String,
    val summary: String,
    val tokenEstimate: Int,
    val generatedBy: String
)
