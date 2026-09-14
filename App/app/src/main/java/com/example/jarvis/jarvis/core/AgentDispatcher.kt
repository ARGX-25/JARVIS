package com.example.jarvis.jarvis.core

import com.example.jarvis.jarvis.models.AgentResponse

interface AgentDispatcher {
    fun dispatch(agent: String, prompt: String): Result<AgentResponse>

    fun dispatchWithSystemPrompt(
        agent: String,
        prompt: String,
        systemPrompt: String
    ): Result<AgentResponse> = dispatch(agent, prompt)
}
