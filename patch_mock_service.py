with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

import re

new_methods = """
    var lastCreatedTree: GitHubCreateTreeRequest? = null
    var lastCreatedCommit: GitHubCreateCommitRequest? = null
    var lastUpdatedRef: GitHubUpdateRefRequest? = null
    var failNextRefUpdate = false
    var blobCounter = 0
    var currentRefSha = "initial-sha"

    override suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef {
        kotlinx.coroutines.delay(100)
        return GitHubRef("refs/heads/$branch", "url", GitHubRefObject(currentRefSha, "commit", "url"))
    }

    override suspend fun createBlob(owner: String, repo: String, request: GitHubCreateBlobRequest): GitHubCreateBlobResponse {
        kotlinx.coroutines.delay(100)
        if (request.content == "fail-blob") throw Exception("Blob creation failed")
        blobCounter++
        return GitHubCreateBlobResponse("blob-sha-$blobCounter", "url")
    }

    override suspend fun createTree(owner: String, repo: String, request: GitHubCreateTreeRequest): GitHubCreateTreeResponse {
        kotlinx.coroutines.delay(100)
        if (request.tree.any { it.path == "fail-tree" }) throw Exception("Tree creation failed")
        lastCreatedTree = request
        return GitHubCreateTreeResponse("new-tree-sha", "url")
    }

    override suspend fun createCommit(owner: String, repo: String, request: GitHubCreateCommitRequest): GitHubCreateCommitResponse {
        kotlinx.coroutines.delay(100)
        if (request.message == "fail-commit") throw Exception("Commit creation failed")
        lastCreatedCommit = request
        return GitHubCreateCommitResponse("new-commit-sha", "url")
    }

    override suspend fun updateRef(owner: String, repo: String, branch: String, request: GitHubUpdateRefRequest): GitHubRef {
        kotlinx.coroutines.delay(100)
        if (failNextRefUpdate) {
            failNextRefUpdate = false
            throw Exception("Ref update failed")
        }
        lastUpdatedRef = request
        currentRefSha = request.sha
        return GitHubRef("refs/heads/$branch", "url", GitHubRefObject(request.sha, "commit", "url"))
    }
"""

content = re.sub(r'    override suspend fun sync\(projectId: String\)', new_methods + '\n    override suspend fun sync(projectId: String)', content)

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
