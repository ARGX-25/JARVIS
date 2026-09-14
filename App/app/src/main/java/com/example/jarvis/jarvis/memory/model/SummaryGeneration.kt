package com.example.jarvis.jarvis.memory.model

data class SummaryGeneration(
    val summary: String,
    val tokenEstimate: Int,
    val generatedBy: String
)
