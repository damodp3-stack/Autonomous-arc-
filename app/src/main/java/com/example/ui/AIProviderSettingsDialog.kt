package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AIModelRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIProviderSettingsDialog(
    viewModel: WorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val connectionTestResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()

    val providers = listOf("Gemini", "OpenAI", "Anthropic")
    var selectedTab by remember { mutableStateOf(selectedProvider) }

    // State for each provider
    var geminiKey by remember { mutableStateOf(viewModel.apiKeyManager.getApiKey("Gemini") ?: "") }
    var geminiModel by remember {
        mutableStateOf(
            if (selectedProvider.equals("Gemini", ignoreCase = true)) {
                AIModelRegistry.resolveModel("Gemini", viewModel.selectedModel.value)
            } else {
                AIModelRegistry.getDefaultModel("Gemini")
            }
        )
    }

    var openaiKey by remember { mutableStateOf(viewModel.apiKeyManager.getApiKey("OpenAI") ?: "") }
    var openaiModel by remember {
        mutableStateOf(
            if (selectedProvider.equals("OpenAI", ignoreCase = true)) {
                AIModelRegistry.resolveModel("OpenAI", viewModel.selectedModel.value)
            } else {
                AIModelRegistry.getDefaultModel("OpenAI")
            }
        )
    }

    var anthropicKey by remember { mutableStateOf(viewModel.apiKeyManager.getApiKey("Anthropic") ?: "") }
    var anthropicModel by remember {
        mutableStateOf(
            if (selectedProvider.equals("Anthropic", ignoreCase = true)) {
                AIModelRegistry.resolveModel("Anthropic", viewModel.selectedModel.value)
            } else {
                AIModelRegistry.getDefaultModel("Anthropic")
            }
        )
    }

    var showKeyPassword by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val currentKey = when (selectedTab) {
        "OpenAI" -> openaiKey
        "Anthropic" -> anthropicKey
        else -> geminiKey
    }

    val currentModel = when (selectedTab) {
        "OpenAI" -> openaiModel
        "Anthropic" -> anthropicModel
        else -> geminiModel
    }

    val onKeyChange: (String) -> Unit = { newKey ->
        when (selectedTab) {
            "OpenAI" -> openaiKey = newKey
            "Anthropic" -> anthropicKey = newKey
            else -> geminiKey = newKey
        }
    }

    val onModelChange: (String) -> Unit = { newModel ->
        when (selectedTab) {
            "OpenAI" -> openaiModel = newModel
            "Anthropic" -> anthropicModel = newModel
            else -> geminiModel = newModel
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    Column {
                        Text(
                            text = "AI Provider & Model Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure API keys, models, and test connections",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Provider Selection Tabs
                TabRow(
                    selectedTabIndex = providers.indexOf(selectedTab).coerceAtLeast(0)
                ) {
                    providers.forEach { provider ->
                        val isActive = selectedProvider.equals(provider, ignoreCase = true)
                        val hasKey = when (provider) {
                            "Gemini" -> geminiKey.isNotBlank() || viewModel.apiKeyManager.hasApiKey("Gemini")
                            "OpenAI" -> openaiKey.isNotBlank()
                            "Anthropic" -> anthropicKey.isNotBlank()
                            else -> false
                        }

                        Tab(
                            selected = selectedTab == provider,
                            onClick = {
                                selectedTab = provider
                                viewModel.clearConnectionTestResult()
                                statusMessage = null
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(provider, fontWeight = if (selectedTab == provider) FontWeight.Bold else FontWeight.Normal)
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                "ACTIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    } else if (hasKey) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                "KEY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Provider Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Provider Header Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val fullName = when (selectedTab) {
                                    "Gemini" -> "Google Gemini AI"
                                    "OpenAI" -> "OpenAI GPT Platform"
                                    "Anthropic" -> "Anthropic Claude AI"
                                    else -> selectedTab
                                }
                                Text(fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val keyStatus = if (currentKey.isNotBlank()) "API Key configured" else if (selectedTab == "Gemini" && viewModel.apiKeyManager.hasApiKey("Gemini")) "Default BuildConfig Key available" else "No API key entered"
                                Text(keyStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selectedProvider.equals(selectedTab, ignoreCase = true)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        "Active Engine",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // API Key Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("API Key", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = currentKey,
                            onValueChange = onKeyChange,
                            placeholder = {
                                Text(
                                    when (selectedTab) {
                                        "Gemini" -> "Enter Google AI Studio API key (AIza...)"
                                        "OpenAI" -> "Enter OpenAI API key (sk-...)"
                                        "Anthropic" -> "Enter Anthropic API key (sk-ant-...)"
                                        else -> "API Key"
                                    }
                                )
                            },
                            singleLine = true,
                            visualTransformation = if (showKeyPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKeyPassword = !showKeyPassword }) {
                                    Icon(
                                        imageVector = if (showKeyPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showKeyPassword) "Hide API Key" else "Show API Key"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val hint = when (selectedTab) {
                            "Gemini" -> "Obtain keys from Google AI Studio (aistudio.google.com)."
                            "OpenAI" -> "Obtain keys from OpenAI Dashboard (platform.openai.com)."
                            "Anthropic" -> "Obtain keys from Anthropic Console (console.anthropic.com)."
                            else -> ""
                        }
                        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Model Selection — Dynamic Live Discovery Source of Truth
                    val discoveryState by viewModel.aiFactory.modelSelectionRepository.getModelsState(selectedTab).collectAsStateWithLifecycle()
                    val discoveredModels = discoveryState.models
                    val hasApiKey = currentKey.isNotBlank()
                    val hasDiscovered = discoveredModels.isNotEmpty()
                    val isCached = discoveryState.isFromCache
                    val discoveryError = discoveryState.errorMessage
                    val lastRefreshTime = discoveryState.lastRefreshedTimestamp

                    LaunchedEffect(selectedTab, currentKey) {
                        if (hasApiKey && discoveryState.lastRefreshedTimestamp == null && !discoveryState.isLoading) {
                            viewModel.refreshModelsForProvider(selectedTab, currentKey)
                        }
                    }

                    val resolvedCurrentModel = if (hasDiscovered) {
                        if (discoveredModels.any { it.id.equals(currentModel, ignoreCase = true) }) {
                            discoveredModels.first { it.id.equals(currentModel, ignoreCase = true) }.id
                        } else {
                            val fallback = viewModel.aiFactory.modelSelectionRepository.getCompatibleFallback(selectedTab, currentModel, discoveredModels)
                            fallback?.id ?: discoveredModels.first().id
                        }
                    } else {
                        currentModel
                    }

                    LaunchedEffect(selectedTab, currentModel, hasDiscovered) {
                        if (hasDiscovered && resolvedCurrentModel != currentModel) {
                            onModelChange(resolvedCurrentModel)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Model Selection", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(6.dp))
                                when {
                                    !hasApiKey -> {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        ) {
                                            Text(
                                                "CONFIG REQUIRED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    hasDiscovered && !isCached -> {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                "VERIFIED LIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    hasDiscovered && isCached -> {
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
                                    else -> {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                "UNVERIFIED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(
                                onClick = { viewModel.refreshModelsForProvider(selectedTab, currentKey) },
                                enabled = !discoveryState.isLoading && hasApiKey,
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (discoveryState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh live models", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // State banner according to requirements
                        when {
                            !hasApiKey -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            "Configuration required — No verified live models",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Enter an API key above to discover models currently verified on your provider account.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            hasDiscovered && isCached -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        val timeStr = lastRefreshTime?.let {
                                            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it))
                                        } ?: "previous session"
                                        Text(
                                            "Cached models (${discoveredModels.size} available) • Last refreshed: $timeStr",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!discoveryError.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                "Current refresh issue: $discoveryError",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                            hasDiscovered && !isCached -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Live verified models (${discoveredModels.size} available from $selectedTab API)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            else -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Unable to verify models",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            discoveryError ?: "Model discovery did not succeed. Check your API key or network connection.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        OutlinedButton(
                                            onClick = { viewModel.refreshModelsForProvider(selectedTab, currentKey) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry / Refresh Models", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        var modelDropdownExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { modelDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (hasDiscovered) resolvedCurrentModel else "$currentModel (Unverified)",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("▼", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val desc = if (hasDiscovered) {
                                        discoveredModels.firstOrNull { it.id == resolvedCurrentModel }?.description
                                            ?: com.example.ai.AIModelCatalog.getModelDescription(resolvedCurrentModel)
                                            ?: "Live discovered model"
                                    } else {
                                        if (!hasApiKey) "Configuration required — API key needed to discover live models"
                                        else "Model availability not verified — discovery failed or pending"
                                    }
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                if (hasDiscovered) {
                                    discoveredModels.forEach { modelItem ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (resolvedCurrentModel == modelItem.id) {
                                                            Text("✓ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                        }
                                                        Text(modelItem.displayName, fontWeight = FontWeight.SemiBold)
                                                        if (modelItem.isImageGeneration) {
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
                                                    Text(
                                                        "ID: ${modelItem.id}${if (isCached) " (Cached)" else " (Live Verified)"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (!modelItem.description.isNullOrBlank()) {
                                                        Text(
                                                            modelItem.description,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onModelChange(modelItem.id)
                                                modelDropdownExpanded = false
                                            }
                                        )
                                    }
                                } else {
                                    // When discovery hasn't succeeded, DO NOT populate static models as available
                                    DropdownMenuItem(
                                        text = {
                                            Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    if (!hasApiKey) "Configuration Required" else "Unable to Verify Models",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    if (!hasApiKey) "Configure an API key and refresh to verify available live models."
                                                    else "Discovery did not succeed. Live models cannot be verified at this time.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Informational Offline Metadata Section (clearly separated from live models)
                        var showStaticCatalogInfo by remember { mutableStateOf(false) }
                        Column {
                            TextButton(
                                onClick = { showStaticCatalogInfo = !showStaticCatalogInfo },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (showStaticCatalogInfo) "▼ Hide Offline Catalog Reference" else "▶ Show Offline Catalog Reference (Informational Only)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (showStaticCatalogInfo) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Offline Documentation Metadata Only",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "The models below are reference entries from provider documentation. They are not authoritative and will not be treated as available without successful live discovery.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val staticEntries = com.example.ai.AIModelCatalog.getCatalogForProvider(selectedTab)
                                        staticEntries.forEach { entry ->
                                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(entry.displayName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(3.dp),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                    ) {
                                                        Text("OFFLINE METADATA", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                    }
                                                }
                                                Text("ID: ${entry.id} • ${entry.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Connection Testing Area
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Connection Diagnostic", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                OutlinedButton(
                                    onClick = {
                                        viewModel.testConnection(selectedTab, currentKey, currentModel)
                                    },
                                    enabled = !isTestingConnection
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Testing...")
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test Connection")
                                    }
                                }
                            }

                            if (connectionTestResult != null) {
                                val isSuccess = !connectionTestResult!!.startsWith("Error")
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = connectionTestResult!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (statusMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }

                    OutlinedButton(
                        onClick = {
                            // Save all current values
                            viewModel.saveProviderSettings("Gemini", geminiKey, geminiModel)
                            viewModel.saveProviderSettings("OpenAI", openaiKey, openaiModel)
                            viewModel.saveProviderSettings("Anthropic", anthropicKey, anthropicModel)
                            statusMessage = "All configurations saved successfully."
                        },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text("Save All")
                    }

                    Button(
                        onClick = {
                            // Save and activate current provider & model
                            viewModel.saveProviderSettings("Gemini", geminiKey, geminiModel)
                            viewModel.saveProviderSettings("OpenAI", openaiKey, openaiModel)
                            viewModel.saveProviderSettings("Anthropic", anthropicKey, anthropicModel)
                            viewModel.setProvider(selectedTab)
                            viewModel.setModel(currentModel)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.8f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as Active")
                    }
                }
            }
        }
    }
}
