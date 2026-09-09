package com.example.github

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class RealGitHubServices(
    private val tokenManager: TokenManager
) : GitHubAuthService, GitHubService, GitHubSyncService {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(GitHubApi::class.java)

    private fun getAuthHeader(): String {
        val token = tokenManager.getToken() ?: throw Exception("Not authenticated")
        return "Bearer $token"
    }

    override suspend fun isAuthenticated(): Boolean {
        return tokenManager.getToken() != null
    }

    override suspend fun getAuthenticatedUser(): GitHubUser? {
        val token = tokenManager.getToken() ?: return null
        return try {
            api.getUser("Bearer $token")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun authenticate(token: String): Boolean {
        return try {
            val user = api.getUser("Bearer $token")
            tokenManager.saveToken(token)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun logout() {
        tokenManager.clearToken()
    }

    override suspend fun getUser(): GitHubUser? {
        return getAuthenticatedUser()
    }

    override suspend fun getRepositories(): List<GitHubRepository> {
        return api.getRepositories(getAuthHeader())
    }

    override suspend fun getBranches(owner: String, repo: String): List<GitHubBranch> {
        return api.getBranches(getAuthHeader(), owner, repo)
    }

    override suspend fun sync(projectId: String): SyncResult {
        // Foundation: We just return success for now as actual file sync is out of scope
        return SyncResult.Success
    }

    override suspend fun pull(projectId: String): SyncResult {
        return SyncResult.Success
    }

    override suspend fun push(projectId: String, commitMessage: String): SyncResult {
        return SyncResult.Success
    }
}
