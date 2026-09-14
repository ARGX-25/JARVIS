package com.example.jarvis.jarvis.memory.ai

import com.example.jarvis.jarvis.memory.model.SessionMessage
import com.example.jarvis.jarvis.memory.model.SummaryGeneration

class MemorySummaryGenerator(
    private val primarySummarizer: ConversationSummarizer,
    private val fallbackSummarizer: ConversationSummarizer = FallbackMechanicalSummarizer()
) {
    suspend fun generate(messages: List<SessionMessage>): SummaryGeneration {
        val cleanMessages = messages.filter { it.content.isNotBlank() }
        val primary = primarySummarizer.summarize(cleanMessages).getOrNull()
        if (primary != null && primary.summary.isNotBlank()) {
            return primary
        }
        return fallbackSummarizer.summarize(cleanMessages).getOrThrow()
    }
}
