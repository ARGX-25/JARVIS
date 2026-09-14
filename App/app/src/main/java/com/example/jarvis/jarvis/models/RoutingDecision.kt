package com.example.jarvis.jarvis.models

data class RoutingDecision(
    val agent: String,
    val confidence: Double,
    val reason: String
)
