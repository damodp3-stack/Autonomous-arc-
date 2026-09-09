with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

content = content.replace("""                return GitHubTree("mock", "url", listOf(
                    GitHubTreeItem("src", "040000", "tree", "src-sha", null, "url")
                ))""", """                return GitHubTree("mock", "url", listOf(
                    GitHubTreeItem("src", "040000", "tree", "src-sha", null, "url")
                ), true)""")

content = content.replace("""                return GitHubTree("mock", "url", listOf(
                    GitHubTreeItem("main.kt", "100644", "blob", "main-sha", 100, "url")
                ))""", """                return GitHubTree("mock", "url", listOf(
                    GitHubTreeItem("main.kt", "100644", "blob", "main-sha", 100, "url")
                ), false)""")

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
