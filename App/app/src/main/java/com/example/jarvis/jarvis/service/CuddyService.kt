package com.example.jarvis.jarvis.service

import com.example.jarvis.jarvis.core.AgentDispatcher
import com.example.jarvis.jarvis.core.CuddyClient
import com.example.jarvis.jarvis.core.CuddyException
import com.example.jarvis.jarvis.core.ExchangeLogger
import com.example.jarvis.jarvis.core.InputProcessor
import com.example.jarvis.jarvis.core.Router
import com.example.jarvis.jarvis.memory.MemoryManager
import com.example.jarvis.jarvis.memory.model.MemoryContext
import com.example.jarvis.jarvis.models.AgentResponse
import com.example.jarvis.jarvis.models.Exchange

import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

class CuddyService(
    private val inputProcessor: InputProcessor,
    private val router: Router,
    private val dispatcher: AgentDispatcher,
    private val logger: ExchangeLogger,
    private val fallbackMessage: String,
    private val missingKeyMessage: String,
    private val memoryManager: MemoryManager? = null
) {
    @Volatile
    private var startupMemoryContext = MemoryContext(
        formattedBlock = "",
        isEmpty = true,
        totalTokenEstimate = 0
    )

    fun handle(rawInput: String): Exchange {
        val draft = inputProcessor.process(rawInput)
        memoryManager?.let { manager ->
            runBlocking {
                manager.recordUserMessage(content = draft.cleanedInput, timestamp = draft.timestamp)
            }
        }
        val routingDecision = router.route()

        var dispatchResult: Result<AgentResponse> = Result.failure(IllegalStateException("UNINITIALIZED"))
        val latencyMs = measureTimeMillis {
            dispatchResult = if (startupMemoryContext.isEmpty) {
                dispatcher.dispatch(routingDecision.agent, draft.cleanedInput)
            } else {
                dispatcher.dispatchWithSystemPrompt(
                    agent = routingDecision.agent,
                    prompt = draft.cleanedInput,
                    systemPrompt = startupMemoryContext.formattedBlock
                )
            }
        }
        val responseText = dispatchResult.getOrElse { error ->
            when {
                // Cuddy explains its own failures in character (role unavailable, Cameron down): show that as-is.
                error is CuddyException && error.spokenText.isNotBlank() -> AgentResponse(error.spokenText)
                error.message == CuddyClient.MISSING_TOKEN || error.message == CuddyClient.UNAUTHORIZED ->
                    AgentResponse(missingKeyMessage)
                else -> AgentResponse(fallbackMessage)
            }
        }.text

        val exchange = Exchange(
            id = draft.id,
            timestamp = draft.timestamp,
            rawInput = draft.rawInput,
            cleanedInput = draft.cleanedInput,
            characterCount = draft.characterCount,
            routedAgent = routingDecision.agent,
            confidence = routingDecision.confidence,
            routingReason = routingDecision.reason,
            response = responseText,
            status = when {
                dispatchResult.isSuccess -> "success"
                dispatchResult.exceptionOrNull()?.message == CuddyClient.ROLE_UNAVAILABLE -> "unavailable"
                else -> "fallback"
            },
            latencyMs = latencyMs,
            error = dispatchResult.exceptionOrNull()?.message
        )

        logger.log(exchange)
        memoryManager?.let { manager ->
            runBlocking {
                manager.recordAssistantMessage(content = responseText)
            }
        }
        return exchange
    }

    suspend fun loadMemoryForStartup(): MemoryContext {
        val context = memoryManager?.loadContextForStartup()
            ?: MemoryContext(formattedBlock = "", isEmpty = true, totalTokenEstimate = 0)
        startupMemoryContext = context
        return context
    }

    suspend fun onSessionBackground(): MemoryManager.SummaryWriteResult =
        memoryManager?.onSessionBackground()
            ?: MemoryManager.SummaryWriteResult.FailedSafely

    suspend fun clearAllMemory() {
        memoryManager?.clearAllMemory()
        startupMemoryContext = MemoryContext(formattedBlock = "", isEmpty = true, totalTokenEstimate = 0)
    }

    fun currentMemoryContext(): MemoryContext = startupMemoryContext
}
