package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.TokenUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsageAnalyticsTest {

    private lateinit var db: AppDatabase
    private lateinit var usageDao: UsageDao
    private lateinit var repository: LocalUsageRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        usageDao = db.usageDao()
        repository = LocalUsageRepository(usageDao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `test usage persistence`() = runBlocking {
        val tokens = TokenUsage(promptTokens = 120, completionTokens = 80, totalTokens = 200)
        val record = repository.recordUsage(
            provider = "OpenAI",
            model = "gpt-4o",
            tokens = tokens,
            status = "SUCCESS",
            projectId = "proj-1"
        )

        val allRecords = repository.getAllUsage().first()
        assertEquals(1, allRecords.size)
        assertEquals(record.id, allRecords[0].id)
        assertEquals("OpenAI", allRecords[0].provider)
        assertEquals("gpt-4o", allRecords[0].model)
        assertEquals(120, allRecords[0].promptTokens)
        assertEquals(80, allRecords[0].completionTokens)
        assertEquals(200, allRecords[0].totalTokens)
        assertEquals("SUCCESS", allRecords[0].requestStatus)
        assertEquals("proj-1", allRecords[0].projectId)
    }

    @Test
    fun `test totals calculation`() = runBlocking {
        repository.recordUsage(
            provider = "Gemini",
            model = "gemini-1.5-pro",
            tokens = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150),
            status = "SUCCESS"
        )
        repository.recordUsage(
            provider = "Anthropic",
            model = "claude-3-5-sonnet",
            tokens = TokenUsage(promptTokens = 200, completionTokens = 100, totalTokens = 300),
            status = "SUCCESS"
        )

        val summary = repository.getUsageSummary(UsageTimeRange.ALL_TIME).first()
        assertEquals(2, summary.totalRequests)
        assertEquals(2, summary.successfulRequests)
        assertEquals(0, summary.failedRequests)
        assertEquals(300, summary.totalPromptTokens)
        assertEquals(150, summary.totalCompletionTokens)
        assertEquals(450, summary.totalTokens)
    }

    @Test
    fun `test provider aggregation`() = runBlocking {
        repository.recordUsage("OpenAI", "gpt-4o", TokenUsage(50, 50, 100), "SUCCESS")
        repository.recordUsage("OpenAI", "gpt-4o-mini", TokenUsage(20, 20, 40), "SUCCESS")
        repository.recordUsage("Gemini", "gemini-1.5-flash", TokenUsage(30, 30, 60), "SUCCESS")

        val summary = repository.getUsageSummary(UsageTimeRange.ALL_TIME).first()
        assertEquals(2, summary.providerBreakdown.size)

        val openAISummary = summary.providerBreakdown.first { it.provider == "OpenAI" }
        assertEquals(2, openAISummary.requestCount)
        assertEquals(140, openAISummary.totalTokens)

        val geminiSummary = summary.providerBreakdown.first { it.provider == "Gemini" }
        assertEquals(1, geminiSummary.requestCount)
        assertEquals(60, geminiSummary.totalTokens)
    }

    @Test
    fun `test model aggregation`() = runBlocking {
        repository.recordUsage("OpenAI", "gpt-4o", TokenUsage(100, 100, 200), "SUCCESS")
        repository.recordUsage("OpenAI", "gpt-4o", TokenUsage(50, 50, 100), "SUCCESS")
        repository.recordUsage("Anthropic", "claude-3-5-sonnet", TokenUsage(80, 20, 100), "SUCCESS")

        val summary = repository.getUsageSummary(UsageTimeRange.ALL_TIME).first()
        assertEquals(2, summary.modelBreakdown.size)

        val gpt4oModel = summary.modelBreakdown.first { it.model == "gpt-4o" }
        assertEquals(2, gpt4oModel.requestCount)
        assertEquals(300, gpt4oModel.totalTokens)
        assertEquals("OpenAI", gpt4oModel.provider)
    }

    @Test
    fun `test failed request recording`() = runBlocking {
        repository.recordUsage(
            provider = "Anthropic",
            model = "claude-3-5-sonnet",
            tokens = null,
            status = "ERROR",
            error = "HTTP 429 Too Many Requests"
        )

        val summary = repository.getUsageSummary(UsageTimeRange.ALL_TIME).first()
        assertEquals(1, summary.totalRequests)
        assertEquals(0, summary.successfulRequests)
        assertEquals(1, summary.failedRequests)
        assertEquals(0, summary.totalTokens)

        val recent = repository.getRecentUsage(10).first()
        assertEquals(1, recent.size)
        assertEquals("ERROR", recent[0].requestStatus)
        assertEquals("HTTP 429 Too Many Requests", recent[0].errorInfo)
    }
}
