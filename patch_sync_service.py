import re

with open('app/src/main/java/com/example/github/GitHubInterfaces.kt', 'r') as f:
    content = f.read()

bad = """interface GitHubSyncService {
    suspend fun sync(projectId: String): SyncResult
    suspend fun pull(projectId: String): SyncResult
    suspend fun push(projectId: String, commitMessage: String): SyncResult
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}"""

good = """interface GitHubSyncService {
    suspend fun sync(projectId: String): SyncResult
    suspend fun pull(projectId: String): SyncResult
    suspend fun detectChanges(projectId: String): com.example.ui.GitHubPushState
    suspend fun push(projectId: String, commitMessage: String, summary: com.example.ui.CommitSummary): com.example.ui.GitHubPushState
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
    data class Conflict(val message: String) : SyncResult()
}"""

content = content.replace(bad, good)

with open('app/src/main/java/com/example/github/GitHubInterfaces.kt', 'w') as f:
    f.write(content)

