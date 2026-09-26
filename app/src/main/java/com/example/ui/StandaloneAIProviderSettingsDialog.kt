package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.AIFactory
import com.example.ai.AIModelRegistry
import com.example.ai.APIKeyManager
import com.example.data.AIProviderConfigRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneAIProviderSettingsDialog(
    apiKeyManager: APIKeyManager,
    aiFactory: AIFactory,
    configRepository: AIProviderConfigRepository,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val providers = listOf("Gemini", "OpenAI", "Anthropic", "Mock")
    var selectedTab by remember { mutableStateOf("Gemini") }

    // State for each provider
    var geminiKey by remember { mutableStateOf(apiKeyManager.getApiKey("Gemini") ?: "") }
    var geminiModel by remember { mutableStateOf(AIModelRegistry.getDefaultModel("Gemini")) }

    var openaiKey by remember { mutableStateOf(apiKeyManager.getApiKey("OpenAI") ?: "") }
    var openaiModel by remember { mutableStateOf(AIModelRegistry.getDefaultModel("OpenAI")) }

    var anthropicKey by remember { mutableStateOf(apiKeyManager.getApiKey("Anthropic") ?: "") }
    var anthropicModel by remember { mutableStateOf(AIModelRegistry.getDefaultModel("Anthropic")) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Password visibility
    var showGeminiKey by remember { mutableStateOf(false) }
    var showOpenAIKey by remember { mutableStateOf(false) }
    var showAnthropicKey by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val gCfg = configRepository.getConfigByProviderType("Gemini")
        if (gCfg != null && gCfg.selectedModel.isNotBlank()) {
            val resolved = AIModelRegistry.resolveModel("Gemini", gCfg.selectedModel)
            geminiModel = resolved
            if (resolved != gCfg.selectedModel) configRepository.updateModelForProvider("Gemini", resolved)
        }

        val oCfg = configRepository.getConfigByProviderType("OpenAI")
        if (oCfg != null && oCfg.selectedModel.isNotBlank()) {
            val resolved = AIModelRegistry.resolveModel("OpenAI", oCfg.selectedModel)
            openaiModel = resolved
            if (resolved != oCfg.selectedModel) configRepository.updateModelForProvider("OpenAI", resolved)
        }

        val aCfg = configRepository.getConfigByProviderType("Anthropic")
        if (aCfg != null && aCfg.selectedModel.isNotBlank()) {
            val resolved = AIModelRegistry.resolveModel("Anthropic", aCfg.selectedModel)
            anthropicModel = resolved
            if (resolved != aCfg.selectedModel) configRepository.updateModelForProvider("Anthropic", resolved)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI Provider Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Provider Tabs
                PrimaryTabRow(
                    selectedTabIndex = providers.indexOf(selectedTab).coerceAtLeast(0)
                ) {
                    providers.forEach { provider ->
                        Tab(
                            selected = selectedTab == provider,
                            onClick = {
                                selectedTab = provider
                                testResult = null
                                saveSuccessMessage = null
                            },
                            text = { Text(provider, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Provider content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val currentKey = when (selectedTab) {
                        "Gemini" -> geminiKey
                        "OpenAI" -> openaiKey
                        "Anthropic" -> anthropicKey
                        else -> "mock"
                    }
                    val currentModel = when (selectedTab) {
                        "Gemini" -> geminiModel
                        "OpenAI" -> openaiModel
                        "Anthropic" -> anthropicModel
                        else -> "mock-default"
                    }

                    if (selectedTab != "Mock") {
                        // API Key Field
                        val isVisible = when (selectedTab) {
                            "Gemini" -> showGeminiKey
                            "OpenAI" -> showOpenAIKey
                            else -> showAnthropicKey
                        }

                        OutlinedTextField(
                            value = currentKey,
                            onValueChange = { newVal ->
                                when (selectedTab) {
                                    "Gemini" -> geminiKey = newVal
                                    "OpenAI" -> openaiKey = newVal
                                    "Anthropic" -> anthropicKey = newVal
                                }
                                testResult = null
                                saveSuccessMessage = null
                            },
                            label = { Text("$selectedTab API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        when (selectedTab) {
                                            "Gemini" -> showGeminiKey = !showGeminiKey
                                            "OpenAI" -> showOpenAIKey = !showOpenAIKey
                                            "Anthropic" -> showAnthropicKey = !showAnthropicKey
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isVisible) "Hide Key" else "Show Key"
                                    )
                                }
                            }
                        )

                        // Model Selector Dropdown
                        val discoveryState by aiFactory.modelSelectionRepository.getModelsState(selectedTab).collectAsState()
                        val discoveredModels = discoveryState.models
                        val availableModelIds = if (discoveredModels.isNotEmpty()) discoveredModels.map { it.id } else AIModelRegistry.getAvailableModels(selectedTab)

                        LaunchedEffect(selectedTab, currentKey) {
                            if (currentKey.isNotBlank() && discoveryState.lastRefreshedTimestamp == null && !discoveryState.isLoading) {
                                aiFactory.modelSelectionRepository.refreshModels(selectedTab, currentKey)
                            }
                        }

                        var modelDropdownExpanded by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Selected Model", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                if (discoveryState.isFromCache) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            "CACHED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        aiFactory.modelSelectionRepository.refreshModels(selectedTab, currentKey)
                                    }
                                },
                                enabled = !discoveryState.isLoading && currentKey.isNotBlank(),
                                modifier = Modifier.size(28.dp)
                            ) {
                                if (discoveryState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh models", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (discoveryState.errorMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = discoveryState.errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = modelDropdownExpanded,
                            onExpandedChange = { modelDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = currentModel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model ID") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                if (discoveredModels.isNotEmpty()) {
                                    discoveredModels.forEach { modelOption ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(modelOption.displayName, fontWeight = FontWeight.SemiBold)
                                                        if (modelOption.isImageGeneration) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                                            ) {
                                                                Text(
                                                                    "IMAGE",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(modelOption.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    if (!modelOption.description.isNullOrBlank()) {
                                                        Text(
                                                            modelOption.description,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                when (selectedTab) {
                                                    "Gemini" -> geminiModel = modelOption.id
                                                    "OpenAI" -> openaiModel = modelOption.id
                                                    "Anthropic" -> anthropicModel = modelOption.id
                                                }
                                                modelDropdownExpanded = false
                                            }
                                        )
                                    }
                                } else {
                                    availableModelIds.forEach { modelOption ->
                                        DropdownMenuItem(
                                            text = { Text(modelOption) },
                                            onClick = {
                                                when (selectedTab) {
                                                    "Gemini" -> geminiModel = modelOption
                                                    "OpenAI" -> openaiModel = modelOption
                                                    "Anthropic" -> anthropicModel = modelOption
                                                }
                                                modelDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Test Connection Button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isTesting = true
                                    testResult = null
                                    val res = aiFactory.testConnection(selectedTab, currentKey, currentModel)
                                    testResult = if (res.isSuccess) {
                                        res.getOrNull()
                                    } else {
                                        "Error: ${res.exceptionOrNull()?.message}"
                                    }
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting && currentKey.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verifying API...")
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Connection")
                            }
                        }
                    } else {
                        // Mock Provider Info
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Mock AI Provider",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Requires no API key. Generates deterministic code change proposals and responses offline.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (testResult != null) {
                        val isOk = !testResult!!.startsWith("Error")
                        Text(
                            text = testResult!!,
                            color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (saveSuccessMessage != null) {
                        Text(
                            text = saveSuccessMessage!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Footer Save & Close buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val keyToSave = when (selectedTab) {
                                    "Gemini" -> geminiKey
                                    "OpenAI" -> openaiKey
                                    "Anthropic" -> anthropicKey
                                    else -> ""
                                }
                                val rawModel = when (selectedTab) {
                                    "Gemini" -> geminiModel
                                    "OpenAI" -> openaiModel
                                    "Anthropic" -> anthropicModel
                                    else -> "mock-default"
                                }
                                val modelToSave = AIModelRegistry.resolveModel(selectedTab, rawModel)
                                if (selectedTab != "Mock") {
                                    apiKeyManager.saveApiKey(selectedTab, keyToSave.trim())
                                    configRepository.updateModelForProvider(selectedTab, modelToSave.trim())
                                }
                                configRepository.setActiveProviderType(selectedTab)
                                saveSuccessMessage = "$selectedTab settings saved successfully!"
                            }
                        }
                    ) {
                        Text("Save & Activate")
                    }
                }
            }
        }
    }
}
