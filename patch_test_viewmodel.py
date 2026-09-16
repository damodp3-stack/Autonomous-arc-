import re

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

bad = """    private fun createViewModel(projectId: String): GitHubViewModel {
        return GitHubViewModel(projectId, mockService, mockService, configRepo, fileRepo)
    }"""

good = """    private fun createViewModel(projectId: String): GitHubViewModel {
        val syncService = com.example.github.RealGitHubSyncService(mockService, fileRepo, configRepo)
        return GitHubViewModel(projectId, mockService, mockService, configRepo, syncService)
    }"""

content = content.replace(bad, good)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)

