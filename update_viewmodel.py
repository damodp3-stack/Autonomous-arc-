import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

# Update GitHubViewModel signature
sig_replace = """class GitHubViewModel(
    private val projectId: String,
    private val authService: GitHubAuthService,
    private val githubService: GitHubService,
    private val configRepository: GitHubConfigRepository,
    private val fileRepository: com.example.data.ProjectFileRepository
) : ViewModel() {"""

content = re.sub(r'class GitHubViewModel\([\s\S]*?\) : ViewModel\(\) \{', sig_replace, content)

# Update RepositoryDiscoveryState
state_replace = """sealed class RepositoryDiscoveryState {
    object Idle : RepositoryDiscoveryState()
    object LoadingRepositories : RepositoryDiscoveryState()
    data class RepositoriesLoaded(val repositories: List<GitHubRepository>) : RepositoryDiscoveryState()
    data class LoadingBranches(val repository: GitHubRepository) : RepositoryDiscoveryState()
    data class BranchesLoaded(val repository: GitHubRepository, val branches: List<GitHubBranch>, val selectedBranch: GitHubBranch?) : RepositoryDiscoveryState()
    data class Conflict(val repository: GitHubRepository, val selectedBranch: GitHubBranch) : RepositoryDiscoveryState()
    data class Cloning(val progress: String) : RepositoryDiscoveryState()
    object Connecting : RepositoryDiscoveryState()
    data class Error(val message: String) : RepositoryDiscoveryState()
}"""

content = re.sub(r'sealed class RepositoryDiscoveryState \{[\s\S]*?(?=class GitHubViewModel)', state_replace + '\n\n', content)

# Update connectRepository method
connect_replace = """
    fun connectRepository(force: Boolean = false) {
        val currentState = _discoveryState.value
        val (repository, selectedBranch) = when (currentState) {
            is RepositoryDiscoveryState.BranchesLoaded -> currentState.repository to currentState.selectedBranch
            is RepositoryDiscoveryState.Conflict -> currentState.repository to currentState.selectedBranch
            else -> return
        }

        if (selectedBranch == null) return

        viewModelScope.launch {
            if (!force) {
                val existingFiles = kotlinx.coroutines.flow.firstOrNull(fileRepository.getFilesForProject(projectId)) ?: emptyList()
                if (existingFiles.isNotEmpty()) {
                    _discoveryState.value = RepositoryDiscoveryState.Conflict(repository, selectedBranch)
                    return@launch
                }
            }

            _discoveryState.value = RepositoryDiscoveryState.Cloning("Starting clone...")
            try {
                fileRepository.clearFilesForProject(projectId)

                val owner = repository.fullName.substringBefore("/")
                val repo = repository.name
                
                _discoveryState.value = RepositoryDiscoveryState.Cloning("Fetching repository tree...")
                val tree = githubService.getTree(owner, repo, selectedBranch.commit.sha)
                
                var filesProcessed = 0
                val totalFiles = tree.tree.count { it.type == "blob" }

                for (item in tree.tree) {
                    if (item.type == "tree") {
                        fileRepository.createFile(projectId, item.path, isDirectory = true)
                    } else if (item.type == "blob") {
                        _discoveryState.value = RepositoryDiscoveryState.Cloning("Downloading file: ${item.path} ($filesProcessed/$totalFiles)")
                        val blob = githubService.getBlob(owner, repo, item.sha)
                        val content = if (blob.encoding == "base64") {
                            val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")
                            String(android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT))
                        } else {
                            blob.content
                        }
                        fileRepository.createFile(projectId, item.path, content)
                        filesProcessed++
                    }
                }

                _discoveryState.value = RepositoryDiscoveryState.Connecting
                val config = GitHubConfigEntity(
                    projectId = projectId,
                    owner = owner,
                    repository = repo,
                    branch = selectedBranch.name,
                    isConnected = true
                )
                configRepository.saveConfig(config)
                _discoveryState.value = RepositoryDiscoveryState.Idle // close discovery
            } catch (e: Exception) {
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to connect and clone")
            }
        }
    }
"""

content = re.sub(r'    fun connectRepository\(\) \{[\s\S]*?(?=    fun disconnectRepository\(\) \{)', connect_replace, content)

# Also need to add kotlinx.coroutines.flow.firstOrNull to imports if not present
if 'import kotlinx.coroutines.flow.firstOrNull' not in content:
    content = content.replace('import kotlinx.coroutines.flow.StateFlow', 'import kotlinx.coroutines.flow.StateFlow\nimport kotlinx.coroutines.flow.firstOrNull')

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)

