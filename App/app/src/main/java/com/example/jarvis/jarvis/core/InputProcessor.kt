package com.example.jarvis.jarvis.core

import com.example.jarvis.jarvis.models.ExchangeDraft

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InputProcessor {
    fun process(rawInput: String): ExchangeDraft {
        val cleanedInput = rawInput.trim().replace(Regex("\\s+"), " ")
        require(cleanedInput.isNotEmpty()) { "INPUT_EMPTY" }
        val truncatedInput = cleanedInput.take(MAX_INPUT_LENGTH)

        return ExchangeDraft(
            id = UUID.randomUUID().toString(),
            timestamp = timestampFormatter.format(Date()),
            rawInput = rawInput,
            cleanedInput = truncatedInput,
            characterCount = truncatedInput.length
        )
    }

    companion object {
        const val MAX_INPUT_LENGTH = 4000
        private val timestampFormatter =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    }
}
