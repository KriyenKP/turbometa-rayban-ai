package com.turbometa.rayban.utils

import android.content.Context
import android.util.Log
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

    companion object {
        private const val TAG = "APIKeyManager"
        private const val PREFS_NAME = "turbometa_secure_prefs"

        // Account names for different providers
        private const val KEY_ALIBABA_BEIJING = "alibaba-beijing-api-key"
        private const val KEY_ALIBABA_SINGAPORE = "alibaba-singapore-api-key"
        private const val KEY_OPENROUTER = "openrouter-api-key"
        private const val KEY_GOOGLE = "google-api-key"
        private const val KEY_LEGACY = "qwen_api_key" // For backward compatibility

        // Settings keys
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_OUTPUT_LANGUAGE = "output_language"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_RTMP_URL = "rtmp_url"

        @Volatile
        private var instance: APIKeyManager? = null

        fun getInstance(context: Context): APIKeyManager {
            return instance ?: synchronized(this) {
                instance ?: APIKeyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
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

    // MARK: - Migration

    private fun migrateLegacyKey() {
        try {
            // Migrate old qwen key to new Alibaba Beijing format
            val legacyKey = sharedPreferences.getString(KEY_LEGACY, null)
            if (!legacyKey.isNullOrBlank() && sharedPreferences.getString(KEY_ALIBABA_BEIJING, null).isNullOrBlank()) {
                sharedPreferences.edit()
                    .putString(KEY_ALIBABA_BEIJING, legacyKey)
                    .remove(KEY_LEGACY)
                    .apply()
                Log.i(TAG, "Migrated legacy qwen API key to Alibaba Beijing")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Migration error: ${e.message}")
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
            val accountKey = accountName(provider, endpoint)
            sharedPreferences.edit().putString(accountKey, key).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save API key: ${e.message}")
            false
        }
    }

    fun getAPIKey(provider: APIProvider, endpoint: AlibabaEndpoint? = null): String? {
        return try {
            val accountKey = accountName(provider, endpoint)
            sharedPreferences.getString(accountKey, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API key: ${e.message}")
            null
        }
    }

    fun deleteAPIKey(provider: APIProvider, endpoint: AlibabaEndpoint? = null): Boolean {
        return try {
            val accountKey = accountName(provider, endpoint)
            sharedPreferences.edit().remove(accountKey).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete API key: ${e.message}")
            false
        }
    }

    fun hasAPIKey(provider: APIProvider, endpoint: AlibabaEndpoint? = null): Boolean {
        return !getAPIKey(provider, endpoint).isNullOrBlank()
    }

    // MARK: - Google API Key (for Live AI)

    fun saveGoogleAPIKey(key: String): Boolean {
        return try {
            if (key.isBlank()) return false
            sharedPreferences.edit().putString(KEY_GOOGLE, key).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save Google API key: ${e.message}")
            false
        }
    }

    fun getGoogleAPIKey(): String? {
        return try {
            sharedPreferences.getString(KEY_GOOGLE, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Google API key: ${e.message}")
            null
        }
    }

    fun deleteGoogleAPIKey(): Boolean {
        return try {
            sharedPreferences.edit().remove(KEY_GOOGLE).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Google API key: ${e.message}")
            false
        }
    }

    fun hasGoogleAPIKey(): Boolean {
        return !getGoogleAPIKey().isNullOrBlank()
    }

    // MARK: - Backward Compatible Methods (defaults to current provider)

    fun saveAPIKey(key: String): Boolean {
        return saveAPIKey(key, APIProviderManager.staticCurrentProvider, APIProviderManager.staticAlibabaEndpoint)
    }

    fun getAPIKey(): String? {
        return getAPIKey(APIProviderManager.staticCurrentProvider, APIProviderManager.staticAlibabaEndpoint)
    }

    fun deleteAPIKey(): Boolean {
        return deleteAPIKey(APIProviderManager.staticCurrentProvider, APIProviderManager.staticAlibabaEndpoint)
    }

    fun hasAPIKey(): Boolean {
        return hasAPIKey(APIProviderManager.staticCurrentProvider, APIProviderManager.staticAlibabaEndpoint)
    }

    // MARK: - Private Helpers

    private fun accountName(provider: APIProvider, endpoint: AlibabaEndpoint?): String {
        return when (provider) {
            APIProvider.ALIBABA -> {
                val effectiveEndpoint = endpoint ?: APIProviderManager.staticAlibabaEndpoint
                when (effectiveEndpoint) {
                    AlibabaEndpoint.BEIJING -> KEY_ALIBABA_BEIJING
                    AlibabaEndpoint.SINGAPORE -> KEY_ALIBABA_SINGAPORE
                }
            }
            APIProvider.OPENROUTER -> KEY_OPENROUTER
        }
    }

    // MARK: - Settings (non-sensitive data)

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

    // Video Quality
    fun saveVideoQuality(quality: String) {
        sharedPreferences.edit().putString(KEY_VIDEO_QUALITY, quality).apply()
    }

    fun getVideoQuality(): String {
        return sharedPreferences.getString(KEY_VIDEO_QUALITY, "MEDIUM") ?: "MEDIUM"
    }

    // RTMP URL
    fun saveRtmpUrl(url: String) {
        sharedPreferences.edit().putString(KEY_RTMP_URL, url).apply()
    }

    fun getRtmpUrl(): String? {
        return sharedPreferences.getString(KEY_RTMP_URL, null)
    }
}

// Available AI models for Live AI
enum class AIModel(val id: String, val displayName: String) {
    // Alibaba Qwen Omni
    QWEN_FLASH_REALTIME("qwen3-omni-flash-realtime", "Qwen3 Omni Flash (Realtime)"),
    QWEN_STANDARD_REALTIME("qwen3-omni-standard-realtime", "Qwen3 Omni Standard (Realtime)"),
    // Google Gemini
    GEMINI_FLASH("gemini-2.0-flash-exp", "Gemini 2.0 Flash")
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
