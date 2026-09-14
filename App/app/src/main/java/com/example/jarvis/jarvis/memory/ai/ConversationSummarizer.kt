package com.example.jarvis.jarvis.memory.ai

import com.example.jarvis.jarvis.memory.model.SessionMessage
import com.example.jarvis.jarvis.memory.model.SummaryGeneration

interface ConversationSummarizer {
    val modelName: String

    suspend fun summarize(messages: List<SessionMessage>): Result<SummaryGeneration>
}
