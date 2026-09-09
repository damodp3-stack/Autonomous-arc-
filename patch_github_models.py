with open('app/src/main/java/com/example/github/GitHubModels.kt', 'r') as f:
    content = f.read()

models = """
@JsonClass(generateAdapter = true)
data class GitHubCreateBlobRequest(
    val content: String,
    val encoding: String
)

@JsonClass(generateAdapter = true)
data class GitHubCreateBlobResponse(
    val sha: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class GitHubCreateTreeRequest(
    @Json(name = "base_tree") val baseTree: String,
    val tree: List<GitHubCreateTreeItem>
)

@JsonClass(generateAdapter = true)
data class GitHubCreateTreeItem(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String?,
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCreateTreeResponse(
    val sha: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class GitHubCreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String>
)

@JsonClass(generateAdapter = true)
data class GitHubCreateCommitResponse(
    val sha: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class GitHubUpdateRefRequest(
    val sha: String,
    val force: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitHubRef(
    val ref: String,
    val url: String,
    val `object`: GitHubRefObject
)

@JsonClass(generateAdapter = true)
data class GitHubRefObject(
    val sha: String,
    val type: String,
    val url: String
)
"""

if "GitHubCreateBlobRequest" not in content:
    with open('app/src/main/java/com/example/github/GitHubModels.kt', 'a') as f:
        f.write(models)

