with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

# GitHubTree(sha, url, tree, truncated)

content = content.replace('GitHubTree("mock", "url", true, listOf(', 'GitHubTree("mock", "url", listOf(')
content = content.replace('))            } else if (treeSha == "src-sha") {', '), true)            } else if (treeSha == "src-sha") {')

content = content.replace('GitHubTree("mock", "url", false, listOf(', 'GitHubTree("mock", "url", listOf(')
content = content.replace('))            }', '), false)            }')

content = content.replace('GitHubTree(\n            sha = "mock-tree-sha",\n            url = "url",\n            truncated = false,\n            tree = listOf(', 'GitHubTree(\n            sha = "mock-tree-sha",\n            url = "url",\n            tree = listOf(')
content = content.replace(')\n        )\n    }', '),\n            truncated = false\n        )\n    }')

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
