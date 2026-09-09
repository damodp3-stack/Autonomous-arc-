package com.example.github

/**
 * Service to interact with the GitHub API.
 */
interface GitHubService {
    suspend fun getUser(): GitHubUser?
    suspend fun getRepositories(): List<GitHubRepository>
    suspend fun getBranches(owner: String, repo: String): List<GitHubBranch>
    suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree
    suspend fun getBlob(owner: String, repo: String, fileSha: String): GitHubBlob
    suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef
    suspend fun createBlob(owner: String, repo: String, request: GitHubCreateBlobRequest): GitHubCreateBlobResponse
    suspend fun createTree(owner: String, repo: String, request: GitHubCreateTreeRequest): GitHubCreateTreeResponse
    suspend fun createCommit(owner: String, repo: String, request: GitHubCreateCommitRequest): GitHubCreateCommitResponse
    suspend fun updateRef(owner: String, repo: String, branch: String, request: GitHubUpdateRefRequest): GitHubRef
}

/**
 * Service to handle GitHub authentication.
 */
interface GitHubAuthService {
    suspend fun isAuthenticated(): Boolean
    suspend fun getAuthenticatedUser(): GitHubUser?
    suspend fun authenticate(token: String): Boolean
    suspend fun logout()
}

/**
 * Service to handle synchronization between GitHub and the local project filesystem.
 */
interface GitHubSyncService {
    suspend fun sync(projectId: String): SyncResult
    suspend fun pull(projectId: String): SyncResult
    suspend fun push(projectId: String, commitMessage: String): SyncResult
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}
