package com.example.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AIProviderConfigEntity
import com.example.data.AIProviderConfigRepository
import com.example.data.AppDatabase
import com.example.data.MessageEntity
import com.example.data.MessageRepository
import com.example.data.ProjectFileEntity
import com.example.data.ProjectFileRepository
import com.example.data.ProjectFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase10ReliabilityTest {

    private lateinit var db: AppDatabase
    private lateinit var fileRepository: ProjectFileRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var codeChangeApplier: CodeChangeApplier
    private lateinit var fakeAIProvider: FakeAIProvider
    private lateinit var fakeAIFactory: FakeAIFactory
    private lateinit var mockModelSelectionRepo: MockModelSelectionRepo
    private lateinit var engine: AutonomousExecutionEngine

    private val projectId = "phase10-reliability-project"
    private val projectName = "ExpenseTrackerAutonomous"

    class MockModelSelectionRepo : ModelSelectionRepository {
        var cachedList = listOf(
            DiscoveredModel(
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                providerType = "GEMINI",
                supportedGenerationMethods = listOf("generateContent"),
                isVerifiedLive = true
            )
        )
        override fun getCachedModels(providerType: String): List<DiscoveredModel> = cachedList
        override fun getLastDiscoveryTimestamp(providerType: String): Long? = null
        override suspend fun refreshModels(providerType: String, apiKey: String): Result<List<DiscoveredModel>> {
            return Result.success(cachedList)
        }
        override fun getCompatibleFallback(providerType: String, unavailableModel: String, availableModels: List<DiscoveredModel>): DiscoveredModel? {
            return availableModels.firstOrNull { it.id != unavailableModel } ?: cachedList.firstOrNull()
        }
        override fun getModelsState(providerType: String) = kotlinx.coroutines.flow.MutableStateFlow(
            ModelDiscoveryState(models = cachedList, isFromCache = false, lastRefreshedTimestamp = System.currentTimeMillis())
        )
    }

    class FakeAIProvider : AIProvider {
        var generateResponseHandler: (suspend (prompt: String, context: List<MessageEntity>, projectContext: ProjectContext?) -> String)? = null
        var proposeCodeChangesHandler: (suspend (request: String, projectContext: ProjectContext) -> CodeChangeProposal)? = null

        override suspend fun generateResponse(prompt: String, context: List<MessageEntity>, projectContext: ProjectContext?): String {
            return generateResponseHandler?.invoke(prompt, context, projectContext)
                ?: """{"goal":"Test Goal","tasks":[{"id":"task-1","description":"Create model","dependsOn":[]}]}"""
        }

        override suspend fun proposeCodeChanges(request: String, projectContext: ProjectContext): CodeChangeProposal {
            return proposeCodeChangesHandler?.invoke(request, projectContext)
                ?: CodeChangeProposal(
                    summary = "Default proposal",
                    explanation = "Test explanation",
                    changes = listOf(
                        FileChange(
                            filePath = "src/Model.kt",
                            operation = FileOperation.CREATE,
                            proposedContent = "class Model"
                        )
                    )
                )
        }
    }

    class FakeAPIKeyManager : APIKeyManager {
        override fun saveApiKey(providerId: String, apiKey: String) {}
        override fun getApiKey(providerId: String): String? = "fake-key"
        override fun clearApiKey(providerId: String) {}
    }

    class FakeAIFactory(
        configRepo: AIProviderConfigRepository,
        keyManager: APIKeyManager,
        modelSelectionRepo: ModelSelectionRepository,
        private val provider: AIProvider
    ) : AIFactory(configRepo, keyManager, modelSelectionRepo) {
        override suspend fun getProvider(projectName: String, uiSelectedProvider: String?, selectedModel: String?): AIProvider {
            return provider
        }
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val fileSystem = ProjectFileSystem(context)
        fileRepository = ProjectFileRepository(db.projectFileDao(), fileSystem)
        messageRepository = MessageRepository(db.messageDao())
        codeChangeApplier = CodeChangeApplier(fileRepository)
        fakeAIProvider = FakeAIProvider()
        mockModelSelectionRepo = MockModelSelectionRepo()
        val configRepo = AIProviderConfigRepository(db.aiProviderConfigDao())
        val keyManager = FakeAPIKeyManager()

        fakeAIFactory = FakeAIFactory(
            configRepo = configRepo,
            keyManager = keyManager,
            modelSelectionRepo = mockModelSelectionRepo,
            provider = fakeAIProvider
        )

        engine = AutonomousExecutionEngine(
            projectId = projectId,
            fileRepository = fileRepository,
            messageRepository = messageRepository,
            codeChangeApplier = codeChangeApplier,
            aiFactory = fakeAIFactory,
            buildValidator = DefaultBuildValidator()
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    // =========================================================================
    // 1. END-TO-END TEST SCENARIO (Requirement 2)
    // =========================================================================

    @Test
    fun `test end to end expense tracker autonomous workflow with build validation`() = runBlocking {
        // Goal: "Create a simple expense tracker with an expense list, amount, category, date, and total spending."
        val expenseGoal = "Create a simple expense tracker with an expense list, amount, category, date, and total spending."

        // Mock structured plan generation
        fakeAIProvider.generateResponseHandler = { prompt, _, _ ->
            if (prompt.contains("implementation plan")) {
                """
                {
                  "goal": "$expenseGoal",
                  "tasks": [
                    {
                      "id": "task-model",
                      "description": "Create Expense data model with amount, category, and date",
                      "dependsOn": []
                    },
                    {
                      "id": "task-repo",
                      "description": "Create ExpenseRepository with total spending calculation",
                      "dependsOn": ["task-model"]
                    },
                    {
                      "id": "task-build",
                      "description": "Create Gradle build configuration",
                      "dependsOn": ["task-repo"]
                    }
                  ]
                }
                """.trimIndent()
            } else {
                """{"status": "ok"}"""
            }
        }

        // Mock task code generation proposals
        fakeAIProvider.proposeCodeChangesHandler = { request, _ ->
            when {
                request.contains("- ID: task-model") -> {
                    CodeChangeProposal(
                        summary = "Create Expense entity",
                        explanation = "Data model for individual expenses",
                        changes = listOf(
                            FileChange(
                                filePath = "src/model/Expense.kt",
                                operation = FileOperation.CREATE,
                                proposedContent = """
                                package model

                                data class Expense(
                                    val id: String,
                                    val description: String,
                                    val amount: Double,
                                    val category: String,
                                    val date: String
                                )
                                """.trimIndent()
                            )
                        )
                    )
                }
                request.contains("- ID: task-repo") -> {
                    CodeChangeProposal(
                        summary = "Create ExpenseRepository",
                        explanation = "Expense repository with spending calculation",
                        changes = listOf(
                            FileChange(
                                filePath = "src/repository/ExpenseRepository.kt",
                                operation = FileOperation.CREATE,
                                proposedContent = """
                                package repository

                                import model.Expense

                                class ExpenseRepository {
                                    private val expenses = mutableListOf<Expense>()

                                    fun addExpense(expense: Expense) {
                                        expenses.add(expense)
                                    }

                                    fun getExpenses(): List<Expense> = expenses.toList()

                                    fun calculateTotalSpending(): Double {
                                        return expenses.sumOf { it.amount }
                                    }
                                }
                                """.trimIndent()
                            )
                        )
                    )
                }
                request.contains("- ID: task-build") -> {
                    CodeChangeProposal(
                        summary = "Create build.gradle.kts",
                        explanation = "Gradle build script configuration",
                        changes = listOf(
                            FileChange(
                                filePath = "build.gradle.kts",
                                operation = FileOperation.CREATE,
                                proposedContent = """
                                plugins {
                                    kotlin("jvm") version "2.0.0"
                                }
                                """.trimIndent()
                            )
                        )
                    )
                }
                else -> error("Unexpected task request: $request")
            }
        }

        // Execute autonomous run
        engine.start(expenseGoal, "GEMINI", projectName, "gemini-2.5-flash")

        // 1. Verify final state is COMPLETED (only after passing build validation)
        assertEquals(AutonomousState.COMPLETED, engine.state.value)

        // 2. Verify plan was created and all 3 tasks are COMPLETED
        val plan = engine.currentPlan.value
        assertNotNull(plan)
        assertEquals(3, plan!!.tasks.size)
        assertTrue(plan.tasks.all { it.status == AutonomousTaskStatus.COMPLETED })

        // 3. Verify files were persisted and remain readable in the workspace
        val projectFiles = fileRepository.getFilesForProject(projectId).first()
        val filePaths = projectFiles.map { it.path }

        assertTrue(filePaths.contains("src/model/Expense.kt"))
        assertTrue(filePaths.contains("src/repository/ExpenseRepository.kt"))
        assertTrue(filePaths.contains("build.gradle.kts"))

        // 4. Verify file contents contain required fields
        val expenseModelFile = fileRepository.getFileByPath(projectId, "src/model/Expense.kt")
        assertNotNull(expenseModelFile)
        assertTrue(expenseModelFile!!.content.contains("val amount: Double"))
        assertTrue(expenseModelFile.content.contains("val category: String"))
        assertTrue(expenseModelFile.content.contains("val date: String"))

        val repoFile = fileRepository.getFileByPath(projectId, "src/repository/ExpenseRepository.kt")
        assertNotNull(repoFile)
        assertTrue(repoFile!!.content.contains("fun calculateTotalSpending(): Double"))

        // 5. Verify Build Validation passed in event history
        val events = engine.executionHistory.value
        val hasBuildValidated = events.any { it.type == AutonomousEventType.BUILD_VALIDATED }
        val hasRunCompleted = events.any { it.type == AutonomousEventType.RUN_COMPLETED }
        assertTrue("Expected BUILD_VALIDATED event in history", hasBuildValidated)
        assertTrue("Expected RUN_COMPLETED event in history", hasRunCompleted)
    }

    // =========================================================================
    // 2. FAILURE RECOVERY TESTS (Requirement 3)
    // =========================================================================

    @Test
    fun `test malformed AI response for plan fails safely without silent success`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            "This is not JSON: {invalid"
        }

        engine.start("Malformed plan test", "GEMINI", projectName)

        assertEquals(AutonomousState.FAILED, engine.state.value)
        assertNotNull(engine.lastError.value)
        assertTrue(engine.lastError.value!!.contains("Failed to parse plan"))
        assertNull(engine.currentPlan.value)

        // Verify failure was logged to messages
        val messages = messageRepository.getMessagesForProject(projectId).first()
        assertTrue(messages.any { it.text.contains("Autonomous Run FAILED to parse plan") })
    }

    @Test
    fun `test empty AI response for plan fails safely`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ -> "   " }

        engine.start("Empty plan test", "GEMINI", projectName)

        assertEquals(AutonomousState.FAILED, engine.state.value)
        assertTrue(engine.lastError.value!!.contains("Failed to parse plan"))
    }

    @Test
    fun `test invalid code change proposal with path traversal is rejected`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """{"goal": "Security Test", "tasks": [{"id": "t1", "description": "hack", "dependsOn": []}]}"""
        }
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            CodeChangeProposal(
                summary = "Path traversal exploit",
                explanation = "Attempting to escape project directory",
                changes = listOf(
                    FileChange(
                        filePath = "../../../etc/passwd",
                        operation = FileOperation.CREATE,
                        proposedContent = "root:x:0:0"
                    )
                )
            )
        }

        engine.start("Security Test", "GEMINI", projectName)

        // The task should be blocked after consecutive validation failures
        assertEquals(AutonomousState.BLOCKED, engine.state.value)
        val files = fileRepository.getFilesForProject(projectId).first()
        assertTrue(files.none { it.path.contains("passwd") })
    }

    @Test
    fun `test missing dependency in plan is rejected before execution`() {
        val planJson = """
        {
          "goal": "Missing dep",
          "tasks": [
            {
              "id": "task-2",
              "description": "Depends on non-existent task-1",
              "dependsOn": ["task-1"]
            }
          ]
        }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(planJson)
        }
        assertTrue(ex.message!!.contains("depends on unknown task: task-1"))
    }

    @Test
    fun `test repeated task failure marks task blocked and terminates engine`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """{"goal": "Fail Goal", "tasks": [{"id": "t1", "description": "Always fails", "dependsOn": []}]}"""
        }
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            // Proposes invalid blank path which fails validation every time
            CodeChangeProposal(
                summary = "Bad proposal",
                explanation = "Bad",
                changes = listOf(
                    FileChange(
                        filePath = "",
                        operation = FileOperation.CREATE,
                        proposedContent = "broken"
                    )
                )
            )
        }

        engine.start("Fail Goal", "GEMINI", projectName)

        assertEquals(AutonomousState.BLOCKED, engine.state.value)
        val plan = engine.currentPlan.value
        assertEquals(AutonomousTaskStatus.BLOCKED, plan!!.tasks[0].status)
    }

    @Test
    fun `test compile and build validation failure prevents completion`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """{"goal": "Build Fail", "tasks": [{"id": "t1", "description": "Creates broken code", "dependsOn": []}]}"""
        }
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            CodeChangeProposal(
                summary = "Broken syntax proposal",
                explanation = "Generates Kotlin code with unclosed braces",
                changes = listOf(
                    FileChange(
                        filePath = "src/Broken.kt",
                        operation = FileOperation.CREATE,
                        proposedContent = "class Broken { fun unclosedMethod( { // syntax error"
                    ),
                    FileChange(
                        filePath = "build.gradle.kts",
                        operation = FileOperation.CREATE,
                        proposedContent = "plugins { kotlin(\"jvm\") }"
                    )
                )
            )
        }

        engine.start("Build Fail", "GEMINI", projectName)

        // MUST NOT be marked COMPLETED!
        assertEquals(AutonomousState.FAILED, engine.state.value)
        assertTrue(engine.lastError.value!!.contains("Build validation failed"))
        val history = engine.executionHistory.value
        assertTrue(history.any { it.type == AutonomousEventType.BUILD_VALIDATION_FAILED })
        assertFalse(history.any { it.type == AutonomousEventType.RUN_COMPLETED })
    }

    @Test
    fun `test build validation unsupported records limitation note instead of false build claim`() = runBlocking {
        // Source files have valid syntax, but no build.gradle or pom.xml script
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """{"goal": "Scriptless", "tasks": [{"id": "t1", "description": "Plain kotlin file", "dependsOn": []}]}"""
        }
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            CodeChangeProposal(
                summary = "Plain kotlin file",
                explanation = "Valid syntax without Gradle script",
                changes = listOf(
                    FileChange(
                        filePath = "src/Model.kt",
                        operation = FileOperation.CREATE,
                        proposedContent = "data class Model(val name: String)"
                    )
                )
            )
        }

        engine.start("Scriptless", "GEMINI", projectName)

        assertEquals(AutonomousState.COMPLETED, engine.state.value)
        val history = engine.executionHistory.value
        val validationEvent = history.find { it.type == AutonomousEventType.BUILD_VALIDATED }
        assertNotNull(validationEvent)
        assertTrue(validationEvent!!.details.contains("Build validation limitation"))
    }

    @Test
    fun `test network failure fails cleanly with descriptive message`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            throw UnknownHostException("Unable to resolve host api.gemini.com")
        }

        engine.start("Network test", "GEMINI", projectName)

        assertEquals(AutonomousState.FAILED, engine.state.value)
        assertTrue(engine.lastError.value!!.contains("Unable to resolve host"))
    }

    @Test
    fun `test authentication failure fails fast without burning retry attempts`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            throw retrofit2.HttpException(
                retrofit2.Response.error<String>(401, okhttp3.ResponseBody.create(null, "{\"error\": \"API_KEY_INVALID\"}"))
            )
        }

        engine.start("Auth test", "GEMINI", projectName)

        assertEquals(AutonomousState.FAILED, engine.state.value)
        assertTrue(engine.lastError.value!!.contains("Authentication failed"))
        assertEquals(0, engine.retryCount.value) // Failed fast, no wasted retries!
    }

    @Test
    fun `test quota exceeded failure fails fast with explicit message`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            throw retrofit2.HttpException(
                retrofit2.Response.error<String>(429, okhttp3.ResponseBody.create(null, "{\"error\": \"RESOURCE_EXHAUSTED\"}"))
            )
        }

        engine.start("Quota test", "GEMINI", projectName)

        assertEquals(AutonomousState.FAILED, engine.state.value)
        assertTrue(engine.lastError.value!!.contains("Quota or rate limit exceeded"))
    }

    @Test
    fun `test model disappearing during execution recovers with compatible discovered fallback`() = runBlocking {
        var attempts = 0
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            attempts++
            if (attempts == 1) {
                // First attempt throws 404 model not found
                throw retrofit2.HttpException(
                    retrofit2.Response.error<String>(404, okhttp3.ResponseBody.create(null, "{\"error\": \"Model obsolete-model not found\"}"))
                )
            } else {
                // Recovered call returns valid plan
                """{"goal": "Recovery Goal", "tasks": [{"id": "t1", "description": "task 1", "dependsOn": []}]}"""
            }
        }

        engine.start("Recovery Goal", "GEMINI", projectName, model = "obsolete-model")

        assertEquals(AutonomousState.COMPLETED, engine.state.value)
        val history = engine.executionHistory.value
        assertTrue(history.any { it.details.contains("Switched to discovered model") || it.details.contains("Recovered with live discovered model") })
    }

    @Test
    fun `test cancellation during execution stops cleanly`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            // Simulate user stopping while planning
            engine.stop()
            throw CancellationException("Cancelled by user")
        }

        try {
            engine.start("Cancel Goal", "GEMINI", projectName)
        } catch (_: CancellationException) {
            // Expected
        }

        assertEquals(AutonomousState.STOPPED, engine.state.value)
    }

    // =========================================================================
    // 3. ROLLBACK & IDEMPOTENT RETRY TESTS (Requirements 4 & 5)
    // =========================================================================

    @Test
    fun `test rollback preserves files from previous tasks when subsequent task fails verification`() = runBlocking {
        // Pre-create Task 1 file
        fileRepository.createFile(projectId, "src/Task1.kt", "class Task1")

        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """{"goal": "Multi-task Rollback", "tasks": [{"id": "t2", "description": "Fails verification", "dependsOn": []}]}"""
        }

        // Custom engine where verification fails for Task 2
        val failingEngine = object : AutonomousExecutionEngine(
            projectId = projectId,
            fileRepository = fileRepository,
            messageRepository = messageRepository,
            codeChangeApplier = codeChangeApplier,
            aiFactory = fakeAIFactory,
            buildValidator = DefaultBuildValidator()
        ) {
            override suspend fun verifyChanges(projectId: String, proposal: CodeChangeProposal): Boolean {
                return false // Verification fails!
            }
        }

        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            CodeChangeProposal(
                summary = "Create Task2",
                explanation = "Will fail verification",
                changes = listOf(
                    FileChange(
                        filePath = "src/Task2.kt",
                        operation = FileOperation.CREATE,
                        proposedContent = "class Task2"
                    )
                )
            )
        }

        failingEngine.start("Multi-task Rollback", "GEMINI", projectName)

        // Task 2 was blocked after failures
        assertEquals(AutonomousState.BLOCKED, failingEngine.state.value)

        // Task 1 file MUST be preserved!
        val task1File = fileRepository.getFileByPath(projectId, "src/Task1.kt")
        assertNotNull("Task 1 file must not be destroyed", task1File)
        assertEquals("class Task1", task1File!!.content)

        // Task 2 file MUST be rolled back (not present)
        val task2File = fileRepository.getFileByPath(projectId, "src/Task2.kt")
        assertNull("Task 2 file must be rolled back on verification failure", task2File)
    }

    @Test
    fun `test retry does not duplicate existing files in repository`() = runBlocking {
        var attempts = 0
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """{"goal": "Idempotent Retry", "tasks": [{"id": "t1", "description": "task", "dependsOn": []}]}"""
        }
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            attempts++
            if (attempts == 1) {
                // First attempt creates file but fails verification
                CodeChangeProposal(
                    summary = "Attempt 1",
                    explanation = "First attempt",
                    changes = listOf(
                        FileChange(filePath = "src/File.kt", operation = FileOperation.CREATE, proposedContent = "v1")
                    )
                )
            } else {
                // Retry succeeds
                CodeChangeProposal(
                    summary = "Attempt 2",
                    explanation = "Second attempt",
                    changes = listOf(
                        FileChange(filePath = "src/File.kt", operation = FileOperation.CREATE, proposedContent = "v2")
                    )
                )
            }
        }

        var verifyCalls = 0
        val retryEngine = object : AutonomousExecutionEngine(
            projectId = projectId,
            fileRepository = fileRepository,
            messageRepository = messageRepository,
            codeChangeApplier = codeChangeApplier,
            aiFactory = fakeAIFactory,
            buildValidator = DefaultBuildValidator()
        ) {
            override suspend fun verifyChanges(projectId: String, proposal: CodeChangeProposal): Boolean {
                verifyCalls++
                return verifyCalls > 1 // Fails first time, succeeds second time
            }
        }

        retryEngine.start("Idempotent Retry", "GEMINI", projectName)

        assertEquals(AutonomousState.COMPLETED, retryEngine.state.value)

        // Verify there is exactly one file with path src/File.kt
        val files = fileRepository.getFilesForProject(projectId).first()
        val matching = files.filter { it.path == "src/File.kt" }
        assertEquals(1, matching.size)
        assertEquals("v2", matching[0].content)
    }

    // =========================================================================
    // 4. LARGE PROJECT & STRESS SAFETY (Requirement 8)
    // =========================================================================

    @Test
    fun `test plan with dependency cycle is detected and rejected`() {
        val cycleJson = """
        {
          "goal": "Cycle Test",
          "tasks": [
            { "id": "t1", "description": "Task 1", "dependsOn": ["t2"] },
            { "id": "t2", "description": "Task 2", "dependsOn": ["t1"] }
          ]
        }
        """.trimIndent()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(cycleJson)
        }
        assertTrue(ex.message!!.contains("Dependency cycle detected"))
    }

    @Test
    fun `test plan with excessive task count is rejected`() {
        val tasks = (1..25).map { """{"id": "t$it", "description": "Task $it", "dependsOn": []}""" }.joinToString(",")
        val json = """{"goal": "Too many tasks", "tasks": [$tasks]}"""

        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("Too many tasks"))
    }

    @Test
    fun `test proposal with excessive file count is rejected by validator`() {
        val changes = (1..55).map {
            FileChange("src/File$it.kt", FileOperation.CREATE, proposedContent = "class File$it")
        }
        val proposal = CodeChangeProposal(summary = "Too many", explanation = "desc", changes = changes)

        val result = engine.validateProposal(proposal)
        assertTrue(result is ProposalValidationResult.Invalid)
        assertTrue((result as ProposalValidationResult.Invalid).reason.contains("Too many file changes"))
    }

    @Test
    fun `test proposal with excessive file content size is rejected by validator`() {
        val hugeContent = "A".repeat(600_000) // 600KB > 500KB limit
        val proposal = CodeChangeProposal(
            summary = "Huge file",
            explanation = "desc",
            changes = listOf(FileChange("src/Huge.kt", FileOperation.CREATE, proposedContent = hugeContent))
        )

        val result = engine.validateProposal(proposal)
        assertTrue(result is ProposalValidationResult.Invalid)
        assertTrue((result as ProposalValidationResult.Invalid).reason.contains("exceeds 500KB limit"))
    }
}
