package com.example.github

/**
 * Service to interact with the GitHub API.
 */
interface GitHubService {
    suspend fun getUser(): GitHubUser?
    suspend fun getRepositories(): List<GitHubRepository>
    suspend fun getBranches(owner: String, repo: String): List<GitHubBranch>
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
