package com.turbometa.rayban.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.turbometa.rayban.R

// AI Provider Enum (top-level, not nested)
enum class AIProvider(val id: String, val displayNameResId: Int) {
    ALIBABA_CLOUD("alibaba_cloud", R.string.provider_alibaba),
    OPENAI("openai", R.string.provider_openai),
    CUSTOM("custom", R.string.provider_custom);
    
    fun getDisplayName(context: Context): String {
        return context.getString(displayNameResId)
    }
}

class APIKeyManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "turbometa_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        // Multi-provider API key storage keys
        private const val KEY_ALIBABA_API_KEY = "alibaba_cloud_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        
        // Custom endpoint configuration keys
        private const val KEY_CUSTOM_REST_ENDPOINT = "custom_rest_endpoint"
        private const val KEY_CUSTOM_WS_ENDPOINT = "custom_ws_endpoint"
        private const val KEY_CUSTOM_VISION_MODEL = "custom_vision_model"
        private const val KEY_CUSTOM_REALTIME_MODEL = "custom_realtime_model"
        private const val KEY_CUSTOM_VOICE = "custom_voice"
        
        // Selected provider
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        
        // Legacy keys for backward compatibility
        private const val KEY_API_KEY = "qwen_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_OUTPUT_LANGUAGE = "output_language"

        @Volatile
        private var instance: APIKeyManager? = null

        fun getInstance(context: Context): APIKeyManager {
            return instance ?: synchronized(this) {
                instance ?: APIKeyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // MARK: - Multi-Provider API Key Management
    
    fun saveAPIKey(provider: AIProvider, key: String): Boolean {
        return try {
            if (key.isBlank()) return false
            val storageKey = when (provider) {
                AIProvider.ALIBABA_CLOUD -> KEY_ALIBABA_API_KEY
                AIProvider.OPENAI -> KEY_OPENAI_API_KEY
                AIProvider.CUSTOM -> KEY_CUSTOM_API_KEY
            }
            sharedPreferences.edit().putString(storageKey, key).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun getAPIKey(provider: AIProvider): String? {
        return try {
            val storageKey = when (provider) {
                AIProvider.ALIBABA_CLOUD -> KEY_ALIBABA_API_KEY
                AIProvider.OPENAI -> KEY_OPENAI_API_KEY
                AIProvider.CUSTOM -> KEY_CUSTOM_API_KEY
            }
            sharedPreferences.getString(storageKey, null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun hasAPIKey(provider: AIProvider): Boolean {
        return !getAPIKey(provider).isNullOrBlank()
    }
    
    fun deleteAPIKey(provider: AIProvider): Boolean {
        return try {
            val storageKey = when (provider) {
                AIProvider.ALIBABA_CLOUD -> KEY_ALIBABA_API_KEY
                AIProvider.OPENAI -> KEY_OPENAI_API_KEY
                AIProvider.CUSTOM -> KEY_CUSTOM_API_KEY
            }
            sharedPreferences.edit().remove(storageKey).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // MARK: - Selected Provider
    
    fun setSelectedProvider(provider: AIProvider) {
        sharedPreferences.edit().putString(KEY_SELECTED_PROVIDER, provider.id).apply()
    }
    
    fun getSelectedProvider(): AIProvider {
        val providerId = sharedPreferences.getString(KEY_SELECTED_PROVIDER, AIProvider.ALIBABA_CLOUD.id)
        return AIProvider.entries.find { it.id == providerId } ?: AIProvider.ALIBABA_CLOUD
    }
    
    // MARK: - Custom Endpoint Configuration
    
    fun saveCustomRestEndpoint(endpoint: String) {
        sharedPreferences.edit().putString(KEY_CUSTOM_REST_ENDPOINT, endpoint).apply()
    }
    
    fun getCustomRestEndpoint(): String {
        return sharedPreferences.getString(KEY_CUSTOM_REST_ENDPOINT, "https://api.openai.com/v1") 
            ?: "https://api.openai.com/v1"
    }
    
    fun saveCustomWsEndpoint(endpoint: String) {
        sharedPreferences.edit().putString(KEY_CUSTOM_WS_ENDPOINT, endpoint).apply()
    }
    
    fun getCustomWsEndpoint(): String {
        return sharedPreferences.getString(KEY_CUSTOM_WS_ENDPOINT, "wss://api.openai.com/v1/realtime") 
            ?: "wss://api.openai.com/v1/realtime"
    }
    
    fun saveCustomVisionModel(model: String) {
        sharedPreferences.edit().putString(KEY_CUSTOM_VISION_MODEL, model).apply()
    }
    
    fun getCustomVisionModel(): String {
        return sharedPreferences.getString(KEY_CUSTOM_VISION_MODEL, "gpt-4-turbo") 
            ?: "gpt-4-turbo"
    }
    
    fun saveCustomRealtimeModel(model: String) {
        sharedPreferences.edit().putString(KEY_CUSTOM_REALTIME_MODEL, model).apply()
    }
    
    fun getCustomRealtimeModel(): String {
        return sharedPreferences.getString(KEY_CUSTOM_REALTIME_MODEL, "gpt-4o-realtime-preview") 
            ?: "gpt-4o-realtime-preview"
    }
    
    fun saveCustomVoice(voice: String) {
        sharedPreferences.edit().putString(KEY_CUSTOM_VOICE, voice).apply()
    }
    
    fun getCustomVoice(): String {
        return sharedPreferences.getString(KEY_CUSTOM_VOICE, "alloy") 
            ?: "alloy"
    }

    // MARK: - Legacy Methods (for backward compatibility)

    fun saveAPIKey(key: String): Boolean {
        return try {
            if (key.isBlank()) return false
            sharedPreferences.edit().putString(KEY_API_KEY, key).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAPIKey(): String? {
        return try {
            sharedPreferences.getString(KEY_API_KEY, null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteAPIKey(): Boolean {
        return try {
            sharedPreferences.edit().remove(KEY_API_KEY).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hasAPIKey(): Boolean {
        return !getAPIKey().isNullOrBlank()
    }

    // AI Model
    fun saveAIModel(model: String) {
        sharedPreferences.edit().putString(KEY_AI_MODEL, model).apply()
    }

    fun getAIModel(): String {
        return sharedPreferences.getString(KEY_AI_MODEL, "qwen3-omni-flash-realtime") ?: "qwen3-omni-flash-realtime"
    }

    // Output Language
    fun saveOutputLanguage(language: String) {
        sharedPreferences.edit().putString(KEY_OUTPUT_LANGUAGE, language).apply()
    }

    fun getOutputLanguage(): String {
        return sharedPreferences.getString(KEY_OUTPUT_LANGUAGE, "zh-CN") ?: "zh-CN"
    }
}

// Available AI models
enum class AIModel(val id: String, val displayName: String) {
    FLASH_REALTIME("qwen3-omni-flash-realtime", "Qwen3 Omni Flash (Realtime)"),
    STANDARD_REALTIME("qwen3-omni-standard-realtime", "Qwen3 Omni Standard (Realtime)")
}

// Available output languages
enum class OutputLanguage(val code: String, val displayNameResId: Int) {
    CHINESE("zh-CN", R.string.language_chinese),
    ENGLISH("en-US", R.string.language_english),
    JAPANESE("ja-JP", R.string.language_japanese),
    KOREAN("ko-KR", R.string.language_korean),
    SPANISH("es-ES", R.string.language_spanish),
    FRENCH("fr-FR", R.string.language_french);
    
    fun getDisplayName(context: Context): String {
        return context.getString(displayNameResId)
    }
}
