package com.example.jarvis.jarvis.core

import com.example.jarvis.jarvis.agents.CameronAgent
import com.example.jarvis.jarvis.agents.ChaseAgent
import com.example.jarvis.jarvis.agents.ForemanAgent
import com.example.jarvis.jarvis.agents.HouseAgent
import com.example.jarvis.jarvis.agents.WilsonAgent
import com.example.jarvis.jarvis.models.AgentResponse
import okhttp3.OkHttpClient

class Dispatcher(
    private val apiKey: String,
    client: OkHttpClient = OkHttpClient()
) : AgentDispatcher {
    private val cameronAgent = CameronAgent(apiKey = apiKey, client = client)
    private val wilsonAgent = WilsonAgent()
    private val foremanAgent = ForemanAgent()
    private val chaseAgent = ChaseAgent()
    private val houseAgent = HouseAgent()

    override fun dispatch(agent: String, prompt: String): Result<AgentResponse> {
        return dispatchInternal(agent = agent, prompt = prompt, systemPrompt = null)
    }

    override fun dispatchWithSystemPrompt(
        agent: String,
        prompt: String,
        systemPrompt: String
    ): Result<AgentResponse> {
        return dispatchInternal(agent = agent, prompt = prompt, systemPrompt = systemPrompt)
    }

    private fun dispatchInternal(
        agent: String,
        prompt: String,
        systemPrompt: String?
    ): Result<AgentResponse> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("MISSING_API_KEY"))
        }

        return when (agent) {
            "CAMERON" -> cameronAgent.call(
                query = prompt,
                systemInstruction = systemPrompt ?: CameronAgent.DEFAULT_SYSTEM_INSTRUCTION
            )
            "WILSON" -> Result.success(wilsonAgent.call(prompt))
            "FOREMAN" -> Result.success(foremanAgent.call(prompt))
            "CHASE" -> Result.success(chaseAgent.call(prompt))
            "HOUSE" -> Result.success(houseAgent.call(prompt))
            else -> cameronAgent.call(
                query = prompt,
                systemInstruction = systemPrompt ?: CameronAgent.DEFAULT_SYSTEM_INSTRUCTION
            )
        }
    }
}
