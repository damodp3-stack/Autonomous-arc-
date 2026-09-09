import re

with open('app/src/main/java/com/example/ui/GitHubIntegrationDialog.kt', 'r') as f:
    content = f.read()

# Make sure connectRepository allows force parameter
content = content.replace('onConnect: () -> Unit', 'onConnect: (Boolean) -> Unit')
content = content.replace('onConnect = { viewModel.connectRepository() }', 'onConnect = { force -> viewModel.connectRepository(force) }')
content = content.replace('onConnect = { viewModel.connectRepository(it) }', 'onConnect = { force -> viewModel.connectRepository(force) }')

replacement = """
            is RepositoryDiscoveryState.LoadingRepositories, is RepositoryDiscoveryState.LoadingBranches, is RepositoryDiscoveryState.Connecting -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RepositoryDiscoveryState.Cloning -> {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(discoveryState.progress)
                }
            }
            is RepositoryDiscoveryState.Conflict -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Existing Files Detected", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This project already contains files. Cloning a repository will completely wipe the existing files in this project and replace them with the remote repository contents.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onConnect(true) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Wipe Files and Clone")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onFetch, // Go back to repository list (or could cancel)
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
"""

content = re.sub(r'            is RepositoryDiscoveryState.LoadingRepositories.*?(?=            is RepositoryDiscoveryState.Error -> \{)', replacement, content, flags=re.DOTALL)

content = content.replace('onClick = onConnect,', 'onClick = { onConnect(false) },')
# Just to make sure it doesn't duplicate we use exact replacement on the connect button:
content = content.replace('onClick = { onConnect(false) }(false)', 'onClick = { onConnect(false) }') # safety

with open('app/src/main/java/com/example/ui/GitHubIntegrationDialog.kt', 'w') as f:
    f.write(content)
