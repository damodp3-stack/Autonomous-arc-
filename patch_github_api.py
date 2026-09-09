with open('app/src/main/java/com/example/github/GitHubApi.kt', 'r') as f:
    content = f.read()

import re

endpoints = """
    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getRef(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): GitHubRef

    @retrofit2.http.POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @retrofit2.http.Body body: GitHubCreateBlobRequest
    ): GitHubCreateBlobResponse

    @retrofit2.http.POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @retrofit2.http.Body body: GitHubCreateTreeRequest
    ): GitHubCreateTreeResponse

    @retrofit2.http.POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @retrofit2.http.Body body: GitHubCreateCommitRequest
    ): GitHubCreateCommitResponse

    @retrofit2.http.PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @retrofit2.http.Body body: GitHubUpdateRefRequest
    ): GitHubRef
}"""

content = re.sub(r'\}[\s]*$', endpoints, content)

with open('app/src/main/java/com/example/github/GitHubApi.kt', 'w') as f:
    f.write(content)

