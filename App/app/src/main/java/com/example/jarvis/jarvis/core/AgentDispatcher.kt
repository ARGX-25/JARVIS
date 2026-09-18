package com.example.jarvis.jarvis.core

import com.example.jarvis.jarvis.models.AgentResponse

interface AgentDispatcher {
    fun dispatch(agent: String, prompt: String): Result<AgentResponse>

    fun dispatchWithSystemPrompt(
        agent: String,
        prompt: String,
        systemPrompt: String
    ): Result<AgentResponse> = dispatch(agent, prompt)

    /** Memory compression: the prompt is the whole instruction, so no persona is applied on the way. */
    fun dispatchMemorySummary(agent: String, prompt: String): Result<AgentResponse> = dispatch(agent, prompt)
}
