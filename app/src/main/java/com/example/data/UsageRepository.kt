package com.example.data

import com.example.ai.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

interface UsageRepository {
    fun getAllUsage(): Flow<List<UsageRecordEntity>>
    fun getRecentUsage(limit: Int = 50): Flow<List<UsageRecordEntity>>
    fun getUsageSummary(timeRange: UsageTimeRange = UsageTimeRange.ALL_TIME): Flow<UsageSummary>
    suspend fun recordUsage(
        provider: String,
        model: String,
        tokens: TokenUsage?,
        status: String = "SUCCESS",
        error: String? = null,
        projectId: String? = null
    ): UsageRecordEntity
    suspend fun clearHistory()
}

class LocalUsageRepository(
    private val usageDao: UsageDao
) : UsageRepository {

    override fun getAllUsage(): Flow<List<UsageRecordEntity>> = usageDao.getAllUsage()

    override fun getRecentUsage(limit: Int): Flow<List<UsageRecordEntity>> =
        usageDao.getRecentUsage(limit)

    override fun getUsageSummary(timeRange: UsageTimeRange): Flow<UsageSummary> {
        val sinceTimestamp = calculateSinceTimestamp(timeRange)
        val sourceFlow = if (sinceTimestamp > 0L) {
            usageDao.getUsageSince(sinceTimestamp)
        } else {
            usageDao.getAllUsage()
        }

        return sourceFlow.map { records ->
            aggregateRecords(records)
        }
    }

    override suspend fun recordUsage(
        provider: String,
        model: String,
        tokens: TokenUsage?,
        status: String,
        error: String?,
        projectId: String?
    ): UsageRecordEntity {
        val promptTokens = tokens?.promptTokens ?: 0
        val completionTokens = tokens?.completionTokens ?: 0
        val totalTokens = tokens?.totalTokens ?: (promptTokens + completionTokens)

        val record = UsageRecordEntity(
            provider = provider,
            model = model,
            timestamp = System.currentTimeMillis(),
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            requestStatus = status,
            errorInfo = error?.take(300), // Protect against large or sensitive error dumps
            projectId = projectId
        )
        usageDao.insert(record)
        return record
    }

    override suspend fun clearHistory() {
        usageDao.clearAll()
    }

    private fun calculateSinceTimestamp(timeRange: UsageTimeRange): Long {
        if (timeRange == UsageTimeRange.ALL_TIME) return 0L
        val calendar = Calendar.getInstance()
        when (timeRange) {
            UsageTimeRange.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return calendar.timeInMillis
            }
            UsageTimeRange.LAST_7_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                return calendar.timeInMillis
            }
            UsageTimeRange.LAST_30_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                return calendar.timeInMillis
            }
            UsageTimeRange.ALL_TIME -> return 0L
        }
    }

    companion object {
        fun aggregateRecords(records: List<UsageRecordEntity>): UsageSummary {
            var totalPrompt = 0
            var totalCompletion = 0
            var totalTokens = 0
            var successCount = 0
            var errorCount = 0

            val providerMap = mutableMapOf<String, ProviderAggregator>()
            val modelMap = mutableMapOf<String, ModelAggregator>()

            for (record in records) {
                totalPrompt += record.promptTokens
                totalCompletion += record.completionTokens
                totalTokens += record.totalTokens

                val isSuccess = record.requestStatus.equals("SUCCESS", ignoreCase = true)
                if (isSuccess) successCount++ else errorCount++

                val pAgg = providerMap.getOrPut(record.provider) {
                    ProviderAggregator(record.provider)
                }
                pAgg.requests++
                pAgg.tokens += record.totalTokens
                if (isSuccess) pAgg.successes++ else pAgg.failures++

                val mKey = "${record.provider}:${record.model}"
                val mAgg = modelMap.getOrPut(mKey) {
                    ModelAggregator(record.model, record.provider)
                }
                mAgg.requests++
                mAgg.tokens += record.totalTokens
            }

            val providerBreakdown = providerMap.values.map {
                ProviderUsageSummary(
                    provider = it.provider,
                    requestCount = it.requests,
                    totalTokens = it.tokens,
                    successfulCount = it.successes,
                    failedCount = it.failures
                )
            }.sortedByDescending { it.requestCount }

            val modelBreakdown = modelMap.values.map {
                ModelUsageSummary(
                    model = it.model,
                    provider = it.provider,
                    requestCount = it.requests,
                    totalTokens = it.tokens
                )
            }.sortedByDescending { it.requestCount }

            return UsageSummary(
                totalRequests = records.size,
                successfulRequests = successCount,
                failedRequests = errorCount,
                totalPromptTokens = totalPrompt,
                totalCompletionTokens = totalCompletion,
                totalTokens = totalTokens,
                providerBreakdown = providerBreakdown,
                modelBreakdown = modelBreakdown
            )
        }
    }

    private class ProviderAggregator(val provider: String) {
        var requests = 0
        var tokens = 0
        var successes = 0
        var failures = 0
    }

    private class ModelAggregator(val model: String, val provider: String) {
        var requests = 0
        var tokens = 0
    }
}
