package com.example.jarvis.jarvis.memory

import com.example.jarvis.jarvis.memory.model.SessionMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionStore(
    initialSessionId: String = todaySessionId()
) {
    private val messages = mutableListOf<SessionMessage>()
    private var activeSessionId: String = initialSessionId

    @Synchronized
    fun add(role: String, content: String, timestamp: String = isoTimestamp()) {
        clearForNewDay(todaySessionId())
        messages += SessionMessage(
            role = role,
            content = content,
            timestamp = timestamp
        )
    }

    @Synchronized
    fun snapshot(): SessionSnapshot =
        SessionSnapshot(
            sessionId = activeSessionId,
            messages = messages.toList(),
            userMessageCount = messages.count { it.role.equals(USER_ROLE, ignoreCase = true) }
        )

    @Synchronized
    fun clearForNewDay(newSessionId: String) {
        if (newSessionId == activeSessionId) return
        messages.clear()
        activeSessionId = newSessionId
    }

    @Synchronized
    fun clearAll() {
        messages.clear()
        activeSessionId = todaySessionId()
    }

    data class SessionSnapshot(
        val sessionId: String,
        val messages: List<SessionMessage>,
        val userMessageCount: Int
    )

    companion object {
        private const val USER_ROLE = "user"
        private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

        fun todaySessionId(): String = dayFormatter.format(Date())
        fun isoTimestamp(): String = timestampFormatter.format(Date())
    }
}
