package com.turbometa.rayban.ui.screens

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.turbometa.rayban.utils.StreamQuality
import com.turbometa.rayban.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBackClick: () -> Unit,
    onNavigateToRecords: () -> Unit,
    onNavigateToQuickVisionMode: () -> Unit = {},
    onNavigateToLiveAIMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val alibabaApiKeyMasked by viewModel.alibabaApiKeyMasked.collectAsState()
    val openaiApiKeyMasked by viewModel.openaiApiKeyMasked.collectAsState()
    val customApiKeyMasked by viewModel.customApiKeyMasked.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val conversationCount by viewModel.conversationCount.collectAsState()
    val message by viewModel.message.collectAsState()
    val showProviderDialog by viewModel.showProviderDialog.collectAsState()
    val showApiKeyDialog by viewModel.showApiKeyDialog.collectAsState()
    val currentApiKeyProvider by viewModel.currentApiKeyProvider.collectAsState()
    val showModelDialog by viewModel.showModelDialog.collectAsState()
    val showLanguageDialog by viewModel.showLanguageDialog.collectAsState()
    val showQualityDialog by viewModel.showQualityDialog.collectAsState()
    val showDeleteConfirmDialog by viewModel.showDeleteConfirmDialog.collectAsState()
    val showVisionProviderDialog by viewModel.showVisionProviderDialog.collectAsState()
    val showEndpointDialog by viewModel.showEndpointDialog.collectAsState()
    val showLiveAIProviderDialog by viewModel.showLiveAIProviderDialog.collectAsState()
    val showAppLanguageDialog by viewModel.showAppLanguageDialog.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val editingKeyType by viewModel.editingKeyType.collectAsState()
    val showVisionModelDialog by viewModel.showVisionModelDialog.collectAsState()
    val selectedVisionModel by viewModel.selectedVisionModel.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    val modelsError by viewModel.modelsError.collectAsState()

    // Picovoice states
    var hasPicovoiceKey by remember { mutableStateOf(PorcupineWakeWordService.hasAccessKey(context)) }
    var showPicovoiceDialog by remember { mutableStateOf(false) }
    var isWakeWordEnabled by remember { mutableStateOf(isServiceRunning(context, PorcupineWakeWordService::class.java)) }
    var pendingWakeWordEnable by remember { mutableStateOf(false) }

    // Permission launcher for microphone
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, start the service
            val intent = Intent(context, PorcupineWakeWordService::class.java).apply {
                action = PorcupineWakeWordService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isWakeWordEnabled = true
            Toast.makeText(context, context.getString(R.string.picovoice_enabled), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.permission_microphone), Toast.LENGTH_LONG).show()
        }
        pendingWakeWordEnable = false
    }

    // Function to toggle wake word service
    fun toggleWakeWordService(enabled: Boolean) {
        if (enabled) {
            // Check if access key is configured
            if (!PorcupineWakeWordService.hasAccessKey(context)) {
                Toast.makeText(context, context.getString(R.string.picovoice_not_configured), Toast.LENGTH_SHORT).show()
                showPicovoiceDialog = true
                return
            }
            // Check microphone permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                pendingWakeWordEnable = true
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            // Start the service
            val intent = Intent(context, PorcupineWakeWordService::class.java).apply {
                action = PorcupineWakeWordService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isWakeWordEnabled = true
            Toast.makeText(context, context.getString(R.string.picovoice_enabled), Toast.LENGTH_SHORT).show()
        } else {
            // Stop the service
            val intent = Intent(context, PorcupineWakeWordService::class.java).apply {
                action = PorcupineWakeWordService.ACTION_STOP
            }
            context.startService(intent)
            isWakeWordEnabled = false
            Toast.makeText(context, context.getString(R.string.picovoice_disabled), Toast.LENGTH_SHORT).show()
        }
    }

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
                    icon = Icons.Default.RecordVoiceOver,
                    title = stringResource(R.string.settings_liveai_provider),
                    subtitle = if (liveAIProvider == LiveAIProvider.ALIBABA)
                        stringResource(R.string.liveai_alibaba)
                    else
                        stringResource(R.string.liveai_google),
                    onClick = { viewModel.showLiveAIProviderDialog() }
                )

                // Google API Key (only when Google selected for Live AI)
                if (liveAIProvider == LiveAIProvider.GOOGLE) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))
                    SettingsItem(
                        icon = Icons.Default.Key,
                        title = stringResource(R.string.apikey_google),
                        subtitle = if (hasGoogleKey)
                            stringResource(R.string.settings_apikey_configured)
                        else
                            stringResource(R.string.settings_apikey_not_configured),
                        subtitleColor = if (hasGoogleKey) Success else Error,
                        onClick = {
                            viewModel.showApiKeyDialogForType(SettingsViewModel.EditingKeyType.GOOGLE)
                        }
                    )
                }
            }

            // Quick Vision / Picovoice Section
            SettingsSection(title = stringResource(R.string.settings_quickvision)) {
                // Quick Vision Mode Settings
                SettingsItem(
                    icon = Icons.Default.Visibility,
                    title = stringResource(R.string.quickvision_mode_settings),
                    subtitle = stringResource(R.string.quickvision_mode_section),
                    onClick = onNavigateToQuickVisionMode
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                // Wake Word Toggle
                SettingsToggleItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = stringResource(R.string.wakeword_detection),
                    subtitle = if (isWakeWordEnabled)
                        stringResource(R.string.wakeword_enabled_desc)
                    else
                        stringResource(R.string.wakeword_disabled_desc),
                    checked = isWakeWordEnabled,
                    onCheckedChange = { toggleWakeWordService(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                SettingsItem(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.picovoice_accesskey),
                    subtitle = if (hasPicovoiceKey)
                        stringResource(R.string.picovoice_configured)
                    else
                        stringResource(R.string.picovoice_not_configured),
                    subtitleColor = if (hasPicovoiceKey) Success else Error,
                    onClick = { showPicovoiceDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                // Battery optimization settings
                SettingsItem(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(R.string.background_running),
                    subtitle = stringResource(R.string.background_running_desc),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                )
            }

            // AI Settings Section
            SettingsSection(title = stringResource(R.string.settings_ai)) {
                // App Language (界面语言)
                SettingsItem(
                    icon = Icons.Default.Translate,
                    title = stringResource(R.string.settings_applanguage),
                    subtitle = viewModel.getAppLanguageDisplayName(),
                    onClick = { viewModel.showAppLanguageDialog() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                // Output Language (AI输出语言)
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.output_language),
                    subtitle = viewModel.getSelectedLanguageDisplayName(),
                    onClick = { viewModel.showLanguageDialog() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                SettingsItem(
                    icon = Icons.Default.HighQuality,
                    title = stringResource(R.string.video_quality),
                    subtitle = stringResource(viewModel.getSelectedQuality().displayNameResId),
                    onClick = { viewModel.showQualityDialog() }
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
                    subtitle = "1.5.0",
                    onClick = {}
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                SettingsItem(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.github_project),
                    subtitle = "turbometa-rayban-ai",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Turbo1123/turbometa-rayban-ai"))
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                SettingsItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.download_latest),
                    subtitle = stringResource(R.string.download_latest_desc),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Turbo1123/turbometa-rayban-ai/releases"))
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.medium))

                SettingsItem(
                    icon = Icons.Default.Coffee,
                    title = stringResource(R.string.support_development),
                    subtitle = stringResource(R.string.buy_me_coffee),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/turbo1123"))
                        context.startActivity(intent)
                    }
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

    // Picovoice Dialog
    if (showPicovoiceDialog) {
        PicovoiceKeyDialog(
            currentKey = PorcupineWakeWordService.getAccessKey(context) ?: "",
            onSave = { key ->
                PorcupineWakeWordService.saveAccessKey(context, key)
                hasPicovoiceKey = PorcupineWakeWordService.hasAccessKey(context)
                true
            },
            onDismiss = { showPicovoiceDialog = false }
        )
    }

    // Vision Model Selection Dialog
    if (showVisionModelDialog) {
        VisionModelSelectionDialog(
            visionProvider = visionProvider,
            selectedModel = selectedVisionModel,
            alibabaModels = viewModel.getAlibabaVisionModels(),
            openRouterModels = openRouterModels,
            isLoading = isLoadingModels,
            error = modelsError,
            onSearch = { viewModel.searchOpenRouterModels(it) },
            onRefresh = { viewModel.fetchOpenRouterModels() },
            onSelect = { viewModel.selectVisionModel(it) },
            onDismiss = { viewModel.hideVisionModelDialog() }
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

    // App Language Selection Dialog
    if (showAppLanguageDialog) {
        AppLanguageSelectionDialog(
            selectedLanguage = appLanguage,
            languages = viewModel.getAvailableAppLanguages(),
            onSelect = { viewModel.selectAppLanguage(it) },
            onDismiss = { viewModel.hideAppLanguageDialog() }
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
    isDestructive: Boolean = false,
    subtitleColor: Color? = null
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
                color = subtitleColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    helpUrl: String?,
    onSave: (String) -> Boolean,
    onDelete: () -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    singleLine = true
                )

                helpUrl?.let { url ->
                    Spacer(modifier = Modifier.height(AppSpacing.small))
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get API Key")
                    }
                }

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
private fun PicovoiceKeyDialog(
    currentKey: String,
    onSave: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var accessKey by remember { mutableStateOf(currentKey) }
    var isVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.picovoice_accesskey), fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.picovoice_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(AppSpacing.medium))

                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it },
                    label = { Text(stringResource(R.string.picovoice_accesskey_hint)) },
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
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(AppSpacing.small))

                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.picovoice.ai/"))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.picovoice_get_key))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onSave(accessKey)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisionModelSelectionDialog(
    visionProvider: APIProvider,
    selectedModel: String,
    alibabaModels: List<AlibabaVisionModel>,
    openRouterModels: List<OpenRouterModel>,
    isLoading: Boolean,
    error: String?,
    onSearch: (String) -> List<OpenRouterModel>,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showVisionOnly by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.8f),
        title = {
            Text(text = stringResource(R.string.select_vision_model), fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (visionProvider == APIProvider.ALIBABA) {
                    // Alibaba models - static list
                    LazyColumn {
                        items(alibabaModels) { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onSelect(model.id) }
                                    )
                                    .padding(vertical = AppSpacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = model.id == selectedModel,
                                    onClick = { onSelect(model.id) }
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.small))
                                Column {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = model.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // OpenRouter models - with search
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.search_models)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    // Vision only toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = showVisionOnly,
                            onCheckedChange = { showVisionOnly = it }
                        )
                        Text(
                            text = stringResource(R.string.vision_capable_only),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        error != null -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = error,
                                    color = Error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.small))
                                TextButton(onClick = onRefresh) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                        else -> {
                            val filteredModels = remember(searchQuery, showVisionOnly, openRouterModels) {
                                val searched = if (searchQuery.isEmpty()) openRouterModels else onSearch(searchQuery)
                                if (showVisionOnly) searched.filter { it.isVisionCapable } else searched
                            }

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(filteredModels) { model ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onSelect(model.id) }
                                            )
                                            .padding(vertical = AppSpacing.small),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = model.id == selectedModel,
                                            onClick = { onSelect(model.id) }
                                        )
                                        Spacer(modifier = Modifier.width(AppSpacing.small))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = model.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1
                                                )
                                                if (model.isVisionCapable) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        Icons.Default.Visibility,
                                                        contentDescription = "Vision",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = Primary
                                                    )
                                                }
                                            }
                                            Text(
                                                text = model.id,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                maxLines = 1
                                            )
                                            if (model.priceDisplay.isNotEmpty()) {
                                                Text(
                                                    text = model.priceDisplay,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Success
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.select_language), fontWeight = FontWeight.SemiBold)
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
                        Column {
                            Text(text = language.nativeName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
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
private fun QualitySelectionDialog(
    selectedQuality: String,
    qualities: List<StreamQuality>,
    onSelect: (StreamQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.select_quality), fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                qualities.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(quality) }
                            )
                            .padding(vertical = AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = quality.id == selectedQuality,
                            onClick = { onSelect(quality) }
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.small))
                        Column {
                            Text(text = stringResource(quality.displayNameResId), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(quality.descriptionResId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
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
private fun AppLanguageSelectionDialog(
    selectedLanguage: AppLanguage,
    languages: List<AppLanguage>,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.select_applanguage), fontWeight = FontWeight.SemiBold)
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
                            selected = language == selectedLanguage,
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

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) Success else Primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(AppSpacing.medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Success,
                checkedTrackColor = Success.copy(alpha = 0.5f)
            )
        )
    }
}

// Helper function to check if a service is running
private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}
