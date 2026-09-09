package com.example.github

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Header

interface GitHubApi {
    @GET("user")
    suspend fun getUser(@Header("Authorization") auth: String): GitHubUser

    @GET("user/repos?sort=updated&per_page=100")
    suspend fun getRepositories(@Header("Authorization") auth: String): List<GitHubRepository>

    @GET("repos/{owner}/{repo}/branches?per_page=100")
    suspend fun getBranches(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubBranch>
}
