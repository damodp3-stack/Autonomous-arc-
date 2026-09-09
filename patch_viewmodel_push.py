import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

models = """
enum class ChangeType { ADDED, MODIFIED, DELETED, UNCHANGED }

data class FileChange(
    val path: String,
    val changeType: ChangeType,
    val isBinary: Boolean,
    val size: Long,
    val contentBytes: ByteArray? = null,
    val remoteSha: String? = null
)

data class CommitSummary(
    val changes: List<FileChange>,
    val additions: Int,
    val modifications: Int,
    val deletions: Int,
    val totalChangedSize: Long
)

sealed class GitHubPushState {
    object Idle : GitHubPushState()
    object DetectingChanges : GitHubPushState()
    data class ChangesReady(val summary: CommitSummary) : GitHubPushState()
    object NoChanges : GitHubPushState()
    data class Committing(val progress: String) : GitHubPushState()
    object Pushing : GitHubPushState()
    object Success : GitHubPushState()
    data class Conflict(val message: String) : GitHubPushState()
    data class Error(val message: String) : GitHubPushState()
}
"""

if "enum class ChangeType" not in content:
    content = content.replace("sealed class GitHubAuthState", models + "\nsealed class GitHubAuthState")

# Add pushState
if "val pushState: StateFlow<GitHubPushState>" not in content:
    content = content.replace(
        "    val discoveryState: StateFlow<RepositoryDiscoveryState> = _discoveryState\n",
        "    val discoveryState: StateFlow<RepositoryDiscoveryState> = _discoveryState\n\n    private val _pushState = MutableStateFlow<GitHubPushState>(GitHubPushState.Idle)\n    val pushState: StateFlow<GitHubPushState> = _pushState\n"
    )

# Save lastRemoteSha on clone
content = content.replace(
    """                    branch = selectedBranch.name,
                    isConnected = true""",
    """                    branch = selectedBranch.name,
                    isConnected = true,
                    lastRemoteSha = selectedBranch.commit.sha"""
)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)

