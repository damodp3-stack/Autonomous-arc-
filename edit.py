import sys

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
'''import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete''',
'''import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete''')

content = content.replace(
'''    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val isEditorOpen = selectedFile != null''',
'''    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val isEditorOpen = selectedFile != null

    // GitHub Integration Dialog state
    var showGitHubDialog by remember { mutableStateOf(false) }
    // Initialize GitHub view model - using generic initialization for simplicity here, assuming ViewModel factory or koin in a real app
    // We'll just pass the viewmodel directly or create a simple factory''')

content = content.replace(
'''                        var providerMenuExpanded by remember { mutableStateOf(false) }

                        Box {
                            TextButton(onClick = { providerMenuExpanded = true }) {''',
'''                        var providerMenuExpanded by remember { mutableStateOf(false) }

                        IconButton(onClick = { showGitHubDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "GitHub Integration",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box {
                            TextButton(onClick = { providerMenuExpanded = true }) {''')

content = content.replace(
'''        }
    }
}

@Composable
fun MessageBubble(''',
'''        }
    }
    
    if (showGitHubDialog) {
        // Need to create the ViewModel. Since we don't have a factory handy in the file, we can inject it or create it inline for this foundation demo
        val context = androidx.compose.ui.platform.LocalContext.current
        val db = remember { com.example.data.AppDatabase.getDatabase(context) }
        val tokenManager = remember { com.example.github.SimpleTokenManager(context) }
        val gitHubServices = remember { com.example.github.RealGitHubServices(tokenManager) }
        
        val githubViewModel = androidx.lifecycle.viewmodel.compose.viewModel<GitHubViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return GitHubViewModel(
                        projectId = projectId,
                        authService = gitHubServices,
                        githubService = gitHubServices,
                        configRepository = com.example.data.GitHubConfigRepository(db.githubConfigDao())
                    ) as T
                }
            }
        )
        
        GitHubIntegrationDialog(
            viewModel = githubViewModel,
            onDismiss = { showGitHubDialog = false }
        )
    }
}

@Composable
fun MessageBubble(''')

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
