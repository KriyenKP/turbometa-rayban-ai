package com.turbometa.rayban.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.turbometa.rayban.R
import com.turbometa.rayban.services.AIProviderConfig
import com.turbometa.rayban.ui.components.*
import com.turbometa.rayban.ui.theme.*
import com.turbometa.rayban.utils.AIModel
import com.turbometa.rayban.utils.AIProvider
import com.turbometa.rayban.utils.APIKeyManager
import com.turbometa.rayban.utils.OutputLanguage
import com.turbometa.rayban.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBackClick: () -> Unit,
    onNavigateToRecords: () -> Unit
) {
    val context = LocalContext.current
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val alibabaApiKeyMasked by viewModel.alibabaApiKeyMasked.collectAsState()
    val openaiApiKeyMasked by viewModel.openaiApiKeyMasked.collectAsState()
    val customApiKeyMasked by viewModel.customApiKeyMasked.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val conversationCount by viewModel.conversationCount.collectAsState()
    val message by viewModel.message.collectAsState()
    val showProviderDialog by viewModel.showProviderDialog.collectAsState()
    val showApiKeyDialog by viewModel.showApiKeyDialog.collectAsState()
    val currentApiKeyProvider by viewModel.currentApiKeyProvider.collectAsState()
    val showModelDialog by viewModel.showModelDialog.collectAsState()
    val showLanguageDialog by viewModel.showLanguageDialog.collectAsState()
    val showDeleteConfirmDialog by viewModel.showDeleteConfirmDialog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Message snackbar
            message?.let { msg ->
                SuccessMessage(
                    message = msg,
                    onDismiss = { viewModel.clearMessage() },
                    modifier = Modifier.padding(AppSpacing.medium)
                )
            }

            // API Configuration Section
            SettingsSection(title = stringResource(R.string.api_configuration)) {
                // AI Provider Selection
                SettingsItem(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.ai_provider),
                    subtitle = selectedProvider.getDisplayName(context),
                    onClick = { viewModel.showProviderDialog() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                // API Key for selected provider
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = when (selectedProvider) {
                        AIProvider.ALIBABA_CLOUD -> stringResource(R.string.alibaba_api_key)
                        AIProvider.OPENAI -> stringResource(R.string.openai_api_key)
                        AIProvider.CUSTOM -> stringResource(R.string.custom_api_key)
                    },
                    subtitle = when (selectedProvider) {
                        AIProvider.ALIBABA_CLOUD -> if (viewModel.hasApiKey(AIProvider.ALIBABA_CLOUD)) 
                            alibabaApiKeyMasked else stringResource(R.string.not_configured)
                        AIProvider.OPENAI -> if (viewModel.hasApiKey(AIProvider.OPENAI)) 
                            openaiApiKeyMasked else stringResource(R.string.not_configured)
                        AIProvider.CUSTOM -> if (viewModel.hasApiKey(AIProvider.CUSTOM)) 
                            customApiKeyMasked else stringResource(R.string.not_configured)
                    },
                    onClick = { viewModel.showApiKeyDialog(selectedProvider) }
                )

                // Show advanced settings for all providers
                if (selectedProvider == AIProvider.ALIBABA_CLOUD || 
                    selectedProvider == AIProvider.OPENAI || 
                    selectedProvider == AIProvider.CUSTOM) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                    SettingsItem(
                        icon = Icons.Default.Settings,
                        title = "Advanced Settings",
                        subtitle = when (selectedProvider) {
                            AIProvider.ALIBABA_CLOUD -> "Configure model and settings"
                            AIProvider.OPENAI -> "Configure models"
                            AIProvider.CUSTOM -> "Configure endpoints and models"
                        },
                        onClick = { viewModel.showAdvancedSettingsDialog(selectedProvider) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.output_language),
                    subtitle = viewModel.getSelectedLanguageDisplayName(),
                    onClick = { viewModel.showLanguageDialog() }
                )
            }

            // Data Section
            SettingsSection(title = stringResource(R.string.data)) {
                SettingsItem(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.conversation_records),
                    subtitle = "$conversationCount ${stringResource(R.string.records)}",
                    onClick = onNavigateToRecords
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.clear_all_records),
                    subtitle = stringResource(R.string.clear_records_desc),
                    onClick = { viewModel.showDeleteConfirmDialog() },
                    isDestructive = true
                )
            }

            // About Section
            SettingsSection(title = stringResource(R.string.about)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.version),
                    subtitle = "1.0.0",
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.large))
        }
    }

    // Provider Selection Dialog
    if (showProviderDialog) {
        ProviderSelectionDialog(
            selectedProvider = selectedProvider,
            providers = viewModel.getAvailableProviders(),
            onSelect = { viewModel.selectProvider(it) },
            onDismiss = { viewModel.hideProviderDialog() }
        )
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        currentApiKeyProvider?.let { provider ->
            ApiKeyDialog(
                provider = provider,
                currentKey = viewModel.getCurrentApiKey(provider),
                onSave = { viewModel.saveApiKey(it, provider) },
                onDelete = { viewModel.deleteApiKey(provider) },
                onDismiss = { viewModel.hideApiKeyDialog() }
            )
        }
    }

    // Model Selection Dialog
    if (showModelDialog) {
        ModelSelectionDialog(
            selectedModel = selectedModel,
            models = viewModel.getAvailableModels(),
            onSelect = { viewModel.selectModel(it) },
            onDismiss = { viewModel.hideModelDialog() }
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            selectedLanguage = selectedLanguage,
            languages = viewModel.getAvailableLanguages(),
            onSelect = { viewModel.selectLanguage(it) },
            onDismiss = { viewModel.hideLanguageDialog() }
        )
    }

    // Advanced Settings Dialog
    val showAdvancedSettingsDialog by viewModel.showAdvancedSettingsDialog.collectAsState()
    val currentAdvancedSettingsProvider by viewModel.currentAdvancedSettingsProvider.collectAsState()
    
    if (showAdvancedSettingsDialog) {
        currentAdvancedSettingsProvider?.let { provider ->
            AdvancedSettingsDialog(
                provider = provider,
                onDismiss = { viewModel.hideAdvancedSettingsDialog() },
                onSave = { config -> viewModel.saveAdvancedSettings(config) }
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        ConfirmDialog(
            title = stringResource(R.string.delete_all),
            message = stringResource(R.string.delete_confirm_message),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { viewModel.deleteAllConversations() },
            onDismiss = { viewModel.hideDeleteConfirmDialog() }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = AppSpacing.medium,
                vertical = AppSpacing.small
            )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.medium),
            shape = RoundedCornerShape(AppRadius.medium)
        ) {
            Column(content = content)
        }

        Spacer(modifier = Modifier.height(AppSpacing.medium))
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) Error else Primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(AppSpacing.medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) Error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun ProviderSelectionDialog(
    selectedProvider: AIProvider,
    providers: List<AIProvider>,
    onSelect: (AIProvider) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_provider),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                providers.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(provider) }
                            )
                            .padding(vertical = AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = provider == selectedProvider,
                            onClick = { onSelect(provider) }
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(
                            text = provider.getDisplayName(context),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ApiKeyDialog(
    provider: AIProvider,
    currentKey: String,
    onSave: (String) -> Boolean,
    onDelete: () -> Boolean,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf(currentKey) }
    var isVisible by remember { mutableStateOf(false) }

    val titleText = when (provider) {
        AIProvider.ALIBABA_CLOUD -> stringResource(R.string.alibaba_api_key)
        AIProvider.OPENAI -> stringResource(R.string.openai_api_key)
        AIProvider.CUSTOM -> stringResource(R.string.custom_api_key)
    }

    val hintText = when (provider) {
        AIProvider.ALIBABA_CLOUD -> stringResource(R.string.enter_alibaba_key)
        AIProvider.OPENAI -> stringResource(R.string.enter_openai_key)
        AIProvider.CUSTOM -> stringResource(R.string.enter_custom_key)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = titleText,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(hintText) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Icon(
                                imageVector = if (isVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    singleLine = true
                )

                if (currentKey.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AppSpacing.small))
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete_api_key))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onSave(apiKey)) {
                        onDismiss()
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ModelSelectionDialog(
    selectedModel: String,
    models: List<AIModel>,
    onSelect: (AIModel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_model),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(model) }
                            )
                            .padding(vertical = AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = model.id == selectedModel,
                            onClick = { onSelect(model) }
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LanguageSelectionDialog(
    selectedLanguage: String,
    languages: List<OutputLanguage>,
    onSelect: (OutputLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_language),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                languages.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(language) }
                            )
                            .padding(vertical = AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language.code == selectedLanguage,
                            onClick = { onSelect(language) }
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Text(
                            text = language.getDisplayName(context),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AdvancedSettingsDialog(
    provider: AIProvider,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val apiKeyManager = remember { APIKeyManager(context) }
    val providerConfig = remember { AIProviderConfig.getProviderConfig(context, provider) }
    
    // State for all form fields - initialize with empty strings
    var selectedAlibabaModel by remember { mutableStateOf("") }
    var realtimeModel by remember { mutableStateOf("") }
    var visionModel by remember { mutableStateOf("") }
    var restEndpoint by remember { mutableStateOf("") }
    var wsEndpoint by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf("") }
    
    // Initialize state from saved preferences in LaunchedEffect
    LaunchedEffect(provider) {
        when (provider) {
            AIProvider.ALIBABA_CLOUD -> {
                selectedAlibabaModel = apiKeyManager.getAIModel()
            }
            AIProvider.OPENAI -> {
                realtimeModel = apiKeyManager.getCustomRealtimeModel() 
                    ?: providerConfig.realtimeModel
                visionModel = apiKeyManager.getCustomVisionModel() 
                    ?: providerConfig.visionModel
                voice = apiKeyManager.getCustomVoice() 
                    ?: providerConfig.voice
            }
            AIProvider.CUSTOM -> {
                realtimeModel = apiKeyManager.getCustomRealtimeModel() ?: ""
                visionModel = apiKeyManager.getCustomVisionModel() ?: ""
                restEndpoint = apiKeyManager.getCustomRestEndpoint() ?: ""
                wsEndpoint = apiKeyManager.getCustomWsEndpoint() ?: ""
                voice = apiKeyManager.getCustomVoice() ?: ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (provider) {
                    AIProvider.ALIBABA_CLOUD -> "Alibaba Cloud Settings"
                    AIProvider.OPENAI -> "OpenAI Advanced Settings"
                    AIProvider.CUSTOM -> "Custom Provider Configuration"
                },
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // For Alibaba Cloud, show AI Model selector
                if (provider == AIProvider.ALIBABA_CLOUD) {
                    Text(
                        text = "AI Model",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = AppSpacing.medium)
                    ) {
                        AIModel.entries.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        selectedAlibabaModel = model.id
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = model.id == selectedAlibabaModel,
                                    onClick = {
                                        selectedAlibabaModel = model.id
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                
                // For Custom provider, show endpoint fields
                if (provider == AIProvider.CUSTOM) {
                    OutlinedTextField(
                        value = restEndpoint,
                        onValueChange = { restEndpoint = it },
                        label = { Text("REST API Endpoint") },
                        placeholder = { Text("https://api.example.com/v1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(AppSpacing.small))
                    
                    OutlinedTextField(
                        value = wsEndpoint,
                        onValueChange = { wsEndpoint = it },
                        label = { Text("WebSocket Endpoint") },
                        placeholder = { Text("wss://api.example.com/v1/realtime") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(AppSpacing.small))
                }
                
                // Model fields (editable for OpenAI and Custom, skip for Alibaba)
                if (provider != AIProvider.ALIBABA_CLOUD) {
                    OutlinedTextField(
                        value = realtimeModel,
                        onValueChange = { realtimeModel = it },
                        label = { Text("Realtime Model") },
                        placeholder = { 
                            Text(
                                if (provider == AIProvider.OPENAI) 
                                    "gpt-4o-realtime-preview-2024-12-17" 
                                else 
                                    "your-realtime-model"
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(AppSpacing.small))
                    
                    OutlinedTextField(
                        value = visionModel,
                        onValueChange = { visionModel = it },
                        label = { Text("Vision Model") },
                        placeholder = { 
                            Text(
                                if (provider == AIProvider.OPENAI) 
                                    "gpt-4-turbo" 
                                else 
                                    "your-vision-model"
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(AppSpacing.small))
                    
                    OutlinedTextField(
                        value = voice,
                        onValueChange = { voice = it },
                        label = { Text("Voice") },
                        placeholder = { 
                            Text(
                                if (provider == AIProvider.OPENAI) 
                                    "alloy" 
                                else 
                                    "default"
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                // Show hint for OpenAI
                if (provider == AIProvider.OPENAI) {
                    Spacer(modifier = Modifier.height(AppSpacing.small))
                    Text(
                        text = "Note: Endpoints are fixed for OpenAI. You can override model names if needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val config = mutableMapOf<String, String>()
                    
                    // Handle Alibaba Cloud model selection
                    if (provider == AIProvider.ALIBABA_CLOUD) {
                        config["alibabaModel"] = selectedAlibabaModel
                    } else {
                        // Handle OpenAI and Custom provider settings
                        config["realtimeModel"] = realtimeModel
                        config["visionModel"] = visionModel
                        config["voice"] = voice
                        if (provider == AIProvider.CUSTOM) {
                            config["restEndpoint"] = restEndpoint
                            config["wsEndpoint"] = wsEndpoint
                        }
                    }
                    
                    onSave(config)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
