package com.turbometa.rayban.services

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.turbometa.rayban.R
import com.turbometa.rayban.utils.AIProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Alibaba Qwen Omni Realtime Service
 * Supports multi-region endpoints (Beijing/Singapore)
 * 1:1 port from iOS OmniRealtimeService.swift
 */
class OmniRealtimeService(
    private val context: Context,
    private val apiKey: String,
    private val providerConfig: ProviderEndpoints,
    private val provider: AIProvider,
    private val outputLanguage: String = "zh-CN"
) {
    companion object {
        private const val TAG = "OmniRealtimeService"
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val websocketURL: String
        get() = when (endpoint) {
            AlibabaEndpoint.BEIJING -> WS_BEIJING_URL
            AlibabaEndpoint.SINGAPORE -> WS_SINGAPORE_URL
        }

    // State
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Callbacks
    var onTranscriptDelta: ((String) -> Unit)? = null
    var onTranscriptDone: ((String) -> Unit)? = null
    var onUserTranscript: ((String) -> Unit)? = null
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechStopped: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onDebugMessage: ((String) -> Unit)? = null  // New debug callback

    // Internal
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var audioPlaybackJob: Job? = null
    private val audioQueue = mutableListOf<ByteArray>()
    private val gson = Gson()
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pendingImageFrame: Bitmap? = null
    
    // Provider detection
    private val isOpenAI: Boolean
        get() = providerConfig.wsBaseUrl.contains("openai", ignoreCase = true)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        if (_isConnected.value) return

        val url = "${providerConfig.wsBaseUrl}?model=${providerConfig.realtimeModel}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _isConnected.value = true
                sendSessionUpdate()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
                // Show more for error/failed messages, less for normal messages
                val debugText = if (text.contains("\"error\"") || text.contains("\"failed\"")) {
                    text.take(800) // Show more for errors
                } else {
                    text.take(300) // Show more for normal messages too
                }
                onDebugMessage?.invoke("📥 RCV: $debugText")
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                _isConnected.value = false
                _errorMessage.value = t.message
                onError?.invoke(t.message ?: context.getString(R.string.error_connection_failed))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                _isConnected.value = false
            }
        })
    }

    fun disconnect() {
        stopRecording()
        stopAudioPlayback()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
        _isRecording.value = false
        _isSpeaking.value = false
        scope.cancel()
    }

    fun startRecording() {
        if (_isRecording.value) return

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            lastImageSentTime = 0  // 重置，确保立即发送第一张图片

            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                while (isActive && _isRecording.value) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        sendAudioData(buffer.copyOf(bytesRead))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied")
            _errorMessage.value = context.getString(R.string.error_microphone_permission)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
            _errorMessage.value = e.message
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun updateVideoFrame(frame: Bitmap) {
        pendingImageFrame = frame
    }

    private fun sendSessionUpdate() {
        // Use mode manager if context is available, otherwise fall back to language-based prompt
        val instructions = context?.let {
            val modeManager = LiveAIModeManager.getInstance(it)
            modeManager.getSystemPrompt()
        } ?: getLiveAIPrompt(outputLanguage)

        val systemPrompt = when (outputLanguage) {
            "zh-CN" -> "你是RayBan Meta智能眼镜AI助手。$languageInstruction 回答要简练，通常在1-3句话内完成。如果用户询问你看到了什么，请描述视觉画面中的内容。"
            "en-US" -> "You are the RayBan Meta smart glasses AI assistant. $languageInstruction Keep answers concise, typically 1-3 sentences. If the user asks what you see, describe the visual content."
            "ja-JP" -> "RayBan Metaスマートグラスのアシスタントです。$languageInstruction 回答は簡潔に、通常1〜3文で。ユーザーが何が見えるか尋ねたら、視覚的な内容を説明してください。"
            "ko-KR" -> "RayBan Meta 스마트 안경 AI 어시스턴트입니다. $languageInstruction 답변은 간결하게, 보통 1-3문장으로. 사용자가 무엇이 보이는지 물으면 시각적 내용을 설명하세요."
            "es-ES" -> "Eres el asistente de IA de las gafas inteligentes RayBan Meta. $languageInstruction Mantén las respuestas concisas, típicamente de 1-3 frases. Si el usuario pregunta qué ves, describe el contenido visual."
            "fr-FR" -> "Vous êtes l'assistant IA des lunettes intelligentes RayBan Meta. $languageInstruction Gardez les réponses concises, généralement 1-3 phrases. Si l'utilisateur demande ce que vous voyez, décrivez le contenu visuel."
            else -> "你是RayBan Meta智能眼镜AI助手。$languageInstruction 回答要简练，通常在1-3句话内完成。如果用户询问你看到了什么，请描述视觉画面中的内容。"
        }

        // Build session configuration based on provider
        val sessionMap = mutableMapOf<String, Any>()

        // OpenAI Realtime API format
        if (isOpenAI) {
            sessionMap["type"] = "realtime"
            sessionMap["model"] = providerConfig.realtimeModel
            sessionMap["output_modalities"] = listOf("audio")
            sessionMap["instructions"] = systemPrompt
            sessionMap["audio"] = mapOf(
                "input" to mapOf(
                    "format" to mapOf(
                        "type" to "audio/pcm",
                        "rate" to 24000
                    ),
                    "turn_detection" to mapOf(
                        "type" to "server_vad",
                        "threshold" to 0.5,
                        "prefix_padding_ms" to 300,
                        "silence_duration_ms" to 500
                    )
                ),
                "output" to mapOf(
                    "format" to mapOf(
                        "type" to "audio/pcm",
                        "rate" to 24000
                    ),
                    "voice" to providerConfig.voice
                )
            )
        } else {
            // Alibaba Cloud format
            sessionMap["modalities"] = listOf("text", "audio")
            sessionMap["voice"] = providerConfig.voice
            sessionMap["instructions"] = systemPrompt
            sessionMap["input_audio_format"] = "pcm16"
            sessionMap["output_audio_format"] = "pcm16"
            sessionMap["smooth_output"] = true
            sessionMap["turn_detection"] = mapOf(
                "type" to "server_vad",
                "threshold" to 0.5,
                "silence_duration_ms" to 800
            )
        }

        val sessionConfig = mapOf(
            "type" to "session.update",
            "session" to sessionMap
        )

        val json = gson.toJson(sessionConfig)
        Log.d(TAG, "Sending session config: $json")
        onDebugMessage?.invoke("📤 SEND: session.update")
        webSocket?.send(json)
    }

    private fun createResponse() {
        if (!_isConnected.value) return

        val responseConfig = mapOf(
            "type" to "response.create"
        )

        val json = gson.toJson(responseConfig)
        Log.d(TAG, "Requesting response creation: $json")
        onDebugMessage?.invoke("📤 SEND: response.create")
        webSocket?.send(json)
    }

    /**
     * Get localized Live AI prompt matching iOS implementation
     */
    private fun getLiveAIPrompt(language: String): String {
        return when (language) {
            "zh-CN" -> """
                你是RayBan Meta智能眼镜AI助手。

                【重要】必须始终用中文回答，无论用户说什么语言。

                回答要简练、口语化，像朋友聊天一样。用户戴着眼镜可以看到周围环境，根据画面快速给出有用的建议。不要啰嗦，直接说重点。
            """.trimIndent()
            "en-US" -> """
                You are a RayBan Meta smart glasses AI assistant.

                [IMPORTANT] Always respond in English.

                Keep your answers concise and conversational, like chatting with a friend. The user is wearing glasses and can see their surroundings, provide quick and useful suggestions based on what they see. Be direct and to the point.
            """.trimIndent()
            "ja-JP" -> """
                あなたはRayBan Metaスマートグラスのアシスタントです。

                【重要】常に日本語で回答してください。

                回答は簡潔で会話的に、友達とチャットするように。ユーザーは眼鏡をかけて周囲を見ています。見えるものに基づいて素早く有用なアドバイスを。要点を直接伝えてください。
            """.trimIndent()
            "ko-KR" -> """
                당신은 RayBan Meta 스마트 안경 AI 어시스턴트입니다.

                【중요】항상 한국어로 응답하세요.

                친구와 대화하듯이 간결하고 대화적으로 답변하세요. 사용자는 안경을 착용하고 주변을 볼 수 있습니다. 보이는 것에 따라 빠르고 유용한 조언을 제공하세요. 요점만 말하세요.
            """.trimIndent()
            else -> getLiveAIPrompt("en-US")
        }
    }

    private fun sendAudioData(audioData: ByteArray) {
        if (!_isConnected.value) return

        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val message = mapOf(
            "type" to "input_audio_buffer.append",
            "audio" to base64Audio
        )

        webSocket?.send(gson.toJson(message))
        Log.v(TAG, "Sent audio data: ${audioData.size} bytes")

        // 定期发送图片（每 500ms 发送一次）
        val currentTime = System.currentTimeMillis()
        if (pendingImageFrame != null && (currentTime - lastImageSentTime >= imageSendIntervalMs)) {
            lastImageSentTime = currentTime
            sendImageFrame(pendingImageFrame!!)
        }
    }

    private fun sendImageFrame(bitmap: Bitmap) {
        try {
            if (isOpenAI) {
                // For OpenAI, analyze image with vision API and inject as context
                scope.launch {
                    try {
                        Log.d(TAG, "Analyzing image with GPT-4 Vision for OpenAI Realtime context")
                        onDebugMessage?.invoke("📷 Analyzing image...")
                        
                        val visionService = VisionAPIService(context, provider)
                        val result = visionService.analyzeImage(bitmap, "Describe what you see in this image briefly.")
                        
                        result.onSuccess { visionDescription ->
                            Log.d(TAG, "Vision context: $visionDescription")
                            onDebugMessage?.invoke("👁️ Vision: ${visionDescription.take(100)}")
                            
                            // Inject vision context as a system message in the conversation
                            val contextMessage = mapOf(
                                "type" to "conversation.item.create",
                                "item" to mapOf(
                                    "type" to "message",
                                    "role" to "system",
                                    "content" to listOf(
                                        mapOf(
                                            "type" to "input_text",
                                            "text" to "Context: User's current view shows: $visionDescription"
                                        )
                                    )
                                )
                            )
                            webSocket?.send(gson.toJson(contextMessage))
                            Log.d(TAG, "Vision context injected into conversation")
                        }.onFailure { error ->
                            Log.e(TAG, "Vision API error: ${error.message}")
                            onDebugMessage?.invoke("❌ Vision error: ${error.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error analyzing image: ${e.message}", e)
                    }
                }
                return
            }
            
            // Alibaba Cloud: Direct image buffer append
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val bytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val message = mapOf(
                "type" to "input_image_buffer.append",
                "image" to base64Image
            )

            webSocket?.send(gson.toJson(message))
            Log.d(TAG, "Image frame sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending image: ${e.message}")
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return
            
            Log.d(TAG, "Processing message type: $type")

            when (type) {
                "session.created", "session.updated" -> {
                    Log.d(TAG, "Session ready: $text")
                }
                "response.created" -> {
                    Log.d(TAG, "Response created")
                    onDebugMessage?.invoke("🎯 Response created")
                }
                "response.output_item.added" -> {
                    Log.d(TAG, "Output item added: $text")
                    onDebugMessage?.invoke("📝 Output item added")
                }
                "response.content_part.added" -> {
                    Log.d(TAG, "Content part added: $text")
                    onDebugMessage?.invoke("📝 Content part added")
                }
                "input_audio_buffer.speech_started" -> {
                    Log.d(TAG, "User speech started")
                    _isSpeaking.value = false
                    stopAudioPlayback()
                    onSpeechStarted?.invoke()
                }
                "input_audio_buffer.speech_stopped" -> {
                    Log.d(TAG, "User speech stopped")
                    onSpeechStopped?.invoke()
                    // Note: With server_vad turn_detection, OpenAI automatically creates a response
                    // No need to call response.create manually
                }
                "response.audio_transcript.delta" -> {
                    val delta = json.get("delta")?.asString ?: ""
                    Log.d(TAG, "Transcript delta: $delta")
                    _currentTranscript.value += delta
                    onTranscriptDelta?.invoke(delta)
                }
                "response.audio_transcript.done" -> {
                    val transcript = _currentTranscript.value
                    Log.d(TAG, "Transcript done: $transcript")
                    onTranscriptDone?.invoke(transcript)
                    _currentTranscript.value = ""
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = json.get("transcript")?.asString ?: ""
                    Log.d(TAG, "User transcript: $transcript")
                    onUserTranscript?.invoke(transcript)
                }
                // OpenAI event names
                "response.output_audio_transcript.delta" -> {
                    val delta = json.get("delta")?.asString ?: ""
                    Log.d(TAG, "Transcript delta (OpenAI): $delta")
                    _currentTranscript.value += delta
                    onTranscriptDelta?.invoke(delta)
                }
                "response.output_audio_transcript.done" -> {
                    val transcript = json.get("transcript")?.asString ?: _currentTranscript.value
                    Log.d(TAG, "Transcript done (OpenAI): $transcript")
                    onTranscriptDone?.invoke(transcript)
                    _currentTranscript.value = ""
                }
                "response.output_audio.delta" -> {
                    Log.d(TAG, "Audio delta event received (OpenAI), full JSON: $text")
                    onDebugMessage?.invoke("🔊 Audio event (OpenAI): ${text.take(200)}")
                    
                    val audioData = json.get("delta")?.asString
                    if (audioData != null) {
                        val audioBytes = Base64.decode(audioData, Base64.DEFAULT)
                        Log.d(TAG, "Audio delta decoded: ${audioBytes.size} bytes")
                        onDebugMessage?.invoke("🔊 Playing audio: ${audioBytes.size} bytes")
                        playAudio(audioBytes)
                    } else {
                        Log.w(TAG, "Audio delta is null in response.output_audio.delta")
                        onDebugMessage?.invoke("⚠️ Audio delta is null!")
                    }
                }
                "response.output_audio.done" -> {
                    Log.d(TAG, "Audio response done (OpenAI)")
                    _isSpeaking.value = false
                }
                // Alibaba Cloud event names
                "response.audio.delta" -> {
                    Log.d(TAG, "Audio delta event received (Alibaba), full JSON: $text")
                    onDebugMessage?.invoke("🔊 Audio event (Alibaba): ${text.take(200)}")
                    
                    val audioData = json.get("delta")?.asString
                    if (audioData != null) {
                        val audioBytes = Base64.decode(audioData, Base64.DEFAULT)
                        Log.d(TAG, "Audio delta decoded: ${audioBytes.size} bytes")
                        onDebugMessage?.invoke("🔊 Playing audio: ${audioBytes.size} bytes")
                        playAudio(audioBytes)
                    } else {
                        Log.w(TAG, "Audio delta is null in response.audio.delta")
                        onDebugMessage?.invoke("⚠️ Audio delta is null!")
                    }
                }
                "response.audio.done" -> {
                    Log.d(TAG, "Audio response done (Alibaba)")
                    _isSpeaking.value = false
                }
                "response.done" -> {
                    val response = json.get("response")?.asJsonObject
                    val status = response?.get("status")?.asString
                    Log.d(TAG, "Response done with status: $status")
                    
                    if (status == "failed") {
                        val statusDetails = response?.get("status_details")?.asJsonObject
                        val errorType = statusDetails?.get("type")?.asString
                        val errorObj = statusDetails?.get("error")?.asJsonObject
                        val errorCode = errorObj?.get("code")?.asString
                        val errorMessage = errorObj?.get("message")?.asString
                        
                        val fullError = "Response failed - Type: $errorType, Code: $errorCode, Message: $errorMessage"
                        Log.e(TAG, fullError)
                        onDebugMessage?.invoke("❌ ERROR: $fullError")
                        _errorMessage.value = errorMessage ?: context.getString(R.string.error_response_failed)
                        onError?.invoke(fullError)
                    }
                }
                "error" -> {
                    val errorMsg = json.get("error")?.asJsonObject?.get("message")?.asString
                    Log.e(TAG, "Server error: $errorMsg")
                    _errorMessage.value = errorMsg
                    onError?.invoke(errorMsg ?: "Unknown error")
                }
                else -> {
                    Log.d(TAG, "Unhandled message type: $type")
                    // Log all unhandled message types to debug
                    if (type.contains("audio") || type.contains("response")) {
                        onDebugMessage?.invoke("⚠️ Unhandled: $type")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}", e)
        }
    }

    private fun playAudio(audioData: ByteArray) {
        Log.d(TAG, "playAudio called with ${audioData.size} bytes, queue size: ${audioQueue.size}")
        onDebugMessage?.invoke("🔊 Queuing audio: ${audioData.size} bytes, queue: ${audioQueue.size}")
        synchronized(audioQueue) {
            audioQueue.add(audioData)
        }

        if (audioPlaybackJob?.isActive != true) {
            Log.d(TAG, "Starting audio playback")
            onDebugMessage?.invoke("▶️ Starting audio playback")
            startAudioPlayback()
        }
    }

    private fun startAudioPlayback() {
        if (audioTrack == null) {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // 使用 AudioAttributes 替代已弃用的 STREAM_MUSIC（兼容性更好）
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize * 2,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
        }

        _isSpeaking.value = true

        audioPlaybackJob = scope.launch {
            while (isActive) {
                val data = synchronized(audioQueue) {
                    if (audioQueue.isNotEmpty()) audioQueue.removeAt(0) else null
                }

                if (data != null) {
                    // Directly write PCM16 data - no conversion needed
                    audioTrack?.write(data, 0, data.size)
                } else {
                    delay(10)
                    // Check if queue is still empty
                    val isEmpty = synchronized(audioQueue) { audioQueue.isEmpty() }
                    if (isEmpty) {
                        delay(100)
                        val stillEmpty = synchronized(audioQueue) { audioQueue.isEmpty() }
                        if (stillEmpty) {
                            _isSpeaking.value = false
                            break
                        }
                    }
                }
            }
        }
    }

    private fun stopAudioPlayback() {
        audioPlaybackJob?.cancel()
        synchronized(audioQueue) {
            audioQueue.clear()
        }
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        _isSpeaking.value = false
    }

    private fun convertPcm24ToPcm16(pcm24Data: ByteArray): ByteArray {
        // PCM24 is 3 bytes per sample, PCM16 is 2 bytes per sample
        // We need to convert by taking the upper 16 bits of each 24-bit sample
        val sampleCount = pcm24Data.size / 3
        val pcm16Data = ByteArray(sampleCount * 2)
        val buffer = ByteBuffer.wrap(pcm24Data).order(ByteOrder.LITTLE_ENDIAN)
        val outBuffer = ByteBuffer.wrap(pcm16Data).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleCount) {
            val sample24 = buffer.get().toInt() and 0xFF or
                    ((buffer.get().toInt() and 0xFF) shl 8) or
                    ((buffer.get().toInt() and 0xFF) shl 16)

            // Sign extend if negative
            val signedSample = if (sample24 and 0x800000 != 0) {
                sample24 or 0xFF000000.toInt()
            } else {
                sample24
            }

            // Take upper 16 bits
            val sample16 = (signedSample shr 8).toShort()
            outBuffer.putShort(sample16)
        }

        return pcm16Data
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
