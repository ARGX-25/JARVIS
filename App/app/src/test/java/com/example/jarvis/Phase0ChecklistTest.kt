package com.example.jarvis

import com.example.jarvis.jarvis.core.AgentDispatcher
import com.example.jarvis.jarvis.core.CuddyClient
import com.example.jarvis.jarvis.core.CuddyException
import com.example.jarvis.jarvis.core.ExchangeLogger
import com.example.jarvis.jarvis.core.InputProcessor
import com.example.jarvis.jarvis.core.Router
import com.example.jarvis.jarvis.models.AgentResponse
import com.example.jarvis.jarvis.models.Exchange
import com.example.jarvis.jarvis.service.CuddyService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase0ChecklistTest {

    @Test
    fun inputProcessor_trimsAndTruncatesTo4000() {
        val processor = InputProcessor()
        val longInput = "  " + "a".repeat(4500) + "   "

        val result = processor.process(longInput)

        assertEquals(4000, result.cleanedInput.length)
        assertEquals(4000, result.characterCount)
        assertTrue(result.id.isNotBlank())
        assertTrue(result.timestamp.isNotBlank())
    }

    @Test
    fun router_returnsCameronWithConfidenceAndReason() {
        val router = Router()

        val result = router.route()

        assertEquals("CAMERON", result.agent)
        assertEquals(1.0, result.confidence, 0.0)
        assertEquals("phase0_default", result.reason)
    }

    @Test
    fun cuddyService_handlesTenConsecutiveQueries_withoutCrash() {
        val dispatcher = object : AgentDispatcher {
            override fun dispatch(agent: String, prompt: String): Result<AgentResponse> {
                return Result.success(AgentResponse("Echo: $prompt"))
            }
        }
        val logger = InMemoryLogger()
        val service = CuddyService(
            inputProcessor = InputProcessor(),
            router = Router(),
            dispatcher = dispatcher,
            logger = logger,
            fallbackMessage = "fallback",
            missingKeyMessage = "missing"
        )

        repeat(10) { index ->
            val exchange = service.handle("query $index")
            assertEquals("CAMERON", exchange.routedAgent)
            assertEquals("success", exchange.status)
            assertTrue(exchange.response.startsWith("Echo:"))
        }

        assertEquals(10, logger.entries.size)
    }

    @Test
    fun cuddyService_unavailableRole_isFlaggedNotCountedAsSuccess() {
        val spoken = "I'm afraid Foreman isn't available yet, Sir: shelved until the model bake-off is done."
        val exchange = serviceFailingWith(CuddyException(CuddyClient.ROLE_UNAVAILABLE, spoken)).handle("prove it")

        assertEquals("unavailable", exchange.status)
        assertEquals(spoken, exchange.response)
    }

    @Test
    fun cuddyService_cameronDown_showsCuddysOwnExplanation() {
        val spoken = "I'm afraid I can't reach my faculties at the moment, Sir."
        val exchange = serviceFailingWith(CuddyException("HTTP_503", spoken)).handle("hello")

        assertEquals("fallback", exchange.status)
        assertEquals(spoken, exchange.response)
    }

    @Test
    fun cuddyService_laptopUnreachable_usesFallbackMessage() {
        val exchange = serviceFailingWith(CuddyException(CuddyClient.UNREACHABLE, "")).handle("hello")

        assertEquals("fallback", exchange.status)
        assertEquals("fallback", exchange.response)
    }

    @Test
    fun cuddyService_missingOrRejectedToken_asksForToken() {
        val missing = serviceFailingWith(IllegalStateException(CuddyClient.MISSING_TOKEN)).handle("hello")
        val rejected = serviceFailingWith(CuddyException(CuddyClient.UNAUTHORIZED, "")).handle("hello")

        assertEquals("missing", missing.response)
        assertEquals("missing", rejected.response)
    }

    @Test
    fun cuddyClient_parsesUrlList_inOrder() {
        assertEquals(
            listOf("http://127.0.0.1:8765", "http://192.168.29.55:8765"),
            CuddyClient.parseUrls(" http://127.0.0.1:8765 , http://192.168.29.55:8765,, ")
        )
        assertTrue(CuddyClient.parseUrls("").isEmpty())
    }

    private fun serviceFailingWith(error: Throwable) = CuddyService(
        inputProcessor = InputProcessor(),
        router = Router(),
        dispatcher = object : AgentDispatcher {
            override fun dispatch(agent: String, prompt: String): Result<AgentResponse> = Result.failure(error)
        },
        logger = InMemoryLogger(),
        fallbackMessage = "fallback",
        missingKeyMessage = "missing"
    )
}

private class InMemoryLogger : ExchangeLogger {
    val entries = mutableListOf<Exchange>()

    override fun log(exchange: Exchange) {
        entries += exchange
    }
}
