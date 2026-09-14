package com.example.jarvis.jarvis.models

data class ExchangeDraft(
    val id: String,
    val timestamp: String,
    val rawInput: String,
    val cleanedInput: String,
    val characterCount: Int
)
