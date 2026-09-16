import re

# Remove GitHubSyncService from RealGitHubServices
with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'r') as f:
    content = f.read()

content = content.replace(': GitHubAuthService, GitHubService, GitHubSyncService', ': GitHubAuthService, GitHubService')

bad = """    override suspend fun sync(projectId: String, progress: (String) -> Unit): SyncResult {
        return SyncResult.Success
    }
    
    override suspend fun pull(projectId: String, progress: (String) -> Unit): SyncResult {
        return SyncResult.Success
    }

    override suspend fun push(projectId: String, commitMessage: String, summary: CommitSummary, progress: (String) -> Unit): SyncResult {
        return SyncResult.Success
    }
    
    override suspend fun detectChanges(projectId: String): CommitSummary? {
        return null
    }"""

content = content.replace(bad, "")

with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'w') as f:
    f.write(content)


# Remove GitHubSyncService from MockGitHubService (or we can keep it there, let's keep it but just update the DI)
with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content2 = f.read()

content2 = content2.replace(': GitHubService, GitHubAuthService, GitHubSyncService', ': GitHubService, GitHubAuthService')

bad2 = """    override suspend fun sync(projectId: String, progress: (String) -> Unit): SyncResult {
        return SyncResult.Success
    }

    override suspend fun pull(projectId: String, progress: (String) -> Unit): SyncResult {
        return SyncResult.Success
    }

    override suspend fun push(projectId: String, commitMessage: String, summary: CommitSummary, progress: (String) -> Unit): SyncResult {
        return SyncResult.Success
    }
    
    override suspend fun detectChanges(projectId: String): CommitSummary? {
        return null
    }"""

content2 = content2.replace(bad2, "")

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content2)

