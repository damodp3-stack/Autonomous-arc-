with open('app/src/main/java/com/example/github/GitHubInterfaces.kt', 'r') as f:
    content = f.read()

import re

new_methods = """    suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef
    suspend fun createBlob(owner: String, repo: String, request: GitHubCreateBlobRequest): GitHubCreateBlobResponse
    suspend fun createTree(owner: String, repo: String, request: GitHubCreateTreeRequest): GitHubCreateTreeResponse
    suspend fun createCommit(owner: String, repo: String, request: GitHubCreateCommitRequest): GitHubCreateCommitResponse
    suspend fun updateRef(owner: String, repo: String, branch: String, request: GitHubUpdateRefRequest): GitHubRef
}"""

content = re.sub(r'    suspend fun getBlob\(owner: String, repo: String, fileSha: String\): GitHubBlob\n\}', '    suspend fun getBlob(owner: String, repo: String, fileSha: String): GitHubBlob\n' + new_methods, content)

with open('app/src/main/java/com/example/github/GitHubInterfaces.kt', 'w') as f:
    f.write(content)


with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'r') as f:
    content = f.read()

new_real_methods = """    override suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef {
        return api.getRef(getAuthHeader(), owner, repo, branch)
    }

    override suspend fun createBlob(owner: String, repo: String, request: GitHubCreateBlobRequest): GitHubCreateBlobResponse {
        return api.createBlob(getAuthHeader(), owner, repo, request)
    }

    override suspend fun createTree(owner: String, repo: String, request: GitHubCreateTreeRequest): GitHubCreateTreeResponse {
        return api.createTree(getAuthHeader(), owner, repo, request)
    }

    override suspend fun createCommit(owner: String, repo: String, request: GitHubCreateCommitRequest): GitHubCreateCommitResponse {
        return api.createCommit(getAuthHeader(), owner, repo, request)
    }

    override suspend fun updateRef(owner: String, repo: String, branch: String, request: GitHubUpdateRefRequest): GitHubRef {
        return api.updateRef(getAuthHeader(), owner, repo, branch, request)
    }

    override suspend fun sync"""

content = re.sub(r'    override suspend fun sync', new_real_methods, content)

with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'w') as f:
    f.write(content)

