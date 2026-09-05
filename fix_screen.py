import re

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.strip() == "val applyResult by viewModel.applyResult.collectAsStateWithLifecycle()":
        break
    new_lines.append(line)

new_ui = """    val applyResult by viewModel.applyResult.collectAsStateWithLifecycle()

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
                                                        text = change.originalContent.take(300) + if (change.originalContent.length > 300) "\\n..." else "",
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
                                                    text = change.proposedContent.take(300) + if (change.proposedContent.length > 300) "\\n..." else "",
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
            is com.example.ai.ApplyResult.ValidationError -> "Validation Error: ${res.message}\\n\\nFile changed since this proposal was generated. Review again."
            is com.example.ai.ApplyResult.Conflict -> "Conflict in ${res.filePath}: ${res.message}\\n\\nFile changed since this proposal was generated. Review again."
            is com.example.ai.ApplyResult.ApplyError -> "Apply Error: ${res.message}\\nRollback successful: ${res.rollbackSucceeded}"
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
                    "No files yet.\\nTap + to create one.",
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
"""

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.writelines(new_lines)
    f.write(new_ui)

