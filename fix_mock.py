with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

replacement = """    override suspend fun getBranches(owner: String, repo: String): List<GitHubBranch> {
        delay(500)
        return listOf(
            GitHubBranch("main", GitHubCommitBase("mock-sha", "url")),
            GitHubBranch("develop", GitHubCommitBase("mock-sha-2", "url"))
        )
    }

    override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree {
        delay(500)
        return GitHubTree(
            sha = "mock-tree-sha",
            url = "url",
            truncated = false,
            tree = listOf(
                GitHubTreeItem("README.md", "100644", "blob", "mock-blob-sha", 100, "url")
            )
        )
    }

    override suspend fun getBlob(owner: String, repo: String, fileSha: String): GitHubBlob {
        delay(500)
        return GitHubBlob(
            content = "IyBNb2NrIFJlcG8KCk1vY2sgY29udGVudA==", // "# Mock Repo\n\nMock content" base64
            encoding = "base64",
            sha = "mock-blob-sha",
            size = 100
        )
    }"""

content = content.replace("""    override suspend fun getBranches(owner: String, repo: String): List<GitHubBranch> {
        delay(500)
        return listOf(
            GitHubBranch("main", GitHubCommitBase("mock-sha", "url")),
            GitHubBranch("develop", GitHubCommitBase("mock-sha-2", "url"))
        )
    }""", replacement)

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
