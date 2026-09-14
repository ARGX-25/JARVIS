package com.example.jarvis.jarvis.memory.ai

import com.example.jarvis.jarvis.memory.model.MemoryContext
import com.example.jarvis.jarvis.memory.model.MemorySummaryRecord

class MemoryContextInjector(
    private val tokenCap: Int = DEFAULT_TOKEN_CAP
) {
    fun formatForSystemPrompt(summariesOldestToNewest: List<MemorySummaryRecord>): MemoryContext {
        val usableSummaries = summariesOldestToNewest
            .filter { it.sessionId.isNotBlank() && it.summary.isNotBlank() }
            .map { summary ->
                summary.copy(
                    summary = summary.summary.normalizeMemoryWhitespace(),
                    tokenEstimate = normalizedTokenEstimate(summary)
                )
            }
            .toMutableList()

        while (usableSummaries.sumOf { it.tokenEstimate } > tokenCap && usableSummaries.isNotEmpty()) {
            usableSummaries.removeAt(0)
        }

        val totalTokens = usableSummaries.sumOf { it.tokenEstimate }
        if (usableSummaries.isEmpty()) {
            return MemoryContext(formattedBlock = "", isEmpty = true, totalTokenEstimate = 0)
        }

        val memoryLines = usableSummaries.joinToString(separator = "\n") { summary ->
            "[${summary.sessionId}]: ${summary.summary}"
        }

        val block = """
${MemoryPrompts.BASE_SYSTEM_PROMPT}

Relevant Memory Context (oldest to newest):
$memoryLines

Use this context naturally. Do not announce that you remember things.
""".trim()

        return MemoryContext(
            formattedBlock = block,
            isEmpty = false,
            totalTokenEstimate = totalTokens
        )
    }

    private fun normalizedTokenEstimate(summary: MemorySummaryRecord): Int {
        val estimated = if (summary.tokenEstimate > 0) {
            summary.tokenEstimate
        } else {
            MemoryTokenEstimator.estimate(summary.summary)
        }
        return estimated.coerceAtLeast(1)
    }

    companion object {
        const val DEFAULT_TOKEN_CAP = 800
    }
}
