package com.example.jarvis

import com.example.jarvis.jarvis.memory.MemoryManager
import com.example.jarvis.jarvis.memory.SessionStore
import com.example.jarvis.jarvis.memory.ai.ConversationSummarizer
import com.example.jarvis.jarvis.memory.ai.MemoryContextInjector
import com.example.jarvis.jarvis.memory.ai.MemorySummaryGenerator
import com.example.jarvis.jarvis.memory.db.MemorySummary
import com.example.jarvis.jarvis.memory.db.MemorySummaryDao
import com.example.jarvis.jarvis.memory.model.SessionMessage
import com.example.jarvis.jarvis.memory.model.SummaryGeneration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1MemoryTest {

    @Test
    fun memoryManager_skipsOneUserMessage() = runBlocking {
        val dao = FakeMemorySummaryDao()
        val manager = memoryManager(dao = dao)

        manager.recordUserMessage("hello", "2026-05-21T09:00:00")
        val result = manager.onSessionBackground()

        assertEquals(MemoryManager.SummaryWriteResult.SkippedTooSmall, result)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun memoryManager_storesTwoUserMessageSummaryAndReplacesSameDay() = runBlocking {
        val dao = FakeMemorySummaryDao()
        val manager = memoryManager(dao = dao)

        manager.recordUserMessage("Build the memory spine.", "2026-05-21T09:00:00")
        manager.recordAssistantMessage("I will wire persistence.", "2026-05-21T09:01:00")
        manager.recordUserMessage("Keep summaries compressed only.", "2026-05-21T09:02:00")

        assertEquals(MemoryManager.SummaryWriteResult.Stored, manager.onSessionBackground())
        assertEquals(MemoryManager.SummaryWriteResult.Stored, manager.onSessionBackground())
        assertEquals(1, dao.rows.size)
        assertEquals("CAMERON", dao.rows.single().generatedBy)
        assertFalse(dao.rows.single().summary.contains("raw conversation log", ignoreCase = true))
    }

    @Test
    fun contextInjector_trimsOldestUntilUnderTokenCap() = runBlocking {
        val dao = FakeMemorySummaryDao()
        repeat(10) { index ->
            dao.insertOrReplace(
                MemorySummary(
                    sessionId = "2026-05-${(index + 1).toString().padStart(2, '0')}",
                    timestamp = "2026-05-${(index + 1).toString().padStart(2, '0')}T00:00:00",
                    summary = "x".repeat(480),
                    tokenEstimate = 120,
                    generatedBy = "CAMERON"
                )
            )
        }
        val manager = memoryManager(dao = dao)

        val context = manager.loadContextForStartup()

        assertTrue(context.totalTokenEstimate <= 800)
        assertFalse(context.formattedBlock.contains("[2026-05-04]:"))
        assertTrue(context.formattedBlock.contains("[2026-05-05]:"))
        assertTrue(context.formattedBlock.contains("[2026-05-10]:"))
    }

    @Test
    fun clearAllMemory_wipesDatabaseAndSessionStoreTogether() = runBlocking {
        val dao = FakeMemorySummaryDao()
        val sessionStore = SessionStore(initialSessionId = "2026-05-21")
        val manager = memoryManager(dao = dao, sessionStore = sessionStore)

        manager.recordUserMessage("one", "2026-05-21T09:00:00")
        manager.recordUserMessage("two", "2026-05-21T09:01:00")
        manager.onSessionBackground()
        manager.clearAllMemory()

        assertTrue(dao.rows.isEmpty())
        assertEquals(0, sessionStore.snapshot().messages.size)
    }

    private fun memoryManager(
        dao: FakeMemorySummaryDao,
        sessionStore: SessionStore = SessionStore(initialSessionId = "2026-05-21")
    ): MemoryManager =
        MemoryManager(
            dao = dao,
            sessionStore = sessionStore,
            summaryGenerator = MemorySummaryGenerator(TestSummarizer()),
            contextInjector = MemoryContextInjector()
        )
}

private class TestSummarizer : ConversationSummarizer {
    override val modelName: String = "test-cameron"

    override suspend fun summarize(messages: List<SessionMessage>): Result<SummaryGeneration> {
        val summary = "Project: ${messages.filter { it.role == "user" }.joinToString("; ") { it.content }}"
        return Result.success(
            SummaryGeneration(
                summary = summary,
                tokenEstimate = summary.length / 4,
                generatedBy = "CAMERON"
            )
        )
    }
}

private class FakeMemorySummaryDao : MemorySummaryDao() {
    val rows = mutableListOf<MemorySummary>()

    override suspend fun getLastSevenOldestFirst(): List<MemorySummary> =
        rows.sortedWith(compareBy<MemorySummary> { it.timestamp }.thenBy { it.id })
            .takeLast(7)
            .sortedWith(compareBy<MemorySummary> { it.timestamp }.thenBy { it.id })

    override suspend fun clearAll() {
        rows.clear()
    }

    override suspend fun insertOrReplaceInternal(summary: MemorySummary) {
        rows.removeAll { it.sessionId == summary.sessionId }
        rows += summary.copy(id = summary.id.takeIf { it != 0L } ?: (rows.size + 1L))
    }

    override suspend fun pruneOldestBeyond(maxRows: Int) {
        val kept = rows.sortedWith(compareByDescending<MemorySummary> { it.timestamp }.thenByDescending { it.id })
            .take(maxRows)
            .map { it.sessionId }
            .toSet()
        rows.removeAll { it.sessionId !in kept }
    }
}
