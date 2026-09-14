package com.example.jarvis.jarvis.memory.ai

import com.example.jarvis.jarvis.core.AgentDispatcher
import com.example.jarvis.jarvis.core.Config
import com.example.jarvis.jarvis.memory.model.SessionMessage
import com.example.jarvis.jarvis.memory.model.SummaryGeneration

class CameronMemorySummarizer(
    private val dispatcher: AgentDispatcher,
    private val agentName: String = CAMERON_AGENT_NAME,
    override val modelName: String = Config.CAMERON_MODEL
) : ConversationSummarizer {
    override suspend fun summarize(messages: List<SessionMessage>): Result<SummaryGeneration> {
        val prompt = buildPrompt(messages)
        return dispatcher.dispatch(agentName, prompt).mapCatching { response ->
            val summary = response.text.normalizeMemoryWhitespace().trimToWordLimit(MAX_SUMMARY_WORDS)
            if (summary.isBlank()) {
                error("EMPTY_MEMORY_SUMMARY")
            }
            SummaryGeneration(
                summary = summary,
                tokenEstimate = MemoryTokenEstimator.estimate(summary),
                generatedBy = agentName
            )
        }
    }

    private fun buildPrompt(messages: List<SessionMessage>): String {
        val transcript = messages.joinToString(separator = "\n") { message ->
            val role = message.role.trim().ifBlank { "unknown" }
            val content = message.content.normalizeMemoryWhitespace()
            "[${message.timestamp}] ${role}: $content"
        }

        return """
${MemoryPrompts.COMPRESSION_PROMPT.trim()}

Conversation transcript, oldest to newest:
$transcript
""".trim()
    }

    private fun String.trimToWordLimit(limit: Int): String {
        val words = split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size <= limit) this else words.take(limit).joinToString(" ")
    }

    companion object {
        const val CAMERON_AGENT_NAME = "CAMERON"
        private const val MAX_SUMMARY_WORDS = 150
    }
}
