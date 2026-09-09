with open('app/src/main/java/com/example/github/GitHubInterfaces.kt', 'r') as f:
    content = f.read()

replacement = """    suspend fun getBranches(owner: String, repo: String): List<GitHubBranch>
    suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree
    suspend fun getBlob(owner: String, repo: String, fileSha: String): GitHubBlob
}"""

content = content.replace("    suspend fun getBranches(owner: String, repo: String): List<GitHubBranch>\n}", replacement)

with open('app/src/main/java/com/example/github/GitHubInterfaces.kt', 'w') as f:
    f.write(content)
