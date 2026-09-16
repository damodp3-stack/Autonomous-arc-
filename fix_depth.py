import re

with open('app/src/main/java/com/example/github/RealGitHubSyncService.kt', 'r') as f:
    content = f.read()

bad = """    private suspend fun fetchTreeRecursive(
        owner: String,
        repo: String,
        treeSha: String
    ): List<GitHubTreeItem> {"""

good = """    private suspend fun fetchTreeRecursive(
        owner: String,
        repo: String,
        treeSha: String,
        depth: Int = 0
    ): List<GitHubTreeItem> {
        if (depth > 10) throw Exception("Repository depth too large")"""

content = content.replace(bad, good)
content = content.replace('fetchTreeRecursive(owner, repo, item.sha)', 'fetchTreeRecursive(owner, repo, item.sha, depth + 1)')

with open('app/src/main/java/com/example/github/RealGitHubSyncService.kt', 'w') as f:
    f.write(content)

