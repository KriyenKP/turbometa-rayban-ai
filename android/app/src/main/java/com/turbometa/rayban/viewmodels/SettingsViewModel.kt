package com.turbometa.rayban.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turbometa.rayban.R
import com.turbometa.rayban.data.ConversationStorage
import com.turbometa.rayban.utils.AIModel
import com.turbometa.rayban.utils.AIProvider
import com.turbometa.rayban.utils.APIKeyManager
import com.turbometa.rayban.utils.OutputLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val apiKeyManager = APIKeyManager.getInstance(application)
    private val conversationStorage = ConversationStorage.getInstance(application)

    // AI Provider
    private val _selectedProvider = MutableStateFlow(apiKeyManager.getSelectedProvider())
    val selectedProvider: StateFlow<AIProvider> = _selectedProvider.asStateFlow()

    // API Keys (per provider)
    private val _alibabaApiKeyMasked = MutableStateFlow(getMaskedApiKey(AIProvider.ALIBABA_CLOUD))
    val alibabaApiKeyMasked: StateFlow<String> = _alibabaApiKeyMasked.asStateFlow()

    private val _openaiApiKeyMasked = MutableStateFlow(getMaskedApiKey(AIProvider.OPENAI))
    val openaiApiKeyMasked: StateFlow<String> = _openaiApiKeyMasked.asStateFlow()

    private val _customApiKeyMasked = MutableStateFlow(getMaskedApiKey(AIProvider.CUSTOM))
    val customApiKeyMasked: StateFlow<String> = _customApiKeyMasked.asStateFlow()

    // Legacy API Key (for backward compatibility)
    private val _hasApiKey = MutableStateFlow(apiKeyManager.hasAPIKey())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _apiKeyMasked = MutableStateFlow(getMaskedApiKey())
    val apiKeyMasked: StateFlow<String> = _apiKeyMasked.asStateFlow()

    // AI Model
    private val _selectedModel = MutableStateFlow(apiKeyManager.getAIModel())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Output Language
    private val _selectedLanguage = MutableStateFlow(apiKeyManager.getOutputLanguage())
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Conversation count
    private val _conversationCount = MutableStateFlow(conversationStorage.getConversationCount())
    val conversationCount: StateFlow<Int> = _conversationCount.asStateFlow()

    // Error/Success messages
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _showProviderDialog = MutableStateFlow(false)
    val showProviderDialog: StateFlow<Boolean> = _showProviderDialog.asStateFlow()

    private val _showApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = _showApiKeyDialog.asStateFlow()

    private val _currentApiKeyProvider = MutableStateFlow<AIProvider?>(null)
    val currentApiKeyProvider: StateFlow<AIProvider?> = _currentApiKeyProvider.asStateFlow()

    private val _showModelDialog = MutableStateFlow(false)
    val showModelDialog: StateFlow<Boolean> = _showModelDialog.asStateFlow()

    private val _showLanguageDialog = MutableStateFlow(false)
    val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _showAdvancedSettingsDialog = MutableStateFlow(false)
    val showAdvancedSettingsDialog: StateFlow<Boolean> = _showAdvancedSettingsDialog.asStateFlow()

    private val _currentAdvancedSettingsProvider = MutableStateFlow<AIProvider?>(null)
    val currentAdvancedSettingsProvider: StateFlow<AIProvider?> = _currentAdvancedSettingsProvider.asStateFlow()

    fun getAvailableModels(): List<AIModel> = AIModel.entries

    fun getAvailableLanguages(): List<OutputLanguage> = OutputLanguage.entries

    fun getAvailableProviders(): List<AIProvider> = AIProvider.entries

    private fun getMaskedApiKey(): String {
        val apiKey = apiKeyManager.getAPIKey() ?: return ""
        if (apiKey.length <= 8) return "****"
        return "${apiKey.take(4)}****${apiKey.takeLast(4)}"
    }

    private fun getMaskedApiKey(provider: AIProvider): String {
        val apiKey = apiKeyManager.getAPIKey(provider) ?: return ""
        if (apiKey.length <= 8) return "****"
        return "${apiKey.take(4)}****${apiKey.takeLast(4)}"
    }

    // Provider Management
    fun showProviderDialog() {
        _showProviderDialog.value = true
    }

    fun hideProviderDialog() {
        _showProviderDialog.value = false
    }

    fun selectProvider(provider: AIProvider) {
        apiKeyManager.setSelectedProvider(provider)
        _selectedProvider.value = provider
        _showProviderDialog.value = false
        _message.value = context.getString(R.string.provider_changed, provider.getDisplayName(context))
    }

    // API Key Management (multi-provider)
    fun showApiKeyDialog(provider: AIProvider) {
        _currentApiKeyProvider.value = provider
        _showApiKeyDialog.value = true
    }

    fun hideApiKeyDialog() {
        _showApiKeyDialog.value = false
        _currentApiKeyProvider.value = null
    }

    fun saveApiKey(apiKey: String, provider: AIProvider? = null): Boolean {
        val targetProvider = provider ?: _currentApiKeyProvider.value ?: return false
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            _message.value = context.getString(R.string.api_key_empty_error)
            return false
        }

        val success = apiKeyManager.saveAPIKey(targetProvider, trimmedKey)
        if (success) {
            // Update the appropriate masked key state
            when (targetProvider) {
                AIProvider.ALIBABA_CLOUD -> _alibabaApiKeyMasked.value = getMaskedApiKey(targetProvider)
                AIProvider.OPENAI -> _openaiApiKeyMasked.value = getMaskedApiKey(targetProvider)
                AIProvider.CUSTOM -> _customApiKeyMasked.value = getMaskedApiKey(targetProvider)
            }
            _message.value = context.getString(R.string.api_key_saved, targetProvider.getDisplayName(context))
            _showApiKeyDialog.value = false
        } else {
            _message.value = context.getString(R.string.api_key_save_failed)
        }
        return success
    }

    fun deleteApiKey(provider: AIProvider? = null): Boolean {
        val targetProvider = provider ?: _currentApiKeyProvider.value ?: return false
        val success = apiKeyManager.deleteAPIKey(targetProvider)
        if (success) {
            // Update the appropriate masked key state
            when (targetProvider) {
                AIProvider.ALIBABA_CLOUD -> _alibabaApiKeyMasked.value = ""
                AIProvider.OPENAI -> _openaiApiKeyMasked.value = ""
                AIProvider.CUSTOM -> _customApiKeyMasked.value = ""
            }
            _message.value = context.getString(R.string.api_key_deleted, targetProvider.getDisplayName(context))
        } else {
            _message.value = context.getString(R.string.api_key_delete_failed)
        }
        return success
    }

    fun getCurrentApiKey(provider: AIProvider? = null): String {
        val targetProvider = provider ?: _currentApiKeyProvider.value ?: return ""
        return apiKeyManager.getAPIKey(targetProvider) ?: ""
    }

    fun hasApiKey(provider: AIProvider): Boolean {
        return apiKeyManager.hasAPIKey(provider)
    }

    // Legacy API Key Management (for backward compatibility)

    // AI Model Management
    fun showModelDialog() {
        _showModelDialog.value = true
    }

    fun hideModelDialog() {
        _showModelDialog.value = false
    }

    fun selectModel(model: AIModel) {
        apiKeyManager.saveAIModel(model.id)
        _selectedModel.value = model.id
        _showModelDialog.value = false
        _message.value = context.getString(R.string.model_changed, model.displayName)
    }

    fun getSelectedModelDisplayName(): String {
        val modelId = _selectedModel.value
        return AIModel.entries.find { it.id == modelId }?.displayName ?: modelId
    }

    // Language Management
    fun showLanguageDialog() {
        _showLanguageDialog.value = true
    }

    fun hideLanguageDialog() {
        _showLanguageDialog.value = false
    }

    fun selectLanguage(language: OutputLanguage) {
        apiKeyManager.saveOutputLanguage(language.code)
        _selectedLanguage.value = language.code
        _showLanguageDialog.value = false
        _message.value = context.getString(R.string.language_changed, language.getDisplayName(context))
    }

    fun getSelectedLanguageDisplayName(): String {
        val langCode = _selectedLanguage.value
        return OutputLanguage.entries.find { it.code == langCode }?.getDisplayName(context) ?: langCode
    }

    // Conversation Management
    fun showDeleteConfirmDialog() {
        _showDeleteConfirmDialog.value = true
    }

    fun hideDeleteConfirmDialog() {
        _showDeleteConfirmDialog.value = false
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            val success = conversationStorage.deleteAllConversations()
            if (success) {
                _conversationCount.value = 0
                _message.value = context.getString(R.string.conversations_deleted)
            } else {
                _message.value = context.getString(R.string.conversations_delete_failed)
            }
            _showDeleteConfirmDialog.value = false
        }
    }

    fun refreshConversationCount() {
        _conversationCount.value = conversationStorage.getConversationCount()
    }

    // Advanced Settings Management
    fun showAdvancedSettingsDialog(provider: AIProvider) {
        _currentAdvancedSettingsProvider.value = provider
        _showAdvancedSettingsDialog.value = true
    }

    fun hideAdvancedSettingsDialog() {
        _showAdvancedSettingsDialog.value = false
        _currentAdvancedSettingsProvider.value = null
    }

    fun saveAdvancedSettings(config: Map<String, String>): Boolean {
        val provider = _currentAdvancedSettingsProvider.value ?: return false
        
        try {
            // Handle Alibaba Cloud model selection
            if (provider == AIProvider.ALIBABA_CLOUD) {
                config["alibabaModel"]?.let { modelId ->
                    apiKeyManager.saveAIModel(modelId)
                    _selectedModel.value = modelId
                }
            } else {
                // Save models and voice for OpenAI and Custom
                config["realtimeModel"]?.let { 
                    apiKeyManager.saveCustomRealtimeModel(it)
                }
                config["visionModel"]?.let { 
                    apiKeyManager.saveCustomVisionModel(it)
                }
                config["voice"]?.let { 
                    apiKeyManager.saveCustomVoice(it)
                }
                
                // Save endpoints for custom provider
                if (provider == AIProvider.CUSTOM) {
                    config["restEndpoint"]?.let { 
                        apiKeyManager.saveCustomRestEndpoint(it)
                    }
                    config["wsEndpoint"]?.let { 
                        apiKeyManager.saveCustomWsEndpoint(it)
                    }
                }
            }
            
            _message.value = context.getString(
                R.string.advanced_settings_saved,
                provider.getDisplayName(context)
            )
            _showAdvancedSettingsDialog.value = false
            return true
        } catch (e: Exception) {
            _message.value = context.getString(R.string.advanced_settings_save_failed)
            return false
        }
    }

    // Message handling
    fun clearMessage() {
        _message.value = null
    }
}
