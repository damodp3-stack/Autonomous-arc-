import re

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

bad = """                    return com.example.ui.GitHubViewModel(
                        projectId = viewModel.projectId,
                        authService = gitHubServices,
                        githubService = gitHubServices,
                        configRepository = com.example.data.GitHubConfigRepository(db.githubConfigDao()),
                        fileRepository = viewModel.fileRepository
                    ) as T"""

good = """                    val configRepo = com.example.data.GitHubConfigRepository(db.githubConfigDao())
                    val syncService = com.example.github.RealGitHubSyncService(
                        githubService = gitHubServices,
                        fileRepository = viewModel.fileRepository,
                        configRepository = configRepo
                    )
                    return com.example.ui.GitHubViewModel(
                        projectId = viewModel.projectId,
                        authService = gitHubServices,
                        githubService = gitHubServices,
                        configRepository = configRepo,
                        syncService = syncService
                    ) as T"""

content = content.replace(bad, good)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)

