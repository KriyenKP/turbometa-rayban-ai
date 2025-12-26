package com.turbometa.rayban.services

import android.graphics.Bitmap
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class VisionAPIService(private val apiKey: String) {

    companion object {
        private const val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        private const val MODEL = "qwen-vl-plus"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun analyzeImage(image: Bitmap, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64Image = encodeImageToBase64(image)
            val requestBody = buildRequestBody(base64Image, prompt)

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API Error: ${response.code} - $responseBody"))
            }

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response from API"))
            }

            val result = parseResponse(responseBody)
            if (result.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Failed to parse response"))
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun buildRequestBody(base64Image: String, prompt: String): String {
        val messages = listOf(
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:image/jpeg;base64,$base64Image"
                        )
                    ),
                    mapOf(
                        "type" to "text",
                        "text" to prompt
                    )
                )
            )
        )

        val request = mapOf(
            "model" to MODEL,
            "messages" to messages,
            "max_tokens" to 2000
        )

        return gson.toJson(request)
    }

    private fun parseResponse(responseBody: String): String? {
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                message?.get("content")?.asString
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

sealed class VisionAPIError : Exception() {
    object InvalidImage : VisionAPIError()
    object EmptyResponse : VisionAPIError()
    object InvalidResponse : VisionAPIError()
    data class APIError(override val message: String) : VisionAPIError()
}
