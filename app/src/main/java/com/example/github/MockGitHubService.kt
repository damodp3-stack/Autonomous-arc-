package com.example.github

import kotlinx.coroutines.delay

class MockGitHubService : GitHubService, GitHubAuthService, GitHubSyncService {
    private var authenticated = false
    private val mockUser = GitHubUser(
        login = "mock_user",
        id = 12345L,
        avatarUrl = null,
        name = "Mock User"
    )

    private val mockRepos = listOf(
        GitHubRepository(
            id = 1,
            name = "autonomous-arc",
            fullName = "mock_user/autonomous-arc",
            private = false,
            htmlUrl = "https://github.com/mock_user/autonomous-arc",
            description = "Mock repo",
            defaultBranch = "main"
        ),
        GitHubRepository(
            id = 2,
            name = "test-repo",
            fullName = "mock_user/test-repo",
            private = true,
            htmlUrl = "https://github.com/mock_user/test-repo",
            description = "Another mock repo",
            defaultBranch = "master"
        )
    )

    private val mockBranches = listOf(
        GitHubBranch(name = "main", commit = GitHubCommitBase(sha = "abcdef123456", url = "")),
        GitHubBranch(name = "dev", commit = GitHubCommitBase(sha = "123456abcdef", url = ""))
    )

    override suspend fun isAuthenticated(): Boolean = authenticated

    override suspend fun getAuthenticatedUser(): GitHubUser? {
        return if (authenticated) mockUser else null
    }

    override suspend fun authenticate(token: String): Boolean {
        delay(500)
        authenticated = token.isNotBlank()
        return authenticated
    }

    override suspend fun logout() {
        authenticated = false
    }

    override suspend fun getUser(): GitHubUser? {
        delay(300)
        return getAuthenticatedUser()
    }

    override suspend fun getRepositories(): List<GitHubRepository> {
        delay(500)
        if (!authenticated) throw Exception("Not authenticated")
        return mockRepos
    }

    override suspend fun getBranches(owner: String, repo: String): List<GitHubBranch> {
        delay(500)
        if (!authenticated) throw Exception("Not authenticated")
        return mockBranches
    }

    override suspend fun sync(projectId: String): SyncResult {
        delay(1000)
        return SyncResult.Success
    }

    override suspend fun pull(projectId: String): SyncResult {
        delay(1000)
        return SyncResult.Success
    }

    override suspend fun push(projectId: String, commitMessage: String): SyncResult {
        delay(1000)
        return SyncResult.Success
    }
}
