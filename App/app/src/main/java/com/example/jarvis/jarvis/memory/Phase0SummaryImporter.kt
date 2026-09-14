package com.example.jarvis.jarvis.memory

import com.example.jarvis.jarvis.memory.db.MemorySummary
import com.example.jarvis.jarvis.memory.db.MemorySummaryDao
import org.json.JSONObject
import java.io.File

class Phase0SummaryImporter(
    private val dao: MemorySummaryDao
) {
    suspend fun importSummaries(jsonlFile: File): ImportResult {
        if (!jsonlFile.exists()) {
            return ImportResult(imported = 0, skipped = 0)
        }

        var skipped = 0
        val explicitSummaries = mutableListOf<MemorySummary>()
        val phase0RawByDay = linkedMapOf<String, MutableList<String>>()

        jsonlFile.useLines { lines ->
            lines.forEach { line ->
                val summary = parseSummaryLine(line)
                if (summary == null) {
                    val rawSummary = parsePhase0RawLine(line)
                    if (rawSummary == null) {
                        skipped += 1
                    } else {
                        phase0RawByDay.getOrPut(rawSummary.sessionId) { mutableListOf() } += rawSummary.summary
                    }
                } else {
                    explicitSummaries += summary
                }
            }
        }

        val compressedPhase0Summaries = phase0RawByDay.map { (sessionId, items) ->
            val summary = "Phase 0 imported summary: " +
                items.joinToString("; ")
                    .replace(Regex("\\s+"), " ")
                    .take(MAX_SUMMARY_CHARS)
            MemorySummary(
                sessionId = sessionId,
                timestamp = "${sessionId}T00:00:00",
                summary = summary,
                tokenEstimate = summary.length / TOKEN_CHAR_RATIO,
                generatedBy = IMPORT_GENERATOR
            )
        }

        val summaries = explicitSummaries + compressedPhase0Summaries
        summaries.forEach { dao.insertOrReplace(it) }

        return ImportResult(imported = summaries.size, skipped = skipped)
    }

    private fun parseSummaryLine(line: String): MemorySummary? {
        if (line.isBlank()) return null

        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        val summary = json.firstPresentString("summary", "memory_summary", "compressed_summary")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_SUMMARY_CHARS)
            ?: return null

        val timestamp = json.firstPresentString("timestamp", "created_at", "generated_at")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val sessionId = json.firstPresentString("sessionId", "session_id", "date")
            ?.takeIf { it.isNotBlank() }
            ?: timestamp.take(SESSION_ID_LENGTH)
                .takeIf { SESSION_ID_REGEX.matches(it) }
            ?: return null

        return MemorySummary(
            sessionId = sessionId,
            timestamp = timestamp,
            summary = summary,
            tokenEstimate = summary.length / TOKEN_CHAR_RATIO,
            generatedBy = json.firstPresentString("generatedBy", "generated_by")
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_GENERATOR
        )
    }

    private fun parsePhase0RawLine(line: String): MemorySummary? {
        if (line.isBlank()) return null

        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        val timestamp = json.firstPresentString("timestamp")
            ?.takeIf { it.length >= SESSION_ID_LENGTH }
            ?: return null
        val sessionId = timestamp.take(SESSION_ID_LENGTH)
            .takeIf { SESSION_ID_REGEX.matches(it) }
            ?: return null
        val userInput = json.firstPresentString("user_input")
            ?.take(MAX_IMPORTED_FIELD_CHARS)
            ?: return null
        val response = json.firstPresentString("agent_response")
            ?.take(MAX_IMPORTED_FIELD_CHARS)
            ?: ""
        val summary = buildString {
            append("User asked: ")
            append(userInput)
            if (response.isNotBlank()) {
                append(". Assistant replied: ")
                append(response)
            }
        }.take(MAX_SUMMARY_CHARS)

        return MemorySummary(
            sessionId = sessionId,
            timestamp = timestamp,
            summary = summary,
            tokenEstimate = summary.length / TOKEN_CHAR_RATIO,
            generatedBy = IMPORT_GENERATOR
        )
    }

    private fun JSONObject.firstPresentString(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = optString(key).trim()
            if (value.isNotBlank()) return value
        }
        return null
    }

    data class ImportResult(
        val imported: Int,
        val skipped: Int
    )

    companion object {
        private const val DEFAULT_GENERATOR = "CAMERON"
        private const val IMPORT_GENERATOR = "PHASE0_IMPORT"
        private const val MAX_SUMMARY_CHARS = 2000
        private const val MAX_IMPORTED_FIELD_CHARS = 240
        private const val SESSION_ID_LENGTH = 10
        private const val TOKEN_CHAR_RATIO = 4
        private val SESSION_ID_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
