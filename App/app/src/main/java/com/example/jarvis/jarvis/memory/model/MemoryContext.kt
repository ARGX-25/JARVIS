package com.example.jarvis.jarvis.memory.model

data class MemoryContext(
    val formattedBlock: String,
    val isEmpty: Boolean,
    val totalTokenEstimate: Int
)
