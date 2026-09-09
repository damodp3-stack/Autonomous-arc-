with open('app/src/main/java/com/example/github/GitHubApi.kt', 'r') as f:
    content = f.read()

# Remove the previously appended part to do it cleanly
content = content.replace('''}

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
}''', '')

content = content.rstrip()
if content.endswith('}'):
    content = content[:-1]

content += '''

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
'''

with open('app/src/main/java/com/example/github/GitHubApi.kt', 'w') as f:
    f.write(content)
