package com.example.jarvis.jarvis.core

import com.example.jarvis.jarvis.models.AgentResponse

/**
 * Sends every role request to Cuddy on the laptop, where the roles actually run. A role with no model behind it
 * comes back as a ROLE_UNAVAILABLE failure, never as a successful reply.
 */
class Dispatcher(private val cuddy: CuddyClient) : AgentDispatcher {

    override fun dispatch(agent: String, prompt: String): Result<AgentResponse> =
        cuddy.chat(message = prompt, role = agent)

    override fun dispatchWithSystemPrompt(
        agent: String,
        prompt: String,
        systemPrompt: String
    ): Result<AgentResponse> =
        cuddy.chat(message = prompt, role = agent, memoryContext = systemPrompt)

    override fun dispatchMemorySummary(agent: String, prompt: String): Result<AgentResponse> =
        cuddy.chat(message = prompt, role = agent, purpose = CuddyClient.PURPOSE_MEMORY_SUMMARY)
}
