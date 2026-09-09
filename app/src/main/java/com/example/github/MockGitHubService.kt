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


    override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree {
        kotlinx.coroutines.delay(100)
        
        if (repo == "truncated-repo") {
            if (treeSha == "root") {
                return GitHubTree("mock", "url", listOf(
                    GitHubTreeItem("src", "040000", "tree", "src-sha", null, "url")
                ), true)
            } else if (treeSha == "src-sha") {
                return GitHubTree("mock", "url", listOf(
                    GitHubTreeItem("main.kt", "100644", "blob", "main-sha", 100, "url")
                ), false)
            }
        }
        
        if (repo == "oversized-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("large.bin", "100644", "blob", "large-sha", 15 * 1024 * 1024, "url")
            ), false)
        }
        
        if (repo == "duplicate-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("src/main.kt", "100644", "blob", "main-sha", 100, "url"),
                GitHubTreeItem("src//main.kt", "100644", "blob", "main-sha", 100, "url")
            ), false)
        }

        // normal repository with nested directories and mixed files
        return GitHubTree(
            sha = "mock-tree-sha",
            url = "url",
            tree = listOf(
                GitHubTreeItem("README.md", "100644", "blob", "readme-sha", 100, "url"),
                GitHubTreeItem("src", "040000", "tree", "src-sha", null, "url"),
                GitHubTreeItem("src/main.kt", "100644", "blob", "main-sha", 150, "url"),
                GitHubTreeItem("assets", "040000", "tree", "assets-sha", null, "url"),
                GitHubTreeItem("assets/icon.png", "100644", "blob", "icon-sha", 200, "url")
            ),
            truncated = false
        )
    }

    override suspend fun getBlob(owner: String, repo: String, fileSha: String): GitHubBlob {
        kotlinx.coroutines.delay(100)
        when (fileSha) {
            "icon-sha" -> return GitHubBlob("iVBORw0KGgo=", "base64", "icon-sha", 200) // binary fake
            "main-sha" -> return GitHubBlob("cHJpbnRsbigiaGVsbG8iKQ==", "base64", "main-sha", 150)
            "readme-sha" -> return GitHubBlob("IyBNb2NrIFJlcG8KCk1vY2sgY29udGVudA==", "base64", "readme-sha", 100)
            "large-sha" -> return GitHubBlob("", "base64", "large-sha", 15 * 1024 * 1024)
            else -> return GitHubBlob("ZGVmYXVsdA==", "base64", fileSha, 100)
        }
    }
}