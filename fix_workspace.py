import re

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

# Remove the incorrectly placed github dialog block before MessageBubble
bad_block = re.search(r'if \(showGitHubDialog\) \{.*?(?=@Composable\nfun MessageBubble)', content, flags=re.DOTALL)
if bad_block:
    content = content.replace(bad_block.group(0), '')

# Put it correctly inside WorkspaceScreen, just before FileExplorerContent
insertion = '''
    if (showGitHubDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val db = androidx.compose.runtime.remember { com.example.data.AppDatabase.getDatabase(context) }
        val tokenManager = androidx.compose.runtime.remember { com.example.github.SimpleTokenManager(context) }
        val gitHubServices = androidx.compose.runtime.remember { com.example.github.RealGitHubServices(tokenManager) }
        
        val githubViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.GitHubViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return com.example.ui.GitHubViewModel(
                        projectId = viewModel.projectId, // We must have viewModel in scope here
                        authService = gitHubServices,
                        githubService = gitHubServices,
                        configRepository = com.example.data.GitHubConfigRepository(db.githubConfigDao())
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
'''

content = content.replace('}\n\n@Composable\nfun FileExplorerContent(', insertion)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
