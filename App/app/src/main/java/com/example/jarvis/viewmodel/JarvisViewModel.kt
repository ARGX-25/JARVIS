package com.example.jarvis.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jarvis.BuildConfig
import com.example.jarvis.R
import com.example.jarvis.jarvis.core.CuddyClient
import com.example.jarvis.jarvis.core.Dispatcher
import com.example.jarvis.jarvis.core.Config
import com.example.jarvis.jarvis.core.InputProcessor
import com.example.jarvis.jarvis.core.Logger
import com.example.jarvis.jarvis.core.Router
import com.example.jarvis.jarvis.memory.MemoryManager
import com.example.jarvis.jarvis.memory.SessionStore
import com.example.jarvis.jarvis.memory.ai.CameronMemorySummarizer
import com.example.jarvis.jarvis.memory.ai.MemoryContextInjector
import com.example.jarvis.jarvis.memory.ai.MemorySummaryGenerator
import com.example.jarvis.jarvis.memory.db.MemoryDatabase
import com.example.jarvis.jarvis.service.CuddyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class JarvisViewModel(
    private val service: CuddyService,
    private val emptyInputMessage: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val context = service.loadMemoryForStartup()
            _uiState.update { state ->
                state.copy(
                    isMemoryLoaded = !context.isEmpty,
                    memoryTokenEstimate = context.totalTokenEstimate,
                    memorySummaryCount = context.formattedBlock.lines().count { line ->
                        line.startsWith("[") && line.contains("]:")
                    }
                )
            }
        }
    }

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
    }

    fun send() {
        val input = uiState.value.input
        if (input.isBlank()) {
            _uiState.update { it.copy(error = emptyInputMessage) }
            return
        }

        _uiState.update { state ->
            state.copy(
                isSending = true,
                error = null,
                messages = state.messages + ChatMessage(role = ChatRole.USER, text = input.trim())
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { service.handle(input) }
                .onSuccess { exchange ->
                    _uiState.update { state ->
                        state.copy(
                            input = "",
                            isSending = false,
                            messages = state.messages + ChatMessage(
                                role = ChatRole.ASSISTANT,
                                text = exchange.response,
                                meta = "${exchange.routedAgent} | ${exchange.status}"
                            )
                        )
                    }
                }
                .onFailure { error ->
                    val message = when (error.message) {
                        "INPUT_EMPTY" -> emptyInputMessage
                        else -> error.localizedMessage ?: emptyInputMessage
                    }
                    _uiState.update { state ->
                        state.copy(
                            isSending = false,
                            error = message,
                            messages = state.messages.dropLast(1)
                        )
                    }
                }
        }
    }

    fun setVoiceActive(active: Boolean) {
        _uiState.update { it.copy(isVoiceActive = active) }
    }

    fun onAppBackgrounded() {
        // Never keep the microphone open behind the user's back.
        _uiState.update { it.copy(isVoiceActive = false) }
        viewModelScope.launch(Dispatchers.IO) {
            service.onSessionBackground()
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val dispatcher = Dispatcher(
                        CuddyClient(
                            baseUrls = CuddyClient.parseUrls(BuildConfig.CUDDY_URLS),
                            token = BuildConfig.CUDDY_TOKEN
                        )
                    )
                    val database = MemoryDatabase.getInstance(appContext)
                    val memoryManager = MemoryManager(
                        dao = database.memorySummaryDao(),
                        sessionStore = SessionStore(),
                        summaryGenerator = MemorySummaryGenerator(
                            primarySummarizer = CameronMemorySummarizer(dispatcher)
                        ),
                        contextInjector = MemoryContextInjector(),
                        phase0LogFile = File(appContext.filesDir, Config.LOG_FILENAME)
                    )
                    val service = CuddyService(
                        inputProcessor = InputProcessor(),
                        router = Router(),
                        dispatcher = dispatcher,
                        logger = Logger(appContext),
                        fallbackMessage = appContext.getString(R.string.jarvis_fallback),
                        missingKeyMessage = appContext.getString(R.string.jarvis_missing_key),
                        memoryManager = memoryManager
                    )
                    return JarvisViewModel(
                        service = service,
                        emptyInputMessage = appContext.getString(R.string.jarvis_empty_input)
                    ) as T
                }
            }
    }
}

data class JarvisUiState(
    val input: String = "",
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            role = ChatRole.ASSISTANT,
            text = "Cuddy is online. Cameron now starts with compressed memory context when available."
        )
    ),
    val isSending: Boolean = false,
    val isVoiceActive: Boolean = false,
    val error: String? = null,
    val isMemoryLoaded: Boolean = false,
    val memoryTokenEstimate: Int = 0,
    val memorySummaryCount: Int = 0
)

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val meta: String? = null
)

enum class ChatRole {
    USER,
    ASSISTANT
}
