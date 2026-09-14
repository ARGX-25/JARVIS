package com.example.jarvis.jarvis.agents

import com.example.jarvis.jarvis.core.Config
import com.example.jarvis.jarvis.models.AgentResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class CameronAgent(
    private val apiKey: String,
    private val client: OkHttpClient
) {
    fun call(
        query: String,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION
    ): Result<AgentResponse> {
        val payload = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().apply {
                    put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", systemInstruction)
                        )
                    )
                }
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", query)))
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", Config.CAMERON_TEMPERATURE)
                    put("maxOutputTokens", Config.CAMERON_MAX_TOKENS)
                }
            )
        }

        val request = Request.Builder()
            .url("${Config.CAMERON_ENDPOINT}?key=$apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("HTTP_${response.code}"))
                }

                val body = response.body?.string().orEmpty()
                val text = parseResponse(body)
                if (text.isBlank()) Result.failure(IOException("EMPTY_RESPONSE"))
                else Result.success(AgentResponse(text))
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun parseResponse(body: String): String {
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val texts = mutableListOf<String>()
        for (index in 0 until parts.length()) {
            val value = parts.getJSONObject(index).optString("text")
            if (value.isNotBlank()) {
                texts += value
            }
        }
        return texts.joinToString("\n").trim()
    }

    companion object {
        const val DEFAULT_SYSTEM_INSTRUCTION = "You are JARVIS, a helpful AI assistant."
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
