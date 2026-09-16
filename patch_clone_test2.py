import re

with open('app/src/test/java/com/example/github/GitHubCloneTest.kt', 'r') as f:
    content = f.read()

bad = """    private fun createViewModel(projectId: String): GitHubViewModel {
        return GitHubViewModel(projectId, githubService, githubService, configRepository, fileRepository)
    }"""

good = """    private fun createViewModel(projectId: String): GitHubViewModel {
        val syncService = com.example.github.RealGitHubSyncService(githubService, fileRepository, configRepository)
        return GitHubViewModel(projectId, githubService, githubService, configRepository, syncService)
    }"""

content = content.replace(bad, good)

with open('app/src/test/java/com/example/github/GitHubCloneTest.kt', 'w') as f:
    f.write(content)

