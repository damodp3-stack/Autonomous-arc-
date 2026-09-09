with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace("var totalSize = 0\n", "var totalSize = 0L\n")
content = content.replace("kotlinx.coroutines.flow.firstOrNull(fileRepository.getFilesForProject(projectId))", "fileRepository.getFilesForProject(projectId).firstOrNull()")

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)
