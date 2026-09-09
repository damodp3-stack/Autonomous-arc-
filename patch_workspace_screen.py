import re

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

replacement = """                    return com.example.ui.GitHubViewModel(
                        projectId = viewModel.projectId,
                        authService = gitHubServices,
                        githubService = gitHubServices,
                        configRepository = com.example.data.GitHubConfigRepository(db.githubConfigDao()),
                        fileRepository = viewModel.fileRepository
                    ) as T"""

content = re.sub(r'                    return com\.example\.ui\.GitHubViewModel\(.*?\) as T', replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
