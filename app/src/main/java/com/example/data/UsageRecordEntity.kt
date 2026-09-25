package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "usage_records")
data class UsageRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val provider: String,
    val model: String,
    val timestamp: Long = System.currentTimeMillis(),
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val requestStatus: String = "SUCCESS", // "SUCCESS" or "ERROR"
    val errorInfo: String? = null,
    val projectId: String? = null
)

data class UsageSummary(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val providerBreakdown: List<ProviderUsageSummary> = emptyList(),
    val modelBreakdown: List<ModelUsageSummary> = emptyList()
)

data class ProviderUsageSummary(
    val provider: String,
    val requestCount: Int,
    val totalTokens: Int,
    val successfulCount: Int,
    val failedCount: Int
)

data class ModelUsageSummary(
    val model: String,
    val provider: String,
    val requestCount: Int,
    val totalTokens: Int
)

enum class UsageTimeRange(val label: String) {
    ALL_TIME("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days")
}
