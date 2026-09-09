import re

with open('app/src/main/java/com/example/ui/GitHubIntegrationDialog.kt', 'r') as f:
    content = f.read()

replacement = """
fun ConnectedProjectView(
    config: com.example.data.GitHubConfigEntity,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
    onChangeRepository: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("● Connected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Repository:", fontWeight = FontWeight.Bold)
                Text("${config.owner}/${config.repository}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Branch:", fontWeight = FontWeight.Bold)
                Text(config.branch)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onSync) {
                Text("Refresh")
            }
            OutlinedButton(onClick = onChangeRepository) {
                Text("Change Repository")
            }
            OutlinedButton(onClick = onDisconnect) {
                Text("Disconnect")
            }
        }
    }
}
"""

content = re.sub(r'fun ConnectedProjectView\(.*?\n\}', replacement.strip(), content, flags=re.DOTALL)

# Update call site
content = content.replace("onSync = { /* Sync Foundation */ }", "onSync = { /* Sync Foundation */ },\n                                            onChangeRepository = { viewModel.disconnectRepository() }")

with open('app/src/main/java/com/example/ui/GitHubIntegrationDialog.kt', 'w') as f:
    f.write(content)

