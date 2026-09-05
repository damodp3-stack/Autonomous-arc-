import re

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

# 1. MessageBubble signature change
content = content.replace(
    'fun MessageBubble(message: MessageEntity) {',
    'fun MessageBubble(message: MessageEntity, onReviewProposal: (() -> Unit)? = null) {'
)

# 2. Add button in MessageBubble
bubble_inner = """
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
"""
content = re.sub(
    r'Surface\(\s*shape = shape,\s*color = backgroundColor,\s*border = if \(isUser\) null else androidx\.compose\.foundation\.BorderStroke\(1\.dp, MaterialTheme\.colorScheme\.outline\),\s*shadowElevation = 1\.dp\s*\) \{[\s\S]*?Text\([\s\S]*?modifier = Modifier\.padding\(12\.dp\)\s*\)\s*\}',
    bubble_inner.strip(),
    content
)

# 3. Update items(messages) to pass onReviewProposal
items_messages_new = """
                    items(messages) { message ->
                        MessageBubble(message, onReviewProposal = { viewModel.reviewProposal() })
                    }
"""
content = re.sub(r'items\(messages\) \{ message ->\s*MessageBubble\(message\)\s*\}', items_messages_new.strip(), content)

# 4. Remove all dialogs after "if (showNewFileDialog) {...}" and replace with new UI
dialog_start = content.find('val applyState by viewModel.applyState.collectAsStateWithLifecycle()')
if dialog_start != -1:
    dialog_end = content.find('@Composable\nfun FileExplorerContent')
    
    new_ui = """
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
        AlertDialog(
            onDismissRequest = { viewModel.dismissProposal() },
            title = { Text("Error") },
            text = { Text(viewModel.proposalError.collectAsStateWithLifecycle().value ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProposal() }) {
                    Text("OK")
                }
            }
        )
    }
\n"""
    
    content = content[:dialog_start] + new_ui + content[dialog_end:]

# Add Dialog imports
imports_to_add = """import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TopAppBar
"""
content = content.replace('import androidx.compose.material3.TextButton\n', 'import androidx.compose.material3.TextButton\n' + imports_to_add)


with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
