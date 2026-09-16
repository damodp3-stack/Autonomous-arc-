import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

# Replace dependencies in constructor
bad_constructor = """class GitHubViewModel(
    private val projectId: String,
    private val authService: GitHubAuthService,
    private val githubService: GitHubService,
    private val configRepository: GitHubConfigRepository,
    private val fileRepository: com.example.data.ProjectFileRepository
)"""
good_constructor = """class GitHubViewModel(
    private val projectId: String,
    private val authService: GitHubAuthService,
    private val githubService: GitHubService,
    private val configRepository: GitHubConfigRepository,
    private val syncService: GitHubSyncService
)"""

content = content.replace(bad_constructor, good_constructor)

# Remove calculateGitSha and fetchTreeRecursive
content = re.sub(r'private fun calculateGitSha[\s\S]*?fun detectChanges', 'fun detectChanges', content)

# Replace detectChanges and commitAndPush logic
bad_detect = r'fun detectChanges\(\) \{[\s\S]*?fun commitAndPush'
good_detect = """fun detectChanges() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _pushState.value = GitHubPushState.DetectingChanges
            try {
                val summary = syncService.detectChanges(projectId)
                if (summary == null) {
                    _pushState.value = GitHubPushState.NoChanges
                } else {
                    _pushState.value = GitHubPushState.ChangesReady(summary)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (e.message?.contains("Remote branch changed") == true) {
                    _pushState.value = GitHubPushState.Conflict(e.message!!)
                } else {
                    _pushState.value = GitHubPushState.Error(e.message ?: "Failed to detect changes")
                }
            }
        }
    }

    fun commitAndPush"""

content = re.sub(bad_detect, good_detect, content)

bad_push = r'fun commitAndPush\(message: String\) \{[\s\S]*?fun clearPushState'
good_push = """fun commitAndPush(message: String) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) {
            _pushState.value = GitHubPushState.Error("Commit message cannot be empty")
            return
        }

        val currentState = _pushState.value
        val summary = (currentState as? GitHubPushState.ChangesReady)?.summary
        if (summary == null || summary.changes.isEmpty()) {
            _pushState.value = GitHubPushState.Error("No changes to commit")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _pushState.value = GitHubPushState.Committing("Starting commit process...")
            val result = syncService.push(projectId, trimmedMessage, summary) { progress ->
                _pushState.value = GitHubPushState.Committing(progress)
            }
            
            when (result) {
                is SyncResult.Success -> {
                    _pushState.value = GitHubPushState.Success
                }
                is SyncResult.Conflict -> {
                    _pushState.value = GitHubPushState.Conflict(result.message)
                }
                is SyncResult.Error -> {
                    _pushState.value = GitHubPushState.Error(result.message)
                }
            }
        }
    }

    fun clearPushState"""

content = re.sub(bad_push, good_push, content)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)

