
import re

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

if 'import androidx.compose.material.icons.filled.CloudQueue' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.Close', 'import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue')

content = re.sub(r'if \(showGitHubDialog\) \{.*?(?=@Composable
fun MessageBubble)', '', content, flags=re.DOTALL)
content = re.sub(r'IconButton\(onClick = \{ showGitHubDialog = true \}\) \{.*?\}', '', content, flags=re.DOTALL)

content = content.replace('var showGitHubDialog by remember { mutableStateOf(false) }
    val selectedFile', 'val selectedFile')
content = content.replace('var showGitHubDialog by remember { mutableStateOf(false) }
', '')

insertion_point = 'val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()'
if insertion_point in content:
    content = content.replace(insertion_point, 'var showGitHubDialog by remember { mutableStateOf(false) }
    ' + insertion_point)

action_insertion = 'val availableProviders = viewModel.availableProviders'
if action_insertion in content:
    content = content.replace(action_insertion, action_insertion + '''
                        IconButton(onClick = { showGitHubDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "GitHub Integration",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }''')

end_scaffold = '''        }
    }
}

@Composable
fun MessageBubble('''
if end_scaffold in content:
    content = content.replace(end_scaffold, '''        }
    }

    if (showGitHubDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val db = remember { com.example.data.AppDatabase.getDatabase(context) }
        val tokenManager = remember { com.example.github.SimpleTokenManager(context) }
        val gitHubServices = remember { com.example.github.RealGitHubServices(tokenManager) }
        
        val githubViewModel = androidx.lifecycle.viewmodel.compose.viewModel<GitHubViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return GitHubViewModel(
                        projectId = viewModel.projectId,
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

