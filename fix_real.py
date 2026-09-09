with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'r') as f:
    content = f.read()

replacement = """    override suspend fun getBranches(owner: String, repo: String): List<GitHubBranch> {
        return api.getBranches(getAuthHeader(), owner, repo)
    }

    override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree {
        return api.getTree(getAuthHeader(), owner, repo, treeSha)
    }

    override suspend fun getBlob(owner: String, repo: String, fileSha: String): GitHubBlob {
        return api.getBlob(getAuthHeader(), owner, repo, fileSha)
    }"""

content = content.replace("""    override suspend fun getBranches(owner: String, repo: String): List<GitHubBranch> {
        return api.getBranches(getAuthHeader(), owner, repo)
    }""", replacement)

with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'w') as f:
    f.write(content)
