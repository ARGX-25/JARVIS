package com.example.jarvis.jarvis.core

import com.example.jarvis.jarvis.models.AgentResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** A reply from Cuddy that is not a success. [spokenText] is Cuddy's in-character explanation, safe to show as-is. */
class CuddyException(
    val code: String,
    val spokenText: String,
    cause: Throwable? = null
) : IOException(code, cause)

/**
 * Client for the Cuddy server on the laptop (Backend/cuddy). Routing, roles and the JARVIS personality live there;
 * the phone sends text and shows the reply.
 *
 * [baseUrls] are tried in order, e.g. USB first (`adb reverse tcp:8765 tcp:8765` -> http://127.0.0.1:8765), then the
 * laptop's Wi-Fi address. The next URL is tried only when the connection itself fails, never once Cuddy has the
 * request, so a slow answer is never asked for twice. The URL that last worked is tried first next time.
 */
class CuddyClient(
    private val baseUrls: List<String>,
    private val token: String,
    private val client: OkHttpClient = defaultClient()
) {
    @Volatile
    private var preferred = 0

    fun chat(
        message: String,
        role: String,
        memoryContext: String? = null,
        purpose: String = PURPOSE_CHAT
    ): Result<AgentResponse> {
        if (token.isBlank()) return Result.failure(IllegalStateException(MISSING_TOKEN))
        if (baseUrls.isEmpty()) return Result.failure(IllegalStateException(NO_SERVER_URL))
        val payload = JSONObject().apply {
            put("message", message)
            put("role", role.lowercase())
            put("purpose", purpose)
            if (!memoryContext.isNullOrBlank()) put("memory_context", memoryContext)
        }.toString()

        var connectionError: IOException? = null
        val first = preferred
        for (offset in baseUrls.indices) {
            val index = (first + offset) % baseUrls.size
            val request = Request.Builder()
                .url(baseUrls[index].trimEnd('/') + Config.CUDDY_CHAT_PATH)
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                if (!e.isConnectionFailure()) return Result.failure(CuddyException(IO_ERROR, "", e))
                connectionError = e
                continue
            }
            preferred = index
            return response.use { parse(it) }
        }
        return Result.failure(CuddyException(UNREACHABLE, "", connectionError))
    }

    private fun parse(response: Response): Result<AgentResponse> {
        val body = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
        val status = body?.optString("status").orEmpty()
        val text = body?.optString("text").orEmpty()
        return when {
            response.code == 401 -> Result.failure(CuddyException(UNAUTHORIZED, ""))
            response.isSuccessful && status == "success" && text.isNotBlank() -> Result.success(AgentResponse(text))
            status == "unavailable" -> Result.failure(CuddyException(ROLE_UNAVAILABLE, text))
            else -> Result.failure(CuddyException("HTTP_${response.code}", text))
        }
    }

    private fun IOException.isConnectionFailure(): Boolean = when (this) {
        is ConnectException, is NoRouteToHostException, is UnknownHostException -> true
        is SocketTimeoutException -> message?.contains("connect", ignoreCase = true) == true
        else -> cause is ConnectException
    }

    companion object {
        const val PURPOSE_CHAT = "chat"
        const val PURPOSE_MEMORY_SUMMARY = "memory_summary"

        const val MISSING_TOKEN = "MISSING_CUDDY_TOKEN"
        const val NO_SERVER_URL = "NO_CUDDY_URL"
        const val UNAUTHORIZED = "CUDDY_UNAUTHORIZED"
        const val UNREACHABLE = "CUDDY_UNREACHABLE"
        const val IO_ERROR = "CUDDY_IO_ERROR"
        const val ROLE_UNAVAILABLE = "ROLE_UNAVAILABLE"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun parseUrls(csv: String): List<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Config.CUDDY_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Config.CUDDY_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }
}
