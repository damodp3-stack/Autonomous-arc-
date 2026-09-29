package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.MediaEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaVaultScreen(
    viewModel: MediaVaultViewModel,
    onNavigateToProject: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val isGeneratingMedia by viewModel.isGeneratingMedia.collectAsStateWithLifecycle()
    val generationStatus by viewModel.generationStatus.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPreviewItem by remember { mutableStateOf<MediaEntity?>(null) }
    var itemToRename by remember { mutableStateOf<MediaEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PermMedia,
                            contentDescription = "Media Vault",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Media Vault", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_media_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Add Media",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (generationStatus != null) {
                val statusText = generationStatus!!
                val isSuccess = statusText.startsWith("Success")
                val isMock = statusText.startsWith("[Mock")
                val isFailed = statusText.startsWith("Generation failed") || statusText.contains("Error", ignoreCase = true)
                val bgColor = when {
                    isSuccess -> MaterialTheme.colorScheme.primaryContainer
                    isMock -> MaterialTheme.colorScheme.secondaryContainer
                    isFailed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val textColor = when {
                    isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
                    isMock -> MaterialTheme.colorScheme.onSecondaryContainer
                    isFailed -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearGenerationStatus() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp), tint = textColor)
                        }
                    }
                }
            }

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search media...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("ALL" to "All", "IMAGE" to "Images", "VIDEO" to "Videos")
                items(filters.size) { index ->
                    val (key, label) = filters[index]
                    FilterChip(
                        selected = filterType == key,
                        onClick = { viewModel.setFilter(key) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Media Grid or Empty State
            if (mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No media in the vault yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add an image or video asset to store it securely in app storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("create_sample_media_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Sample Media")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mediaItems, key = { it.id }) { item ->
                        MediaItemCard(
                            item = item,
                            isValid = viewModel.isMediaFileValid(item),
                            onClick = { selectedPreviewItem = item },
                            onRename = {
                                itemToRename = item
                                renameInput = item.filename
                            },
                            onDelete = { viewModel.deleteMedia(item.id) }
                        )
                    }
                }
            }
        }

        // Preview Dialog
        if (selectedPreviewItem != null) {
            val previewItem = selectedPreviewItem!!
            val file = viewModel.getMediaFile(previewItem)
            MediaPreviewDialog(
                media = previewItem,
                file = file,
                onDismiss = { selectedPreviewItem = null },
                onNavigateToProject = { projId ->
                    selectedPreviewItem = null
                    onNavigateToProject(projId)
                }
            )
        }

        // Rename Dialog
        if (itemToRename != null) {
            val item = itemToRename!!
            AlertDialog(
                onDismissRequest = { itemToRename = null },
                title = { Text("Rename Media") },
                text = {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Filename") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameInput.isNotBlank()) {
                                viewModel.renameMedia(item.id, renameInput)
                                itemToRename = null
                            }
                        }
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToRename = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add Media Dialog
        if (showAddDialog) {
            var selectedMode by remember { mutableStateOf(0) }
            var promptText by remember { mutableStateOf("") }
            var selectedModel by remember { mutableStateOf("gemini-2.5-flash-image") }
            var mediaTitle by remember { mutableStateOf("Asset_${System.currentTimeMillis() % 1000}") }
            var isVideoSelected by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isGeneratingMedia) showAddDialog = false },
                title = { Text(if (selectedMode == 0) "Generate AI Media" else "Create Local Asset") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedMode == 0,
                                onClick = { selectedMode = 0 },
                                label = { Text("AI Generator") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                            )
                            FilterChip(
                                selected = selectedMode == 1,
                                onClick = { selectedMode = 1 },
                                label = { Text("Local Asset") },
                                leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) }
                            )
                        }

                        if (selectedMode == 0) {
                            Text(
                                "Generate a real image using the AI Media Provider (saved directly to Media Vault):",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = promptText,
                                onValueChange = { promptText = it },
                                label = { Text("Image Prompt") },
                                placeholder = { Text("A futuristic minimalist logo...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Model: ", style = MaterialTheme.typography.labelSmall)
                                AssistChip(
                                    onClick = {},
                                    label = { Text(selectedModel) }
                                )
                            }
                        } else {
                            Text("Create a new media file in the local media vault:")
                            OutlinedTextField(
                                value = mediaTitle,
                                onValueChange = { mediaTitle = it },
                                label = { Text("Media Name") },
                                singleLine = true
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !isVideoSelected,
                                    onClick = { isVideoSelected = false },
                                    label = { Text("Image (PNG)") },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                                )
                                FilterChip(
                                    selected = isVideoSelected,
                                    onClick = { isVideoSelected = true },
                                    label = { Text("Video (MP4)") },
                                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (selectedMode == 0) {
                        Button(
                            onClick = {
                                if (promptText.isNotBlank()) {
                                    viewModel.generateMediaAsset(promptText, selectedModel)
                                    showAddDialog = false
                                }
                            },
                            enabled = !isGeneratingMedia && promptText.isNotBlank()
                        ) {
                            if (isGeneratingMedia) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generating...")
                            } else {
                                Text("Generate")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (mediaTitle.isNotBlank()) {
                                    viewModel.createSampleMedia(mediaTitle, isVideo = isVideoSelected)
                                    showAddDialog = false
                                }
                            }
                        ) {
                            Text("Create Asset")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }, enabled = !isGeneratingMedia) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun MediaItemCard(
    item: MediaEntity,
    isValid: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Thumbnail or Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (item.isImage && isValid) {
                    AsyncImage(
                        model = File(item.filePath),
                        contentDescription = item.filename,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Corrupt or Missing File",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                // Type badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.mediaType,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Info & Actions
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.fileSizeBytes / 1024} KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaPreviewDialog(
    media: MediaEntity,
    file: File?,
    onDismiss: () -> Unit,
    onNavigateToProject: (String) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val dateString = formatter.format(Date(media.createdTimestamp))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = media.filename,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Media visual
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (media.isImage && file != null && file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = media.filename,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else if (media.isVideo) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Play Video",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Android Playback Ready", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text("File missing on device storage", color = MaterialTheme.colorScheme.error)
                    }
                }

                // Details
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Type: ${media.mediaType}", style = MaterialTheme.typography.bodySmall)
                    Text("Size: ${media.fileSizeBytes} bytes (${media.fileSizeBytes / 1024} KB)", style = MaterialTheme.typography.bodySmall)
                    Text("Created: $dateString", style = MaterialTheme.typography.bodySmall)
                    if (media.projectAssociation != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { onNavigateToProject(media.projectAssociation) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Linked Project")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
