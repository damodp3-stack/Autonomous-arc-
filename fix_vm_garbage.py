import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

replacement = r"""
                        _discoveryState.value = RepositoryDiscoveryState.Cloning("Downloading file: ${item.path} ($filesProcessed/$totalFiles)")
                        val blob = githubService.getBlob(owner, repo, item.sha)
                        val content = if (blob.encoding == "base64") {
                            val cleanBase64 = blob.content.replace("\n", "").replace("\r", "")
                            String(android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT), kotlin.text.Charsets.UTF_8)
                        } else {
                            blob.content
                        }
                        fileRepository.createFile(projectId, item.path, content)
"""

content = re.sub(r'_discoveryState\.value = RepositoryDiscoveryState\.Cloning\("Downloading file: \$\{item\.path\} \(\$filesProcessed/\$totalFiles\)"\)[\s\S]*?fileRepository\.createFile\(projectId, item\.path, content\)', replacement.strip(), content)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)

