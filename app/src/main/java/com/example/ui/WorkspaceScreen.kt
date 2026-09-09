package com.example.ui







import androidx.compose.ui.window.Dialog





import androidx.compose.ui.window.DialogProperties





import androidx.compose.material3.TopAppBar





import androidx.compose.material3.ExperimentalMaterial3Api






import androidx.compose.foundation.background





import androidx.compose.foundation.clickable





import androidx.compose.foundation.layout.*





import androidx.compose.foundation.lazy.LazyColumn





import androidx.compose.foundation.lazy.items





import androidx.compose.foundation.lazy.rememberLazyListState





import androidx.compose.foundation.shape.RoundedCornerShape





import androidx.compose.material.icons.Icons





import androidx.compose.material.icons.automirrored.filled.ArrowBack





import androidx.compose.material.icons.automirrored.filled.Send





import androidx.compose.material.icons.filled.Build





import androidx.compose.material.icons.filled.Folder





import androidx.compose.material.icons.filled.Menu





import androidx.compose.material.icons.filled.Add





import androidx.compose.material.icons.filled.Delete





import androidx.compose.material.icons.filled.Save





import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue





import androidx.compose.material.icons.filled.Edit





import androidx.compose.material3.*





import androidx.compose.runtime.*





import androidx.compose.ui.Alignment





import androidx.compose.ui.Modifier





import androidx.compose.ui.draw.clip





import androidx.compose.ui.graphics.Color





import androidx.compose.ui.platform.testTag





import androidx.compose.ui.text.font.FontFamily





import androidx.compose.ui.text.font.FontWeight





import androidx.compose.ui.unit.dp





import androidx.lifecycle.compose.collectAsStateWithLifecycle




import com.example.data.MessageEntity




import com.example.data.ProjectFileEntity




import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: WorkspaceViewModel, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val projectName by viewModel.projectName.collectAsStateWithLifecycle()
    val isBuilding by viewModel.isBuilding.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    var showGitHubDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val editorContent by viewModel.editorContent.collectAsStateWithLifecycle()
    
    val proposalState by viewModel.proposalState.collectAsStateWithLifecycle()
    val currentProposal by viewModel.currentProposal.collectAsStateWithLifecycle()
    val proposalError by viewModel.proposalError.collectAsStateWithLifecycle()

    var promptText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showNewFileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                FileExplorerContent(
                    files = files,
                    onFileSelected = {
                        viewModel.selectFile(it)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewFileClick = { showNewFileDialog = true },
                    onDeleteFile = { viewModel.deleteFile(it.id) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Files",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("AI Architect", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Project: $projectName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    actions = {
                        val availableProviders = viewModel.availableProviders
                        val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
                        var providerMenuExpanded by remember { mutableStateOf(false) }

                        IconButton(onClick = { showGitHubDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "GitHub Integration",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box {
                            TextButton(onClick = { providerMenuExpanded = true }) {
                                Text(selectedProvider)
                            }
                            DropdownMenu(
                                expanded = providerMenuExpanded,
                                onDismissRequest = { providerMenuExpanded = false }
                            ) {
                                availableProviders.forEach { provider ->
                                    DropdownMenuItem(
                                        text = { Text(provider) },
                                        onClick = {
                                            viewModel.setProvider(provider)
                                            providerMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(com.example.ui.theme.HighDensityMainBg)
            ) {
                // Main split: Editor (if active) and Chat
                if (selectedFile != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Editor Header
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedFile!!.path,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { viewModel.saveCurrentFile() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.closeFile() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        
                        // Editor Content
                        TextField(
                            value = editorContent,
                            onValueChange = { viewModel.updateEditorContent(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("code_editor"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Chat Area
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (messages.isEmpty() && selectedFile == null) {
                        item {
                            EmptyState()
                        }
                    }
                    items(messages) { message ->
                        MessageBubble(message, onReviewProposal = { viewModel.reviewProposal() })
                    }
                    if (isBuilding) {
                        item {
                            BuildingState()
                        }
                    }
                    if (proposalState == ProposalState.GENERATING) {
                        item {
                            GeneratingState()
                        }
                    }
                }

                // Input Area
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = promptText,
                                    onValueChange = { promptText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("prompt_input"),
                                    placeholder = { Text("Describe your code or feature...", style = MaterialTheme.typography.bodySmall) },
                                    maxLines = 4,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                    )
                                )
                                
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row {
                                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                            Icon(Icons.Default.Folder, contentDescription = "Files", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (promptText.isNotBlank() && !isBuilding && proposalState != ProposalState.GENERATING) {
                                                    viewModel.proposeChange(promptText)
                                                    promptText = ""
                                                }
                                            },
                                            enabled = !isBuilding && proposalState != ProposalState.GENERATING
                                        ) {
                                            Text("PROPOSE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        }

                                        Button(
                                            onClick = {
                                                if (promptText.isNotBlank() && !isBuilding && proposalState != ProposalState.GENERATING) {
                                                    viewModel.sendMessage(promptText)
                                                    promptText = ""
                                                }
                                            },
                                            enabled = !isBuilding && proposalState != ProposalState.GENERATING,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.testTag("send_button"),
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                        ) {
                                            Text("BUILD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Build",
                                                modifier = Modifier.size(16.dp)
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
    }

    if (showNewFileDialog) {
        var newFilePath by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New File") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFilePath,
                        onValueChange = { 
                            newFilePath = it
                            isError = it.contains("..") || it.trim().isEmpty()
                        },
                        label = { Text("File Path (e.g., src/main.kt)") },
                        isError = isError,
                        singleLine = true
                    )
                    if (isError) {
                        Text(
                            "Invalid path.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val path = newFilePath.trim()
                        if (path.isNotEmpty() && !path.contains("..")) {
                            viewModel.createFile(path)
                            showNewFileDialog = false
                        }
                    },
                    enabled = !isError && newFilePath.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    
    val applyResult by viewModel.applyResult.collectAsStateWithLifecycle()

    if (proposalState == ProposalState.REVIEWING && currentProposal != null) {
        Dialog(
            onDismissRequest = { viewModel.dismissProposal() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(
                        title = { Text("Diff Viewer") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.dismissProposal() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "AI CODE PROPOSAL",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentProposal!!.summary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentProposal!!.explanation,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "${currentProposal!!.changes.size} files affected",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(currentProposal!!.changes) { change ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = change.filePath,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val opColor = when(change.operation) {
                                                com.example.ai.FileOperation.CREATE -> Color(0xFF4CAF50)
                                                com.example.ai.FileOperation.MODIFY -> Color(0xFF2196F3)
                                                com.example.ai.FileOperation.DELETE -> Color(0xFFF44336)
                                            }
                                            Surface(
                                                color = opColor.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = change.operation.name,
                                                    color = opColor,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        if (change.operation == com.example.ai.FileOperation.DELETE) {
                                            Text(
                                                text = "WARNING: This file will be deleted.",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        } else {
                                            if (change.operation == com.example.ai.FileOperation.MODIFY) {
                                                Text("OLD CONTENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = change.originalContent.take(300) + if (change.originalContent.length > 300) "\n..." else "",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                        modifier = Modifier.padding(8.dp),
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                            }
                                            
                                            Text("NEW CONTENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = change.proposedContent.take(300) + if (change.proposedContent.length > 300) "\n..." else "",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                    modifier = Modifier.padding(8.dp),
                                                    color = Color(0xFF4CAF50)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.rejectProposal() }) {
                                Text("Reject", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(onClick = { viewModel.approveAndApplyProposal() }) {
                                Text("Approve & Apply")
                            }
                        }
                    }
                }
            }
        }
    }

    if (proposalState == ProposalState.APPLYING) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Applying Changes") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Validating...")
                    Text("Creating snapshot...")
                    Text("Applying changes...")
                    Text("Saving...")
                }
            },
            confirmButton = { }
        )
    }

    if (proposalState == ProposalState.APPLIED) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissProposal() },
            title = { Text("Success") },
            text = { Text("Changes applied successfully.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProposal() }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rollbackProposal() }) {
                    Text("Undo / Rollback")
                }
            }
        )
    }
    
    if (proposalState == ProposalState.REJECTED) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissProposal() },
            title = { Text("Proposal Rejected") },
            text = { Text("The changes were rejected and have not been applied.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProposal() }) {
                    Text("OK")
                }
            }
        )
    }

    if (proposalState == ProposalState.FAILED && applyResult != null) {
        val errorMessage = when (val res = applyResult) {
            is com.example.ai.ApplyResult.ValidationError -> "Validation Error: ${res.message}\n\nFile changed since this proposal was generated. Review again."
            is com.example.ai.ApplyResult.Conflict -> "Conflict in ${res.filePath}: ${res.message}\n\nFile changed since this proposal was generated. Review again."
            is com.example.ai.ApplyResult.ApplyError -> "Apply Error: ${res.message}\nRollback successful: ${res.rollbackSucceeded}"
            is com.example.ai.ApplyResult.RollbackError -> "Critical Error during Rollback: ${res.rollbackError}"
            else -> "Unknown error"
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissProposal() },
            title = { Text("Apply Failed") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProposal() }) {
                    Text("OK")
                }
            }
        )
    }
    
    if (proposalState == ProposalState.FAILED && applyResult == null) {
        val errorString = viewModel.proposalError.collectAsStateWithLifecycle().value ?: "Unknown error"
        AlertDialog(
            onDismissRequest = { viewModel.dismissProposal() },
            title = { Text("Error") },
            text = { Text(errorString) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProposal() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showGitHubDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val db = androidx.compose.runtime.remember { com.example.data.AppDatabase.getDatabase(context) }
        val tokenManager = androidx.compose.runtime.remember { com.example.github.SimpleTokenManager(context) }
        val gitHubServices = androidx.compose.runtime.remember { com.example.github.RealGitHubServices(tokenManager) }
        
        val githubViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.GitHubViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return com.example.ui.GitHubViewModel(
                        projectId = viewModel.projectId,
                        authService = gitHubServices,
                        githubService = gitHubServices,
                        configRepository = com.example.data.GitHubConfigRepository(db.githubConfigDao()),
                        fileRepository = viewModel.fileRepository
                    ) as T
                }
            }
        )
        
        com.example.ui.GitHubIntegrationDialog(
            viewModel = githubViewModel,
            onDismiss = { showGitHubDialog = false }
        )
    }
}

@Composable
fun FileExplorerContent(

    files: List<ProjectFileEntity>,
    onFileSelected: (ProjectFileEntity) -> Unit,
    onNewFileClick: () -> Unit,
    onDeleteFile: (ProjectFileEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Project Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNewFileClick) {
                Icon(Icons.Default.Add, contentDescription = "New File")
            }
        }
        
        HorizontalDivider()
        
        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No files yet.\nTap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(files) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileSelected(file) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Folder, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = file.path,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(
                            onClick = { onDeleteFile(file) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete, 
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    
}
    @Composable
fun MessageBubble(message: MessageEntity, onReviewProposal: (() -> Unit)? = null) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 0.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 48.dp else 0.dp,
                end = if (isUser) 0.dp else 48.dp
            ),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Surface(
                shape = shape,
                color = backgroundColor,
                border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 1.dp
            ) {
                Column {
                    Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                    if (!isUser && message.text.startsWith("AI proposed changes") && onReviewProposal != null) {
                        Button(
                            onClick = { onReviewProposal() },
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        ) {
                            Text("Review Changes")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isUser) "You" else "AI Assistant",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "What are we building today?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BuildingState() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Building...",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun GeneratingState() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Proposing changes...",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
