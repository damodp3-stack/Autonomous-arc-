import re

with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'r') as f:
    content = f.read()

bad = """    override suspend fun sync(projectId: String): SyncResult {
        // Foundation: We just return success for now as actual file sync is out of scope
        return SyncResult.Success
    }
    
    override suspend fun pull(projectId: String): SyncResult {
        return SyncResult.Success
    }

    override suspend fun push(projectId: String, commitMessage: String): SyncResult {
        return SyncResult.Success
    }"""

good = """    override suspend fun sync(projectId: String, progress: (String) -> Unit): SyncResult {
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

content = content.replace(bad, good)

with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'w') as f:
    f.write(content)


with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content2 = f.read()

bad2 = """    override suspend fun sync(projectId: String): SyncResult {
        return SyncResult.Success
    }

    override suspend fun pull(projectId: String): SyncResult {
        return SyncResult.Success
    }

    override suspend fun push(projectId: String, commitMessage: String): SyncResult {
        return SyncResult.Success
    }"""

good2 = """    override suspend fun sync(projectId: String, progress: (String) -> Unit): SyncResult {
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

content2 = content2.replace(bad2, good2)

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content2)

