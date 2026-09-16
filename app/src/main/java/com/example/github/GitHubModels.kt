package com.example.github

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val id: Long,
    @Json(name = "avatar_url") val avatarUrl: String?,
    val name: String?
)

@JsonClass(generateAdapter = true)
data class GitHubRepository(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val private: Boolean,
    @Json(name = "html_url") val htmlUrl: String,
    val description: String?,
    @Json(name = "default_branch") val defaultBranch: String
)

@JsonClass(generateAdapter = true)
data class GitHubBranch(
    val name: String,
    val commit: GitHubCommitBase
)

@JsonClass(generateAdapter = true)
data class GitHubCommitBase(
    val sha: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class GitHubTree(
    val sha: String,
    val url: String,
    val tree: List<GitHubTreeItem>,
    val truncated: Boolean
)

@JsonClass(generateAdapter = true)
data class GitHubTreeItem(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
    val size: Long? = null,
    val url: String
)

@JsonClass(generateAdapter = true)
data class GitHubBlob(
    val content: String,
    val encoding: String,
    val sha: String,
    val size: Long
)

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

enum class ChangeType { ADDED, MODIFIED, DELETED, UNCHANGED }

data class FileChange(
    val path: String,
    val changeType: ChangeType,
    val isBinary: Boolean,
    val size: Long,
    val contentBytes: ByteArray? = null,
    val remoteSha: String? = null
)

data class CommitSummary(
    val changes: List<FileChange>,
    val additions: Int,
    val modifications: Int,
    val deletions: Int,
    val totalChangedSize: Long
)
