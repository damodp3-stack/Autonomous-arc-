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
