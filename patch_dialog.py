import re

with open('app/src/main/java/com/example/ui/GitHubIntegrationDialog.kt', 'r') as f:
    content = f.read()

# First, fix imports if necessary
if 'import com.example.github.GitHubBranch' not in content:
    content = content.replace('import com.example.github.GitHubRepository', 'import com.example.github.GitHubRepository\nimport com.example.github.GitHubBranch')

if 'import androidx.compose.material.icons.filled.ArrowBack' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.Close', 'import androidx.compose.material.icons.filled.Close\nimport androidx.compose.material.icons.filled.ArrowBack')

if 'import androidx.compose.material3.RadioButton' not in content:
    content = content.replace('import androidx.compose.material3.TextButton', 'import androidx.compose.material3.TextButton\nimport androidx.compose.material3.RadioButton')

# Fix discovery state matching
discovery_view = """
@Composable
fun RepositoryDiscoveryView(
    discoveryState: RepositoryDiscoveryState,
    onFetch: () -> Unit,
    onSelectRepository: (GitHubRepository) -> Unit,
    onSelectBranch: (GitHubBranch) -> Unit,
    onConnect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Connect a Repository", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        when (discoveryState) {
            is RepositoryDiscoveryState.Idle -> {
                Button(onClick = onFetch, modifier = Modifier.fillMaxWidth()) {
                    Text("Load Repositories")
                }
            }
            is RepositoryDiscoveryState.LoadingRepositories, is RepositoryDiscoveryState.LoadingBranches, is RepositoryDiscoveryState.Connecting -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RepositoryDiscoveryState.Error -> {
                Text(discoveryState.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onFetch, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry")
                }
            }
            is RepositoryDiscoveryState.RepositoriesLoaded -> {
                LazyColumn {
                    items(discoveryState.repositories) { repo ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectRepository(repo) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(repo.fullName, fontWeight = FontWeight.Bold)
                                if (repo.description != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(repo.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            is RepositoryDiscoveryState.BranchesLoaded -> {
                Text("Repository: ${discoveryState.repository.fullName}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select Branch", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(discoveryState.branches) { branch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBranch(branch) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = branch == discoveryState.selectedBranch,
                                onClick = { onSelectBranch(branch) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(branch.name)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onConnect,
                    enabled = discoveryState.selectedBranch != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
"""

content = re.sub(r'@Composable\nfun RepositoryDiscoveryView.*?(?=@Composable\nfun ConnectedProjectView)', discovery_view, content, flags=re.DOTALL)

call_site_replacement = """
                                        RepositoryDiscoveryView(
                                            discoveryState = discoveryState,
                                            onFetch = { viewModel.fetchRepositories() },
                                            onSelectRepository = { repo -> viewModel.selectRepository(repo) },
                                            onSelectBranch = { branch -> viewModel.selectBranch(branch) },
                                            onConnect = { viewModel.connectRepository() }
                                        )
"""

content = re.sub(r'                                        RepositoryDiscoveryView\(.*?onConnect = \{ repo -> viewModel\.connectRepository\(repo\) \}\n                                        \)', call_site_replacement.strip('\n'), content, flags=re.DOTALL)


with open('app/src/main/java/com/example/ui/GitHubIntegrationDialog.kt', 'w') as f:
    f.write(content)
