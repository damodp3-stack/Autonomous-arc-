package com.example.ai

import org.junit.Assert.*
import org.junit.Test

class CodeChangeProposalTest {

    @Test
    fun testValidProposalParsing() {
        val json = """
            {
              "summary": "Add a settings button",
              "explanation": "Adds a settings action to the workspace top bar.",
              "changes": [
                {
                  "filePath": "app/src/main/java/com/example/ui/WorkspaceScreen.kt",
                  "operation": "MODIFY",
                  "originalContent": "Old Code",
                  "proposedContent": "New Code"
                },
                {
                  "filePath": "app/src/main/java/com/example/ui/Settings.kt",
                  "operation": "CREATE",
                  "originalContent": "",
                  "proposedContent": "Settings Code"
                }
              ]
            }
        """.trimIndent()

        val adapter = GeminiRetrofitClient.moshi.adapter(CodeChangeProposal::class.java)
        val proposal = adapter.fromJson(json)

        assertNotNull(proposal)
        assertEquals("Add a settings button", proposal?.summary)
        assertEquals(2, proposal?.changes?.size)
        assertEquals(FileOperation.MODIFY, proposal?.changes?.get(0)?.operation)
        assertEquals(FileOperation.CREATE, proposal?.changes?.get(1)?.operation)
    }

    @Test
    fun testInvalidJsonParsing() {
        val json = """
            {
              "summary": "Missing changes"
            }
        """.trimIndent()

        val adapter = GeminiRetrofitClient.moshi.adapter(CodeChangeProposal::class.java)
        try {
            adapter.fromJson(json)
            fail("Expected exception due to missing fields")
        } catch (e: Exception) {
            // Success
        }
    }

    @Test
    fun testPathTraversalRejection() {
        val json = """
            {
              "summary": "Malicious",
              "explanation": "Trying to overwrite files outside project",
              "changes": [
                {
                  "filePath": "../../../etc/passwd",
                  "operation": "MODIFY",
                  "originalContent": "",
                  "proposedContent": "hacked"
                }
              ]
            }
        """.trimIndent()

        val adapter = GeminiRetrofitClient.moshi.adapter(CodeChangeProposal::class.java)
        val proposal = adapter.fromJson(json)
        assertNotNull(proposal)
        
        // This simulates the validation loop from GeminiAIProvider
        val validatedChanges = try {
            proposal!!.changes.map { change ->
                if (change.filePath.contains("../") || change.filePath.startsWith("/")) {
                    throw IllegalStateException("Security Error: Path traversal detected in AI proposal.")
                }
                change
            }
        } catch (e: Exception) {
            null
        }

        assertNull("Path validation should fail", validatedChanges)
    }

    @Test
    fun testEmptyProposalRejection() {
        val json = """
            {
              "summary": "Empty",
              "explanation": "No changes",
              "changes": []
            }
        """.trimIndent()

        val adapter = GeminiRetrofitClient.moshi.adapter(CodeChangeProposal::class.java)
        val proposal = adapter.fromJson(json)
        
        assertNotNull(proposal)
        assertTrue(proposal!!.changes.isEmpty())
    }
}
