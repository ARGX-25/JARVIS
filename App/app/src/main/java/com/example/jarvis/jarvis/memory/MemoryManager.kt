package com.example.jarvis.jarvis.memory

import com.example.jarvis.jarvis.memory.ai.MemoryContextInjector
import com.example.jarvis.jarvis.memory.ai.MemorySummaryGenerator
import com.example.jarvis.jarvis.memory.db.MemorySummary
import com.example.jarvis.jarvis.memory.db.MemorySummaryDao
import com.example.jarvis.jarvis.memory.model.MemoryContext
import com.example.jarvis.jarvis.memory.model.MemorySummaryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MemoryManager(
    private val dao: MemorySummaryDao,
    private val sessionStore: SessionStore,
    private val summaryGenerator: MemorySummaryGenerator,
    private val contextInjector: MemoryContextInjector,
    private val phase0LogFile: File? = null
) {
    suspend fun recordUserMessage(content: String, timestamp: String = SessionStore.isoTimestamp()) {
        withContext(Dispatchers.Default) {
            sessionStore.add(role = USER_ROLE, content = content, timestamp = timestamp)
        }
    }

    suspend fun recordAssistantMessage(content: String, timestamp: String = SessionStore.isoTimestamp()) {
        withContext(Dispatchers.Default) {
            sessionStore.add(role = ASSISTANT_ROLE, content = content, timestamp = timestamp)
        }
    }

    suspend fun onSessionBackground(): SummaryWriteResult = withContext(Dispatchers.IO) {
        val snapshot = sessionStore.snapshot()
        if (snapshot.userMessageCount < MIN_USER_MESSAGES) {
            return@withContext SummaryWriteResult.SkippedTooSmall
        }

        runCatching {
            val generated = summaryGenerator.generate(snapshot.messages)
            val summary = generated.summary.take(MAX_SUMMARY_CHARS)
            dao.insertOrReplace(
                MemorySummary(
                    sessionId = snapshot.sessionId,
                    timestamp = SessionStore.isoTimestamp(),
                    summary = summary,
                    tokenEstimate = summary.length / TOKEN_CHAR_RATIO,
                    generatedBy = generated.generatedBy
                )
            )
            SummaryWriteResult.Stored
        }.getOrElse {
            SummaryWriteResult.FailedSafely
        }
    }

    suspend fun loadContextForStartup(): MemoryContext = withContext(Dispatchers.IO) {
        runCatching {
            importPhase0LogsIfPresent()
            val summaries = dao.getLastSevenOldestFirst()
                .mapNotNull { it.toRecordOrNull() }
            contextInjector.formatForSystemPrompt(summaries)
        }.getOrElse {
            MemoryContext(formattedBlock = "", isEmpty = true, totalTokenEstimate = 0)
        }
    }

    suspend fun clearAllMemory() = withContext(Dispatchers.IO) {
        dao.clearAll()
        sessionStore.clearAll()
    }

    private suspend fun importPhase0LogsIfPresent() {
        val file = phase0LogFile ?: return
        if (!file.exists()) return

        Phase0SummaryImporter(dao).importSummaries(file)
        file.delete()
    }

    private fun MemorySummary.toRecordOrNull(): MemorySummaryRecord? {
        if (sessionId.isBlank() || summary.isBlank()) return null
        return MemorySummaryRecord(
            sessionId = sessionId,
            timestamp = timestamp,
            summary = summary,
            tokenEstimate = tokenEstimate,
            generatedBy = generatedBy
        )
    }

    sealed interface SummaryWriteResult {
        data object Stored : SummaryWriteResult
        data object SkippedTooSmall : SummaryWriteResult
        data object FailedSafely : SummaryWriteResult
    }

    companion object {
        private const val USER_ROLE = "user"
        private const val ASSISTANT_ROLE = "assistant"
        private const val MIN_USER_MESSAGES = 2
        private const val MAX_SUMMARY_CHARS = 2000
        private const val TOKEN_CHAR_RATIO = 4
    }
}
