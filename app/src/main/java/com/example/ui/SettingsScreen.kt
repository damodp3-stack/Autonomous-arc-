package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sync.ConflictResolutionStrategy
import com.example.sync.LocalOnlySyncProvider
import com.example.sync.MockCloudSyncProvider
import com.example.sync.SyncRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    syncRepository: SyncRepository,
    onOpenAISettings: () -> Unit,
    onOpenGitHubSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val syncState by syncRepository.syncState.collectAsStateWithLifecycle()
    val pendingCount by syncRepository.getPendingCount().collectAsStateWithLifecycle(initialValue = 0)

    var syncMessage by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionResult by remember { mutableStateOf<String?>(null) }

    val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val lastSyncStr = syncState.lastSyncTimestamp?.let { formatter.format(Date(it)) } ?: "Never"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. AI Configuration Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "AI Providers & Models",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Configure API keys and models for Gemini, OpenAI, Anthropic, or Mock provider with real-time connection verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenAISettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("configure_ai_providers_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure AI Providers")
                        }
                    }
                }
            }

            // 2. GitHub Integration Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Source,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "GitHub Integration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Manage GitHub Personal Access Token (PAT), clone repositories, and push project workspaces.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onOpenGitHubSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manage GitHub Integration")
                        }
                    }
                }
            }

            // 3. Cloud Synchronization Foundation Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Cloud Synchronization Foundation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Offline-first architecture with Room as the local source of truth. Ready for cloud provider adapters without modifying application state.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Sync Status Details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Active Provider:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(syncState.currentProvider, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Last Synced:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(lastSyncStr, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pending Changes:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("$pendingCount items", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (connectionResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = connectionResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (syncMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = syncMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (syncState.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Provider Toggle (Offline Local Only vs Test Mock Sync Provider)
                        Text("Sync Mode:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val isOffline = syncState.currentProvider.contains("Local Only", ignoreCase = true)
                            FilterChip(
                                selected = isOffline,
                                onClick = {
                                    syncRepository.setProvider(LocalOnlySyncProvider())
                                    connectionResult = null
                                    syncMessage = null
                                },
                                label = { Text("Local Only (Offline)") }
                            )
                            FilterChip(
                                selected = !isOffline,
                                onClick = {
                                    syncRepository.setProvider(MockCloudSyncProvider())
                                    connectionResult = null
                                    syncMessage = null
                                },
                                label = { Text("Mock Cloud Sync") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: Test Connection & Sync Now
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isTestingConnection = true
                                        val res = syncRepository.testProviderConnection()
                                        connectionResult = if (res.isSuccess) {
                                            res.getOrNull()
                                        } else {
                                            "Connection failed: ${res.exceptionOrNull()?.message}"
                                        }
                                        isTestingConnection = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isTestingConnection && !syncState.isSyncing
                            ) {
                                Text(if (isTestingConnection) "Testing..." else "Test Link")
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val summary = syncRepository.syncAll(ConflictResolutionStrategy.LAST_WRITE_WINS)
                                        syncMessage = "Sync finished: ${summary.uploaded} uploaded, ${summary.downloaded} downloaded, ${summary.conflicts} conflicts."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !syncState.isSyncing
                            ) {
                                if (syncState.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Syncing...")
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now")
                                }
                            }
                        }
                    }
                }
            }

            // 4. App Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Autonomous Arc", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Version 1.0 (Phase 8 — Product Vaults & Cloud Sync)", style = MaterialTheme.typography.bodySmall)
                        Text("Database: Room Persistence v6 (SQLite)", style = MaterialTheme.typography.bodySmall)
                        Text("Architecture: Offline-first MVVM + Jetpack Compose", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
