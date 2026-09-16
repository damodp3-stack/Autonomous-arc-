with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

old_actions = """                        Box {
                            TextButton(onClick = { providerMenuExpanded = true }) {
                                Text(selectedProvider)
                            }
                            DropdownMenu("""

new_actions = """                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = "Settings")
                        }
                        Box {
                            TextButton(onClick = { providerMenuExpanded = true }) {
                                Text(selectedProvider)
                            }
                            DropdownMenu("""

content = content.replace(old_actions, new_actions)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
