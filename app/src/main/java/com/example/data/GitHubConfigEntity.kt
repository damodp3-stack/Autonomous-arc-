package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "github_configs")
data class GitHubConfigEntity(
    @PrimaryKey val projectId: String,
    val owner: String,
    val repository: String,
    val branch: String,
    val isConnected: Boolean,
    val lastRemoteSha: String? = null,
    val lastSyncAt: Long? = null
)
