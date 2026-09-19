package com.example.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FallbackAIProviderTest {

    private class FakeTestAIProvider(
        val name: String,
        var shouldFail: Boolean = false,
        var responseText: String = "Success from fake",
        var usage: TokenUsage? = TokenUsage(100, 50, 150)
    ) : AIProvider {
        override suspend fun generateResponse(
            prompt: String,
            context: List<com.example.data.MessageEntity>,
            projectContext: ProjectContext?
        ): String {
            if (shouldFail) throw RuntimeException("Simulated failure in $name")
            return responseText
        }

        override suspend fun proposeCodeChanges(
            request: String,
            projectContext: ProjectContext
        ): CodeChangeProposal {
            if (shouldFail) throw RuntimeException("Simulated failure in $name")
            return CodeChangeProposal(
                summary = "Proposal from $name",
                explanation = "Explanation",
                changes = emptyList(),
                tokenUsage = usage
            )
        }

        override fun getLatestUsage(): TokenUsage? = usage
    }

    @Test
    fun testPrimarySucceeds() = runBlocking {
        val primary = FakeTestAIProvider("primary", shouldFail = false, responseText = "Primary Response")
        val fallback = FakeTestAIProvider("fallback", shouldFail = false, responseText = "Fallback Response")
        val provider = FallbackAIProvider(primary, fallback)

        val context = ProjectContext("TestProject", emptyList(), null)
        val response = provider.generateResponse("Hello", emptyList(), context)

        assertEquals("Primary Response", response)
        assertEquals(150, provider.getLatestUsage()?.totalTokens)
    }

    @Test
    fun testPrimaryFailsFallbackSucceeds() = runBlocking {
        val primary = FakeTestAIProvider("primary", shouldFail = true)
        val fallback = FakeTestAIProvider("fallback", shouldFail = false, responseText = "Fallback Responded")
        val provider = FallbackAIProvider(primary, fallback)

        val context = ProjectContext("TestProject", emptyList(), null)
        val response = provider.generateResponse("Hello", emptyList(), context)

        assertEquals("Fallback Responded", response)
    }

    @Test
    fun testBothFailThrowsException() = runBlocking {
        val primary = FakeTestAIProvider("primary", shouldFail = true)
        val fallback = FakeTestAIProvider("fallback", shouldFail = true)
        val provider = FallbackAIProvider(primary, fallback)

        val context = ProjectContext("TestProject", emptyList(), null)
        try {
            provider.generateResponse("Hello", emptyList(), context)
            fail("Expected exception when both providers fail")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Simulated failure") == true)
        }
    }

    @Test
    fun testProposeCodeChangesFallback() = runBlocking {
        val primary = FakeTestAIProvider("primary", shouldFail = true)
        val fallback = FakeTestAIProvider("fallback", shouldFail = false)
        val provider = FallbackAIProvider(primary, fallback)

        val context = ProjectContext("TestProject", emptyList(), null)
        val proposal = provider.proposeCodeChanges("Add feature", context)

        assertEquals("Proposal from fallback", proposal.summary)
    }
}
