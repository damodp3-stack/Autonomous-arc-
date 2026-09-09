package com.example.github

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Header

interface GitHubApi {
    @GET("user")
    suspend fun getUser(@Header("Authorization") auth: String): GitHubUser

    @GET("user/repos?sort=updated")
    suspend fun getRepositories(@Header("Authorization") auth: String): List<GitHubRepository>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubBranch>
}
