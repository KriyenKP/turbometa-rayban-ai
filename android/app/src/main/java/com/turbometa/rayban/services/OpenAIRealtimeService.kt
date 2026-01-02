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
import com.turbometa.rayban.managers.LiveAIModeManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI Realtime WebSocket Service Implements WebSocket-based real-time audio chat with Vision API
 * for image analysis Realtime API: https://platform.openai.com/docs/guides/realtime Vision API:
 * https://platform.openai.com/docs/guides/vision
 */
class OpenAIRealtimeService(
        private val apiKey: String,
        private val model: String = "gpt-4o-realtime-preview",
        private val outputLanguage: String = "en-US",
        private val context: Context? = null
) {
  companion object {
    private const val TAG = "OpenAIRealtimeService"
    private const val WS_BASE_URL = "wss://api.openai.com/v1/realtime"
    private const val VISION_API_URL = "https://api.openai.com/v1/chat/completions"
    private const val SAMPLE_RATE = 24000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
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
  var onConnected: (() -> Unit)? = null

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
  private var lastImageSentTime = 0L
  private val imageSendIntervalMs = 500L // send image every 500ms while recording

  private val client =
          OkHttpClient.Builder()
                  .readTimeout(0, TimeUnit.MILLISECONDS)
                  .pingInterval(30, TimeUnit.SECONDS)
                  .build()

  fun connect() {
    if (_isConnected.value) return

    if (!scope.isActive) {
      scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    val url = "$WS_BASE_URL?model=$model"
    Log.d(TAG, "Connecting to OpenAI Realtime: $url")

    val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $apiKey").build()

    webSocket =
            client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                      override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket connected")
                        _isConnected.value = true
                        // Log handshake details (status + a few headers)
                        val code = response.code
                        val msg = response.message
                        Log.d(TAG, "WebSocket opened: $code $msg")
                        sendSessionUpdate()
                        onConnected?.invoke()
                      }

                      override fun onMessage(webSocket: WebSocket, text: String) {
                        handleMessage(text)
                      }

                      override fun onFailure(
                              webSocket: WebSocket,
                              t: Throwable,
                              response: Response?
                      ) {
                        Log.e(TAG, "WebSocket error: ${t.message}")
                        _isConnected.value = false
                        _errorMessage.value = t.message
                        onError?.invoke(t.message ?: "Connection failed")
                      }

                      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closing: code=$code reason=$reason")
                      }

                      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $reason")
                        _isConnected.value = false
                      }
                    }
            )
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

  private fun sendSessionUpdate() {
    val instructions =
            context?.let {
              val modeManager = LiveAIModeManager.getInstance(it)
              modeManager.getSystemPrompt()
            }
                    ?: getLiveAIPrompt(outputLanguage)

    // Per OpenAI Realtime API documentation example
    // https://platform.openai.com/docs/guides/realtime-conversations
    // Note: Vision handled separately via Vision API, descriptions injected as text
    val sessionConfig =
            mapOf(
                    "type" to "session.update",
                    "session" to
                            mapOf(
                                    "type" to "realtime",
                                    "model" to model,
                                    "output_modalities" to listOf("audio"),
                                    "audio" to
                                            mapOf(
                                                    "input" to
                                                            mapOf(
                                                                    "format" to
                                                                            mapOf(
                                                                                    "type" to
                                                                                            "audio/pcm",
                                                                                    "rate" to
                                                                                            SAMPLE_RATE
                                                                            ),
                                                                    "turn_detection" to
                                                                            mapOf(
                                                                                    "type" to
                                                                                            "server_vad"
                                                                            ),
                                                                    "transcription" to
                                                                            mapOf(
                                                                                    "model" to
                                                                                            "whisper-1"
                                                                            )
                                                            ),
                                                    "output" to
                                                            mapOf(
                                                                    "format" to
                                                                            mapOf(
                                                                                    "type" to
                                                                                            "audio/pcm",
                                                                                    "rate" to
                                                                                            SAMPLE_RATE
                                                                            ),
                                                                    "voice" to "alloy"
                                                            )
                                            ),
                                    "instructions" to instructions
                            )
            )
    val json = gson.toJson(sessionConfig)
    Log.d(TAG, "Sending session update: model=$model, modalities=[audio], vad=server_vad")
    webSocket?.send(json)
  }

  private fun getLiveAIPrompt(language: String): String {
    return when (language) {
      "zh-CN" ->
              """
                你是RayBan Meta智能眼镜AI助手。

                【重要】必须始终用中文回答，无论用户说什么语言。

                回答要简练、口语化，像朋友聊天一样。用户戴着眼镜可以看到周围环境。如果用户问"你看到什么"、"这是什么"、"描述一下"或类似问题，请回复"[REQUEST_VISION]"来获取视觉信息，然后基于看到的内容回答。不要啰嗦，直接说重点。
            """.trimIndent()
      "en-US" ->
              """
                You are a RayBan Meta smart glasses AI assistant.

                [IMPORTANT] Always respond in English.

                Keep your answers concise and conversational, like chatting with a friend. The user is wearing glasses and can see their surroundings. When the user asks vision-related questions, you will receive visual context to help answer. Be direct and to the point.
            """.trimIndent()
      "ja-JP" ->
              """
                あなたはRayBan Metaスマートグラスのアシスタントです。

                【重要】常に日本語で回答してください。

                回答は簡潔で会話的に、友達とチャットするように。ユーザーは眼鏡をかけて周囲を見ています。「何が見える」「これは何」「説明して」などの視覚に関する質問があれば、「[REQUEST_VISION]」と応答して視覚情報を受け取り、見えるものに基づいて答えてください。要点を直接伝えてください。
            """.trimIndent()
      "ko-KR" ->
              """
                당신은 RayBan Meta 스마트 안경 AI 어시스턴트입니다.

                【중요】항상 한국어로 응답하세요.

                친구와 대화하듯이 간결하고 대화적으로 답변하세요. 사용자는 안경을 착용하고 주변을 볼 수 있습니다. "뭐가 보여", "이게 뭐야", "설명해줘" 같은 시각 관련 질문이 있으면 "[REQUEST_VISION]"으로 응답하여 시각 정보를 받고, 보이는 것을 바탕으로 답변하세요. 요점만 말하세요.
            """.trimIndent()
      else -> getLiveAIPrompt("en-US")
    }
  }

  // Audio recording
  fun startRecording() {
    if (_isRecording.value) return
    try {
      val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
      audioRecord =
              AudioRecord(
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
      recordingJob =
              scope.launch {
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
      _errorMessage.value = "Microphone permission denied"
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

  /**
   * Manually trigger vision analysis with current frame Call this when user explicitly asks about
   * what they see
   */
  fun requestVisionAnalysis() {
    pendingImageFrame?.let {
      try {
        Log.d(TAG, "Manual vision analysis requested")
        sendImageInput(it)
      } catch (e: Exception) {
        Log.e(TAG, "Error in manual vision request: ${e.message}")
      }
    }
            ?: Log.w(TAG, "No video frame available for vision analysis")
  }

  private fun sendAudioData(audioData: ByteArray) {
    if (!_isConnected.value) return
    val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
    val message = mapOf("type" to "input_audio_buffer.append", "audio" to base64Audio)
    val json = gson.toJson(message)
    webSocket?.send(json)
  }

  fun sendImageInput(bitmap: Bitmap) {
    // Use Vision API to analyze the image, then inject description as text into Realtime
    // conversation
    scope.launch {
      try {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val bytes = outputStream.toByteArray()
        val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

        // Call Vision API to get image description
        val visionRequest =
                """
                {
                    "model": "gpt-4o",
                    "messages": [
                        {
                            "role": "user",
                            "content": [
                                {
                                    "type": "text",
                                    "text": "Describe what you see in this image concisely in 1-2 sentences."
                                },
                                {
                                    "type": "image_url",
                                    "image_url": {
                                        "url": "data:image/jpeg;base64,$base64Image",
                                        "detail": "low"
                                    }
                                }
                            ]
                        }
                    ],
                    "max_tokens": 100
                }
                """.trimIndent()

        val requestBody = visionRequest.toRequestBody("application/json".toMediaType())
        val request =
                Request.Builder()
                        .url(VISION_API_URL)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build()

        Log.d(TAG, "Sending vision API request, ${bytes.size} bytes")

        client.newCall(request)
                .enqueue(
                        object : Callback {
                          override fun onFailure(call: Call, e: java.io.IOException) {
                            Log.e(TAG, "Vision API call failed: ${e.message}")
                          }

                          override fun onResponse(call: Call, response: Response) {
                            response.use {
                              if (!it.isSuccessful) {
                                Log.e(TAG, "Vision API error: ${it.code} ${it.body?.string()}")
                                return
                              }

                              val responseBody = it.body?.string() ?: return
                              val json = gson.fromJson(responseBody, JsonObject::class.java)
                              val description =
                                      json.getAsJsonArray("choices")
                                              ?.get(0)
                                              ?.asJsonObject
                                              ?.getAsJsonObject("message")
                                              ?.get("content")
                                              ?.asString
                                              ?: "Unable to analyze image"

                              Log.d(TAG, "Vision description: $description")

                              // Inject vision description as text into Realtime conversation
                              val textMessage =
                                      mapOf(
                                              "type" to "conversation.item.create",
                                              "item" to
                                                      mapOf(
                                                              "type" to "message",
                                                              "role" to "user",
                                                              "content" to
                                                                      listOf(
                                                                              mapOf(
                                                                                      "type" to
                                                                                              "input_text",
                                                                                      "text" to
                                                                                              "[Visual context: $description]"
                                                                              )
                                                                      )
                                                      )
                                      )
                              val messageJson = gson.toJson(textMessage)
                              webSocket?.send(messageJson)
                              Log.d(TAG, "Visual context injected into conversation")
                            }
                          }
                        }
                )
      } catch (e: Exception) {
        Log.e(TAG, "Error processing image: ${e.message}")
      }
    }
  }

  private fun handleMessage(text: String) {
    try {
      val json = gson.fromJson(text, JsonObject::class.java)
      val type = json.get("type")?.asString ?: return
      when (type) {
        // OpenAI input lifecycle
        "input_audio_buffer.committed" -> {
          Log.d(TAG, "Input committed")
        }
        "session.created", "session.updated" -> {
          Log.d(TAG, "Session ready")
        }
        "response.created" -> {
          Log.d(TAG, "Response created")
        }
        "response.output_item.added" -> {
          Log.d(TAG, "Output item added")
        }
        "response.output_item.done" -> {
          Log.d(TAG, "Output item done")
        }
        "response.content_part.added" -> {
          Log.d(TAG, "Content part added")
        }
        "response.content_part.done" -> {
          Log.d(TAG, "Content part done")
        }
        "input_audio_buffer.speech_started" -> {
          _isSpeaking.value = false
          stopAudioPlayback()
          onSpeechStarted?.invoke()
          Log.d(TAG, "Speech started")
        }
        "input_audio_buffer.speech_stopped" -> {
          onSpeechStopped?.invoke()
          Log.d(TAG, "Speech stopped")
        }
        // OpenAI transcript streaming (assistant output)
        "response.output_audio_transcript.delta" -> {
          val delta = json.get("delta")?.asString ?: ""
          _currentTranscript.value += delta
          onTranscriptDelta?.invoke(delta)
        }
        "response.output_audio_transcript.done" -> {
          val transcriptField = json.get("transcript")?.asString
          val transcript = _currentTranscript.value
          val finalText = transcriptField ?: transcript
          onTranscriptDone?.invoke(finalText)
          _currentTranscript.value = ""
          Log.d(TAG, "Transcript done: ${finalText.take(50)}")
        }
        "conversation.item.input_audio_transcription.completed" -> {
          val transcript = json.get("transcript")?.asString ?: ""
          onUserTranscript?.invoke(transcript)
          Log.d(TAG, "User transcript: $transcript")

          // Detect vision-related questions and send image immediately
          val visionKeywords =
                  listOf(
                          "what do you see",
                          "what am i looking at",
                          "what is this",
                          "what's this",
                          "describe this",
                          "describe what you see",
                          "tell me what you see",
                          "what can you see",
                          "look at this",
                          "what am i seeing",
                          "can you see",
                          "do you see",
                          "看到什么",
                          "这是什么",
                          "描述一下"
                  )

          val transcriptLower = transcript.lowercase()
          if (visionKeywords.any { transcriptLower.contains(it) }) {
            Log.d(TAG, "Vision question detected, sending current frame")
            pendingImageFrame?.let {
              try {
                sendImageInput(it)
              } catch (e: Exception) {
                Log.e(TAG, "Error sending vision frame: ${e.message}")
              }
            }
          }
        }
        // OpenAI audio output streaming
        "response.output_audio.delta" -> {
          val audioData = json.get("delta")?.asString ?: return
          val audioBytes = Base64.decode(audioData, Base64.DEFAULT)
          playAudio(audioBytes)
        }
        "response.output_audio.done" -> {
          _isSpeaking.value = false
          Log.d(TAG, "Audio response done")
        }
        "error" -> {
          val errorMsg = json.get("error")?.asJsonObject?.get("message")?.asString
          Log.e(TAG, "Server error: $errorMsg")
          _errorMessage.value = errorMsg
          onError?.invoke(errorMsg ?: "Unknown error")
        }
        else -> {
          Log.d(TAG, "Unhandled message type: $type")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling message: ${e.message}")
    }
  }

  private fun playAudio(audioData: ByteArray) {
    synchronized(audioQueue) { audioQueue.add(audioData) }
    if (audioPlaybackJob?.isActive != true) {
      startAudioPlayback()
    }
  }

  private fun startAudioPlayback() {
    if (audioTrack == null) {
      val bufferSize =
              AudioTrack.getMinBufferSize(
                      SAMPLE_RATE,
                      AudioFormat.CHANNEL_OUT_MONO,
                      AudioFormat.ENCODING_PCM_16BIT
              )
      val audioAttributes =
              AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_MEDIA)
                      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                      .build()
      val audioFormat =
              AudioFormat.Builder()
                      .setSampleRate(SAMPLE_RATE)
                      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                      .build()
      audioTrack =
              AudioTrack(
                      audioAttributes,
                      audioFormat,
                      bufferSize * 2,
                      AudioTrack.MODE_STREAM,
                      AudioManager.AUDIO_SESSION_ID_GENERATE
              )
      audioTrack?.play()
    }
    _isSpeaking.value = true
    audioPlaybackJob =
            scope.launch {
              while (isActive) {
                val data =
                        synchronized(audioQueue) {
                          if (audioQueue.isNotEmpty()) audioQueue.removeAt(0) else null
                        }
                if (data != null) {
                  audioTrack?.write(data, 0, data.size)
                } else {
                  delay(10)
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
    synchronized(audioQueue) { audioQueue.clear() }
    audioTrack?.stop()
    audioTrack?.release()
    audioTrack = null
    _isSpeaking.value = false
  }

  fun clearError() {
    _errorMessage.value = null
  }
}
