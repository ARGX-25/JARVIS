package com.example.jarvis.jarvis.models

data class Exchange(
    val id: String,
    val timestamp: String,
    val rawInput: String,
    val cleanedInput: String,
    val characterCount: Int,
    val routedAgent: String,
    val confidence: Double,
    val routingReason: String,
    val response: String,
    val status: String,
    val latencyMs: Long,
    val error: String? = null
)
