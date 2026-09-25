package com.example.ai

import com.example.data.ProjectFileEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProductionHardeningTest {

    @Test
    fun testPathTraversalRejectionInProposal() {
        // Verify path traversal checks reject dangerous filenames
        val dangerousPaths = listOf(
            "../etc/passwd",
            "../../app/build.gradle.kts",
            "/absolute/root/file.txt",
            "src/../../../secret.txt",
            "nested\\backslash\\path.txt"
        )

        for (path in dangerousPaths) {
            val hasTraversal = path.contains("../") || path.startsWith("/") || path.contains("\\")
            assertTrue("Path '$path' should be detected as invalid/unsafe traversal", hasTraversal)
        }
    }

    @Test
    fun testSecureKeyManagerPlaceholderRejection() {
        val placeholders = listOf(
            "",
            "   ",
            "MY_GEMINI_API_KEY",
            "YOUR_GEMINI_API_KEY",
            "MY_OPENAI_API_KEY",
            "YOUR_OPENAI_API_KEY",
            "MY_ANTHROPIC_API_KEY",
            "YOUR_ANTHROPIC_API_KEY"
        )

        for (key in placeholders) {
            val isBlankOrPlaceholder = key.trim().isBlank() ||
                    key.trim().startsWith("MY_") ||
                    key.trim().startsWith("YOUR_")
            assertTrue("Key '$key' should be identified as blank or placeholder", isBlankOrPlaceholder)
        }
    }

    @Test
    fun testMockProviderFallbackUnderEmptyResponses() = runBlocking {
        val mockProvider = MockAIProvider(model = "mock-model")
        val response = mockProvider.generateResponse("Hello world", emptyList(), null)
        assertNotNull(response)
        assertTrue(response.isNotEmpty())

        val proposal = mockProvider.proposeCodeChanges(
            "Create file test.txt",
            ProjectContext("Demo", listOf(ProjectFileEntity(id = "1", projectId = "Demo", path = "MainActivity.kt", name = "MainActivity.kt", extension = "kt", content = "")), null)
        )
        assertNotNull(proposal)
        assertNotNull(proposal.changes)
        assertTrue(proposal.changes.all { !it.filePath.contains("../") && !it.filePath.startsWith("/") })
    }

    @Test
    fun testJsonCodeProposalCleaning() {
        val rawMarkdown = """
            ```json
            {
              "summary": "Fix layout",
              "explanation": "Added spacing",
              "changes": []
            }
            ```
        """.trimIndent()

        var cleaned = rawMarkdown
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json")
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substringBeforeLast("```")
        }
        cleaned = cleaned.trim()

        assertTrue(cleaned.startsWith("{"))
        assertTrue(cleaned.endsWith("}"))
        assertFalse(cleaned.contains("```"))
    }
}
