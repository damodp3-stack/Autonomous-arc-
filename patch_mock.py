with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

replacement = """    override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree {
        kotlinx.coroutines.delay(100)
        
        if (repo == "truncated-repo") {
            if (treeSha == "root") {
                return GitHubTree("mock", "url", true, listOf(
                    GitHubTreeItem("src", "040000", "tree", "src-sha", null, "url")
                ))
            } else if (treeSha == "src-sha") {
                return GitHubTree("mock", "url", false, listOf(
                    GitHubTreeItem("main.kt", "100644", "blob", "main-sha", 100, "url")
                ))
            }
        }
        
        if (repo == "oversized-repo") {
            return GitHubTree("mock", "url", false, listOf(
                GitHubTreeItem("large.bin", "100644", "blob", "large-sha", 15 * 1024 * 1024, "url")
            ))
        }
        
        if (repo == "duplicate-repo") {
            return GitHubTree("mock", "url", false, listOf(
                GitHubTreeItem("src/main.kt", "100644", "blob", "main-sha", 100, "url"),
                GitHubTreeItem("src//main.kt", "100644", "blob", "main-sha", 100, "url")
            ))
        }

        // normal repository with nested directories and mixed files
        return GitHubTree(
            sha = "mock-tree-sha",
            url = "url",
            truncated = false,
            tree = listOf(
                GitHubTreeItem("README.md", "100644", "blob", "readme-sha", 100, "url"),
                GitHubTreeItem("src", "040000", "tree", "src-sha", null, "url"),
                GitHubTreeItem("src/main.kt", "100644", "blob", "main-sha", 150, "url"),
                GitHubTreeItem("assets", "040000", "tree", "assets-sha", null, "url"),
                GitHubTreeItem("assets/icon.png", "100644", "blob", "icon-sha", 200, "url")
            )
        )
    }

    override suspend fun getBlob(owner: String, repo: String, fileSha: String): GitHubBlob {
        kotlinx.coroutines.delay(100)
        when (fileSha) {
            "icon-sha" -> return GitHubBlob("iVBORw0KGgo=", "base64", "icon-sha", 200) // binary fake
            "main-sha" -> return GitHubBlob("cHJpbnRsbigiaGVsbG8iKQ==", "base64", "main-sha", 150)
            "readme-sha" -> return GitHubBlob("IyBNb2NrIFJlcG8KCk1vY2sgY29udGVudA==", "base64", "readme-sha", 100)
            "large-sha" -> return GitHubBlob("", "base64", "large-sha", 15 * 1024 * 1024)
            else -> return GitHubBlob("ZGVmYXVsdA==", "base64", fileSha, 100)
        }
    }
}"""

content = content.replace("    override suspend fun getTree", replacement.split("    override suspend fun getTree")[0] + "    override suspend fun getTree")
content = content.rsplit("    override suspend fun getTree", 1)[0] + replacement

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
