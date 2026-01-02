package com.turbometa.rayban.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turbometa.rayban.R
import com.turbometa.rayban.data.ConversationStorage
import com.turbometa.rayban.models.ConversationMessage
import com.turbometa.rayban.models.ConversationRecord
import com.turbometa.rayban.models.MessageRole
import com.turbometa.rayban.services.AIProviderConfig
import com.turbometa.rayban.services.OmniRealtimeService
import com.turbometa.rayban.utils.APIKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class OmniRealtimeViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyManager = APIKeyManager.getInstance(application)
    private val conversationStorage = ConversationStorage.getInstance(application)

    private var realtimeService: OmniRealtimeService? = null

    // State
    sealed class ViewState {
        object Idle : ViewState()
        object Connecting : ViewState()
        object Connected : ViewState()
        object Recording : ViewState()
        object Processing : ViewState()
        object Speaking : ViewState()
        data class Error(val message: String) : ViewState()
    }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Idle)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _debugMessages = MutableStateFlow<List<String>>(emptyList())
    val debugMessages: StateFlow<List<String>> = _debugMessages.asStateFlow()

    private val _showDebugOverlay = MutableStateFlow(false)
    val showDebugOverlay: StateFlow<Boolean> = _showDebugOverlay.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var pendingVideoFrame: Bitmap? = null

    init {
        initializeService()
    }

    private fun initializeService() {
        // Get the selected provider and its API key
        val selectedProvider = apiKeyManager.getSelectedProvider()
        val apiKey = apiKeyManager.getAPIKey(selectedProvider)
        
        if (apiKey.isNullOrBlank()) {
            val providerName = selectedProvider.getDisplayName(getApplication())
            _errorMessage.value = getApplication<Application>().getString(
                R.string.error_api_key_not_configured,
                providerName
            )
            return
        }

        // Get provider configuration
        val providerConfig = AIProviderConfig.getProviderConfig(getApplication(), selectedProvider)
        val language = apiKeyManager.getOutputLanguage()

        realtimeService = OmniRealtimeService(
            context = getApplication(),
            apiKey = apiKey,
            providerConfig = providerConfig,
            provider = selectedProvider,
            outputLanguage = language
        ).apply {
            onTranscriptDelta = { delta ->
                _currentTranscript.value += delta
            }

            onTranscriptDone = { transcript ->
                if (transcript.isNotBlank()) {
                    addAssistantMessage(transcript)
                }
                _currentTranscript.value = ""
                _viewState.value = ViewState.Connected
            }

            onUserTranscript = { transcript ->
                if (transcript.isNotBlank()) {
                    _userTranscript.value = transcript
                    addUserMessage(transcript)
                }
            }

            onSpeechStarted = {
                _viewState.value = ViewState.Recording
            }

            onSpeechStopped = {
                _viewState.value = ViewState.Processing
            }

            onError = { error ->
                _errorMessage.value = error
                _viewState.value = ViewState.Error(error)
            }

            onDebugMessage = { message ->
                addDebugMessage(message)
            }
        }

        // Observe service states
        viewModelScope.launch {
            realtimeService?.isConnected?.collect { connected ->
                _isConnected.value = connected
                if (connected && _viewState.value == ViewState.Connecting) {
                    _viewState.value = ViewState.Connected
                } else if (!connected && _viewState.value != ViewState.Idle) {
                    _viewState.value = ViewState.Idle
                }
            }
        }

        viewModelScope.launch {
            realtimeService?.isRecording?.collect { recording ->
                _isRecording.value = recording
            }
        }

        viewModelScope.launch {
            realtimeService?.isSpeaking?.collect { speaking ->
                _isSpeaking.value = speaking
                if (speaking) {
                    _viewState.value = ViewState.Speaking
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            if (_isConnected.value) return@launch

            _viewState.value = ViewState.Connecting
            _messages.value = emptyList()
            currentSessionId = UUID.randomUUID().toString()

            realtimeService?.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            saveCurrentConversation()
            realtimeService?.disconnect()
            _viewState.value = ViewState.Idle
            _messages.value = emptyList()
            _currentTranscript.value = ""
            _userTranscript.value = ""
        }
    }

    fun startRecording() {
        if (!_isConnected.value) {
            _errorMessage.value = getApplication<Application>().getString(R.string.error_not_connected)
            return
        }

        // Update video frame if available
        pendingVideoFrame?.let { frame ->
            realtimeService?.updateVideoFrame(frame)
        }

        realtimeService?.startRecording()
        _viewState.value = ViewState.Recording
    }

    fun stopRecording() {
        realtimeService?.stopRecording()
        if (_viewState.value == ViewState.Recording) {
            _viewState.value = ViewState.Processing
        }
    }

    fun updateVideoFrame(frame: Bitmap) {
        pendingVideoFrame = frame
        realtimeService?.updateVideoFrame(frame)
    }

    private fun addUserMessage(text: String) {
        val message = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + message
    }

    private fun addAssistantMessage(text: String) {
        val message = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = text,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + message
    }

    private fun saveCurrentConversation() {
        if (_messages.value.isEmpty()) return

        val record = ConversationRecord(
            id = currentSessionId,
            timestamp = System.currentTimeMillis(),
            messages = _messages.value,
            aiModel = apiKeyManager.getAIModel(),
            language = apiKeyManager.getOutputLanguage()
        )

        conversationStorage.saveConversation(record)
    }

    fun clearError() {
        _errorMessage.value = null
        realtimeService?.clearError()
        if (_viewState.value is ViewState.Error) {
            _viewState.value = if (_isConnected.value) ViewState.Connected else ViewState.Idle
        }
    }

    fun refreshService() {
        realtimeService?.disconnect()
        realtimeService = null
        initializeService()
    }

    fun toggleDebugOverlay() {
        _showDebugOverlay.value = !_showDebugOverlay.value
    }

    private fun addDebugMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val debugMsg = "[$timestamp] $message"
        
        _debugMessages.value = (_debugMessages.value + debugMsg).takeLast(50) // Keep last 50 messages
    }

    fun clearDebugMessages() {
        _debugMessages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        saveCurrentConversation()
        realtimeService?.disconnect()
    }
}
