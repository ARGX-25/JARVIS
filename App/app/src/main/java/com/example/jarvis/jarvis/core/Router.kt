package com.example.jarvis.jarvis.core

import com.example.jarvis.jarvis.models.RoutingDecision

class Router {
    fun route(): RoutingDecision {
        val decision = RoutingDecision(
            agent = "CAMERON",
            confidence = 1.0,
            reason = "phase0_default"
        )
        return if (decision.agent in knownAgents) decision else fallbackDecision()
    }

    private fun fallbackDecision(): RoutingDecision = RoutingDecision(
        agent = "CAMERON",
        confidence = 1.0,
        reason = "fallback_unknown_agent"
    )

    companion object {
        private val knownAgents = setOf("CAMERON", "WILSON", "FOREMAN", "CHASE", "HOUSE")
    }
}
