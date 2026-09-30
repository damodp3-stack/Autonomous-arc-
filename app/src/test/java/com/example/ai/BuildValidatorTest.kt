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

    @Test
    fun `test unsupported project type detection like python returns unsupported with explicit reason`() = runBlocking {
        val files = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "main.py", name = "main.py", extension = "py", content = "def main():\n    print('hello')", isDirectory = false),
            ProjectFileEntity(id = "2", projectId = "proj-1", path = "requirements.txt", name = "requirements.txt", extension = "txt", content = "requests==2.28.1", isDirectory = false)
        )
        val result = validator.validateBuild("proj-1", files)
        assertTrue(result is BuildValidationResult.Unsupported)
        val unsupported = result as BuildValidationResult.Unsupported
        assertTrue(unsupported.reason.contains("cannot be natively compiled in this Android/JVM execution environment"))
    }

    @Test
    fun `test valid android gradle project passes build validation gate`() = runBlocking {
        val files = listOf(
            ProjectFileEntity(id = "1", projectId = "proj-1", path = "build.gradle.kts", name = "build.gradle.kts", extension = "kts", content = "plugins { kotlin(\"android\") }", isDirectory = false),
            ProjectFileEntity(id = "2", projectId = "proj-1", path = "app/src/main/java/Main.kt", name = "Main.kt", extension = "kt", content = "package app\nclass Main { fun start() { } }", isDirectory = false),
            ProjectFileEntity(id = "3", projectId = "proj-1", path = "app/src/main/AndroidManifest.xml", name = "AndroidManifest.xml", extension = "xml", content = "<manifest package=\"com.test\"><application/></manifest>", isDirectory = false)
        )
        val result = validator.validateBuild("proj-1", files)
        assertTrue(result is BuildValidationResult.Success)
        val success = result as BuildValidationResult.Success
        assertTrue(success.message.contains("passed syntax and structural integrity validation"))
    }
}
