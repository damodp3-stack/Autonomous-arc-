package com.example.ai

import com.example.data.ProjectFileEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BuildValidatorTest {

    private val validator = DefaultBuildValidator()

    @Test
    fun `test empty project files list returns unsupported`() = runBlocking {
        val result = validator.validateBuild("proj-1", emptyList())
        assertTrue(result is BuildValidationResult.Unsupported)
        assertTrue((result as BuildValidationResult.Unsupported).reason.contains("zero files"))
    }

    @Test
    fun `test empty source file returns failure`() = runBlocking {
        val files = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "src/Empty.kt", name = "Empty.kt", extension = "kt", content = "", isDirectory = false)
        )
        val result = validator.validateBuild("proj-1", files)
        assertTrue(result is BuildValidationResult.Failure)
        assertTrue((result as BuildValidationResult.Failure).errors.any { it.contains("empty or whitespace-only") })
    }

    @Test
    fun `test unbalanced closing brace returns failure`() = runBlocking {
        val files = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "src/Broken.kt", name = "Broken.kt", extension = "kt", content = "class Broken { fun test() { }", isDirectory = false),
            ProjectFileEntity(id = "2", projectId = "proj-1", path = "build.gradle.kts", name = "build.gradle.kts", extension = "kts", content = "plugins { }", isDirectory = false)
        )
        val result = validator.validateBuild("proj-1", files)
        assertTrue(result is BuildValidationResult.Failure)
        assertTrue((result as BuildValidationResult.Failure).errors.any { it.contains("Unclosed delimiter") })
    }

    @Test
    fun `test mismatched closing parenthesis returns failure`() = runBlocking {
        val files = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "src/Mismatch.kt", name = "Mismatch.kt", extension = "kt", content = "val x = (1 + 2}", isDirectory = false),
            ProjectFileEntity(id = "2", projectId = "proj-1", path = "build.gradle.kts", name = "build.gradle.kts", extension = "kts", content = "plugins { }", isDirectory = false)
        )
        val result = validator.validateBuild("proj-1", files)
        assertTrue(result is BuildValidationResult.Failure)
        assertTrue((result as BuildValidationResult.Failure).errors.any { it.contains("Unmatched closing brace") })
    }

    @Test
    fun `test strings and comments containing braces are ignored properly`() = runBlocking {
        val content = """
        // comment with unmatched { ( [
        /* block comment with unmatched { ( [ */
        val str = "string with { ( [ and \" escaped quotes"
        val raw = ""${'"'}
        raw string with { ( [
        ""${'"'}
        fun valid() {
            println("OK")
        }
        """.trimIndent()

        val files = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "src/Valid.kt", name = "Valid.kt", extension = "kt", content = content, isDirectory = false),
            ProjectFileEntity(id = "2", projectId = "proj-1", path = "build.gradle.kts", name = "build.gradle.kts", extension = "kts", content = "plugins { kotlin(\"jvm\") }", isDirectory = false)
        )
        val result = validator.validateBuild("proj-1", files)
        assertTrue(result is BuildValidationResult.Success)
    }

    @Test
    fun `test xml tag integrity validation`() = runBlocking {
        val validXml = "<resources><string name=\"app_name\">App</string></resources>"
        val files = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "res/values/strings.xml", name = "strings.xml", extension = "xml", content = validXml, isDirectory = false),
            ProjectFileEntity(id = "2", projectId = "proj-1", path = "build.gradle.kts", name = "build.gradle.kts", extension = "kts", content = "plugins { }", isDirectory = false)
        )
        val result = validator.validateBuild("proj-1", files)
        assertTrue(result is BuildValidationResult.Success)

        val invalidXml = "<resources><string name=\"app_name\">App"
        val brokenFiles = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "res/values/strings.xml", name = "strings.xml", extension = "xml", content = invalidXml, isDirectory = false),
            ProjectFileEntity(id = "2", projectId = "proj-1", path = "build.gradle.kts", name = "build.gradle.kts", extension = "kts", content = "plugins { }", isDirectory = false)
        )
        val brokenResult = validator.validateBuild("proj-1", brokenFiles)
        assertTrue(brokenResult is BuildValidationResult.Failure)
    }

    @Test
    fun `test unsupported build script detection`() = runBlocking {
        val files = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "src/Main.kt", name = "Main.kt", extension = "kt", content = "fun main() { println(\"hi\") }", isDirectory = false)
        )
        val result = validator.validateBuild("proj-1", files)
        assertTrue(result is BuildValidationResult.Unsupported)
        assertTrue((result as BuildValidationResult.Unsupported).reason.contains("lacks standard build scripts"))
    }
}
