with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

content = content.replace("""        if (repo == "oversized-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("large.bin", "100644", "blob", "large-sha", 15 * 1024 * 1024, "url")
            ))
        }""", """        if (repo == "oversized-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("large.bin", "100644", "blob", "large-sha", 15 * 1024 * 1024, "url")
            ), false)
        }""")

content = content.replace("""        if (repo == "duplicate-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("src/main.kt", "100644", "blob", "main-sha", 100, "url"),
                GitHubTreeItem("src//main.kt", "100644", "blob", "main-sha", 100, "url")
            ))
        }""", """        if (repo == "duplicate-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("src/main.kt", "100644", "blob", "main-sha", 100, "url"),
                GitHubTreeItem("src//main.kt", "100644", "blob", "main-sha", 100, "url")
            ), false)
        }""")

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
