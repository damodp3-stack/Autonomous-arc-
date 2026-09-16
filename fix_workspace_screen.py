with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

import re

# Add state for settings dialog
new_state = """    var providerMenuExpanded by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }"""

content = content.replace("    var providerMenuExpanded by remember { mutableStateOf(false) }", new_state)

# Add icon to top app bar
old_app_bar_actions = """                                IconButton(onClick = { providerMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "AI Provider")
                                }
                                DropdownMenu("""

new_app_bar_actions = """                                IconButton(onClick = { showSettingsDialog = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                                IconButton(onClick = { providerMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "AI Provider")
                                }
                                DropdownMenu("""

content = content.replace(old_app_bar_actions, new_app_bar_actions)

# Add Settings Dialog composable call
settings_dialog_call = """        if (showNewFileDialog) {
            NewFileDialog(
                onDismiss = { showNewFileDialog = false },
                onCreate = { name, isDirectory ->
                    viewModel.createFile(name)
                    showNewFileDialog = false
                }
            )
        }
        
        if (showSettingsDialog) {
            APIKeySettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSettingsDialog = false }
            )
        }"""

content = content.replace("""        if (showNewFileDialog) {
            NewFileDialog(
                onDismiss = { showNewFileDialog = false },
                onCreate = { name, isDirectory ->
                    viewModel.createFile(name)
                    showNewFileDialog = false
                }
            )
        }""", settings_dialog_call)

# Add the composable definition at the end
settings_dialog_def = """
@Composable
fun APIKeySettingsDialog(
    viewModel: WorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val providers = listOf("Gemini", "OpenAI", "Anthropic")
    var geminiKey by remember { mutableStateOf(viewModel.apiKeyManager.getApiKey("Gemini") ?: "") }
    var openaiKey by remember { mutableStateOf(viewModel.apiKeyManager.getApiKey("OpenAI") ?: "") }
    var anthropicKey by remember { mutableStateOf(viewModel.apiKeyManager.getApiKey("Anthropic") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Key Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openaiKey,
                    onValueChange = { openaiKey = it },
                    label = { Text("OpenAI API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = anthropicKey,
                    onValueChange = { anthropicKey = it },
                    label = { Text("Anthropic API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.apiKeyManager.saveApiKey("Gemini", geminiKey.trim())
                    viewModel.apiKeyManager.saveApiKey("OpenAI", openaiKey.trim())
                    viewModel.apiKeyManager.saveApiKey("Anthropic", anthropicKey.trim())
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
"""

content = content + settings_dialog_def

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
