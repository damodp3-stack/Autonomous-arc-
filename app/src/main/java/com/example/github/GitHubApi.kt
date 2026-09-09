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

    @GET("repos/{owner}/{repo}/git/trees/{tree_sha}?recursive=1")
    suspend fun getTree(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("tree_sha") treeSha: String
    ): GitHubTree

    @GET("repos/{owner}/{repo}/git/blobs/{file_sha}")
    suspend fun getBlob(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("file_sha") fileSha: String
    ): GitHubBlob
}
