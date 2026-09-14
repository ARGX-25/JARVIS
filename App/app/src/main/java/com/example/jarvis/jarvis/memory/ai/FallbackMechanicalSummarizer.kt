package com.example.jarvis.jarvis.memory.ai

import com.example.jarvis.jarvis.memory.model.SessionMessage
import com.example.jarvis.jarvis.memory.model.SummaryGeneration

class FallbackMechanicalSummarizer(
    override val modelName: String = GENERATED_BY
) : ConversationSummarizer {
    override suspend fun summarize(messages: List<SessionMessage>): Result<SummaryGeneration> {
        val userMessages = messages
            .filter { it.role.equals(USER_ROLE, ignoreCase = true) }
            .mapNotNull { it.content.normalizeMemoryWhitespace().takeIf(String::isNotBlank) }

        val assistantMessages = messages
            .filterNot { it.role.equals(USER_ROLE, ignoreCase = true) }
            .mapNotNull { it.content.normalizeMemoryWhitespace().takeIf(String::isNotBlank) }

        val summary = buildList {
            if (userMessages.isNotEmpty()) {
                add("User discussed: ${userMessages.take(MAX_ITEMS).joinToString("; ") { it.limitChars(MAX_ITEM_CHARS) }}.")
            }
            if (assistantMessages.isNotEmpty()) {
                add("Assistant responded on: ${assistantMessages.take(MAX_ITEMS).joinToString("; ") { it.limitChars(MAX_ITEM_CHARS) }}.")
            }
        }.joinToString(" ").ifBlank {
            "Session contained no usable memory content."
        }.trimToWordLimit(MAX_SUMMARY_WORDS)

        return Result.success(
            SummaryGeneration(
                summary = summary,
                tokenEstimate = MemoryTokenEstimator.estimate(summary),
                generatedBy = GENERATED_BY
            )
        )
    }

    private fun String.limitChars(limit: Int): String =
        if (length <= limit) this else take(limit).trimEnd() + "..."

    private fun String.trimToWordLimit(limit: Int): String {
        val words = split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size <= limit) this else words.take(limit).joinToString(" ")
    }

    companion object {
        const val GENERATED_BY = "MECHANICAL_FALLBACK"
        private const val USER_ROLE = "user"
        private const val MAX_ITEMS = 3
        private const val MAX_ITEM_CHARS = 180
        private const val MAX_SUMMARY_WORDS = 150
    }
}
