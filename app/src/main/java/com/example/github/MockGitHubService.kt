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
        GitHubRepository(1, "normal-repo", "mock_user/normal-repo", false, "url", "desc", "main"),
        GitHubRepository(2, "truncated-repo", "mock_user/truncated-repo", false, "url", "desc", "main"),
        GitHubRepository(3, "oversized-repo", "mock_user/oversized-repo", false, "url", "desc", "main"),
        GitHubRepository(4, "duplicate-repo", "mock_user/duplicate-repo", false, "url", "desc", "main"),
        GitHubRepository(5, "collision-repo", "mock_user/collision-repo", false, "url", "desc", "main"),
        GitHubRepository(6, "invalid-path-repo", "mock_user/invalid-path-repo", false, "url", "desc", "main"),
        GitHubRepository(7, "too-many-files-repo", "mock_user/too-many-files-repo", false, "url", "desc", "main"),
        GitHubRepository(8, "oversized-total-repo", "mock_user/oversized-total-repo", false, "url", "desc", "main"),
        GitHubRepository(9, "deep-repo", "mock_user/deep-repo", false, "url", "desc", "main"),
        GitHubRepository(10, "download-failure-repo", "mock_user/download-failure-repo", false, "url", "desc", "main")
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
        authenticated = token.isNotBlank()
        return authenticated
    }

    override suspend fun logout() {
        authenticated = false
    }

    override suspend fun getUser(): GitHubUser? {
        return getAuthenticatedUser()
    }

    override suspend fun getRepositories(): List<GitHubRepository> {
        if (!authenticated) throw Exception("Not authenticated")
        return mockRepos
    }

    override suspend fun getBranches(owner: String, repo: String): List<GitHubBranch> {
        if (!authenticated) throw Exception("Not authenticated")
        return mockBranches
    }

    override suspend fun sync(projectId: String): SyncResult {
        return SyncResult.Success
    }

    override suspend fun pull(projectId: String): SyncResult {
        return SyncResult.Success
    }

    override suspend fun push(projectId: String, commitMessage: String): SyncResult {
        return SyncResult.Success
    }


    override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree {
        
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
        

        if (repo == "collision-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("src/main.kt", "100644", "blob", "main-sha", 100, "url"),
                GitHubTreeItem("src", "100644", "blob", "main-sha", 100, "url")
            ), false)
        }

        if (repo == "invalid-path-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("../secret", "100644", "blob", "main-sha", 100, "url")
            ), false)
        }

        if (repo == "too-many-files-repo") {
            val list = mutableListOf<GitHubTreeItem>()
            for (i in 1..2001) {
                list.add(GitHubTreeItem("file$i.txt", "100644", "blob", "main-sha", 10, "url"))
            }
            return GitHubTree("mock", "url", list, false)
        }

        if (repo == "oversized-total-repo") {
            val list = mutableListOf<GitHubTreeItem>()
            for (i in 1..11) {
                list.add(GitHubTreeItem("large$i.bin", "100644", "blob", "main-sha", 10 * 1024 * 1024, "url"))
            }
            return GitHubTree("mock", "url", list, false)
        }

        if (repo == "deep-repo") {
            if (treeSha.startsWith("depth-")) {
                val d = treeSha.split("-")[1].toInt()
                if (d > 22) {
                    return GitHubTree("mock", "url", listOf(GitHubTreeItem("file", "100644", "blob", "main-sha", 10, "url")), false)
                }
                return GitHubTree("mock", "url", listOf(
                    GitHubTreeItem("dir$d", "040000", "tree", "depth-${d+1}", null, "url")
                ), true)
            }
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("dir0", "040000", "tree", "depth-1", null, "url")
            ), true)
        }

        if (repo == "download-failure-repo") {
            return GitHubTree("mock", "url", listOf(
                GitHubTreeItem("fail.txt", "100644", "blob", "fail-sha", 100, "url")
            ), false)
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

        if (fileSha == "fail-sha") {
            throw Exception("Network download failure")
        }
        when (fileSha) {
            "icon-sha" -> return GitHubBlob("iVBORw0KGgo=", "base64", "icon-sha", 200) // binary fake
            "main-sha" -> return GitHubBlob("cHJpbnRsbigiaGVsbG8iKQ==", "base64", "main-sha", 150)
            "readme-sha" -> return GitHubBlob("IyBNb2NrIFJlcG8KCk1vY2sgY29udGVudA==", "base64", "readme-sha", 100)
            "large-sha" -> return GitHubBlob("", "base64", "large-sha", 15 * 1024 * 1024)
            else -> return GitHubBlob("ZGVmYXVsdA==", "base64", fileSha, 100)
        }
    }
}