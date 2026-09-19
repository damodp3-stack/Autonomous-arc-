package com.example.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AIProviderConfigRepository
import com.example.data.AppDatabase
import com.example.data.MessageEntity
import com.example.data.MessageRepository
import com.example.data.ProjectFileRepository
import com.example.data.ProjectFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutonomousExecutionEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var fileRepository: ProjectFileRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var codeChangeApplier: CodeChangeApplier
    private lateinit var fakeAIProvider: FakeAIProvider
    private lateinit var fakeAIFactory: FakeAIFactory
    private lateinit var engine: AutonomousExecutionEngine

    private val projectId = "test-project-autonomous"
    private val projectName = "AutonomousTestProject"

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
                    summary = "Default mock proposal",
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
        private val provider: AIProvider
    ) : AIFactory(configRepo, keyManager) {
        override suspend fun getProvider(projectName: String, uiSelectedProvider: String?): AIProvider {
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
        fakeAIFactory = FakeAIFactory(
            AIProviderConfigRepository(db.aiProviderConfigDao()),
            FakeAPIKeyManager(),
            fakeAIProvider
        )
        engine = AutonomousExecutionEngine(
            projectId = projectId,
            fileRepository = fileRepository,
            messageRepository = messageRepository,
            codeChangeApplier = codeChangeApplier,
            aiFactory = fakeAIFactory
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    // ==========================================
    // 1. Plan Parsing & Validation Tests
    // ==========================================

    @Test
    fun `test valid JSON plan parsing`() {
        val validJson = """
        {
          "goal": "Build todo app",
          "tasks": [
            {"id": "task-1", "description": "Create Todo model", "dependsOn": []},
            {"id": "task-2", "description": "Create Todo repository", "dependsOn": ["task-1"]}
          ]
        }
        """.trimIndent()

        val plan = engine.parseAndValidatePlan(validJson)
        assertEquals("Build todo app", plan.goal)
        assertEquals(2, plan.tasks.size)
        assertEquals("task-1", plan.tasks[0].id)
        assertEquals("Create Todo model", plan.tasks[0].description)
        assertTrue(plan.tasks[0].dependsOn.isEmpty())
        assertEquals(listOf("task-1"), plan.tasks[1].dependsOn)
    }

    @Test
    fun `test malformed JSON throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan("Not a valid json at all")
        }
    }

    @Test
    fun `test missing goal throws exception`() {
        val json = """{"tasks": [{"id": "task-1", "description": "desc"}]}"""
        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("goal"))
    }

    @Test
    fun `test missing tasks throws exception`() {
        val json = """{"goal": "Build app"}"""
        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("tasks"))
    }

    @Test
    fun `test empty task list throws exception`() {
        val json = """{"goal": "Build app", "tasks": []}"""
        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("empty"))
    }

    @Test
    fun `test more than 20 tasks throws exception`() {
        val tasksBuilder = StringBuilder()
        for (i in 1..21) {
            tasksBuilder.append("""{"id": "t-$i", "description": "desc $i"},""")
        }
        val tasksStr = tasksBuilder.toString().removeSuffix(",")
        val json = """{"goal": "Huge plan", "tasks": [$tasksStr]}"""

        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("Too many tasks"))
    }

    @Test
    fun `test duplicate IDs throw exception`() {
        val json = """
        {
          "goal": "Test",
          "tasks": [
            {"id": "task-dup", "description": "First"},
            {"id": "task-dup", "description": "Second"}
          ]
        }
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("Duplicate task IDs"))
    }

    @Test
    fun `test blank IDs throw exception`() {
        val json = """
        {
          "goal": "Test",
          "tasks": [
            {"id": "   ", "description": "Valid description"}
          ]
        }
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("Task ID"))
    }

    @Test
    fun `test blank descriptions throw exception`() {
        val json = """
        {
          "goal": "Test",
          "tasks": [
            {"id": "task-1", "description": "    "}
          ]
        }
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("Task description"))
    }

    @Test
    fun `test unknown dependencies throw exception`() {
        val json = """
        {
          "goal": "Test",
          "tasks": [
            {"id": "task-1", "description": "First", "dependsOn": ["non-existent-task"]}
          ]
        }
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(json)
        }
        assertTrue(ex.message!!.contains("unknown task"))
    }

    @Test
    fun `test dependency cycle throws exception`() {
        // Direct cycle task-1 <-> task-2
        val cycleJson = """
        {
          "goal": "Test",
          "tasks": [
            {"id": "task-1", "description": "First", "dependsOn": ["task-2"]},
            {"id": "task-2", "description": "Second", "dependsOn": ["task-1"]}
          ]
        }
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(cycleJson)
        }
        assertTrue(ex.message!!.contains("cycle"))

        // Self cycle
        val selfCycleJson = """
        {
          "goal": "Test",
          "tasks": [
            {"id": "task-self", "description": "Self", "dependsOn": ["task-self"]}
          ]
        }
        """.trimIndent()
        val exSelf = assertThrows(IllegalArgumentException::class.java) {
            engine.parseAndValidatePlan(selfCycleJson)
        }
        assertTrue(exSelf.message!!.contains("cycle"))
    }

    // ==========================================
    // 2. Dependency & Task Selection Tests
    // ==========================================

    @Test
    fun `test dependency ordering and independent tasks`() {
        val tasks = listOf(
            AutonomousTask("t-ind-1", "Independent 1", dependsOn = emptyList()),
            AutonomousTask("t-ind-2", "Independent 2", dependsOn = emptyList()),
            AutonomousTask("t-dep-1", "Dependent on 1", dependsOn = listOf("t-ind-1"))
        )
        val plan = AutonomousPlan("Goal", tasks)

        // Initially, first ready pending task is t-ind-1
        val firstReady = engine.getNextReadyTask(plan)
        assertEquals("t-ind-1", firstReady?.id)

        // Once t-ind-1 is COMPLETED, t-ind-2 is next ready pending task
        val planAfter1 = plan.copy(tasks = listOf(
            tasks[0].copy(status = AutonomousTaskStatus.COMPLETED),
            tasks[1],
            tasks[2]
        ))
        val secondReady = engine.getNextReadyTask(planAfter1)
        assertEquals("t-ind-2", secondReady?.id)

        // Once t-ind-2 is also COMPLETED, t-dep-1 is ready
        val planAfter2 = planAfter1.copy(tasks = listOf(
            planAfter1.tasks[0],
            planAfter1.tasks[1].copy(status = AutonomousTaskStatus.COMPLETED),
            planAfter1.tasks[2]
        ))
        val thirdReady = engine.getNextReadyTask(planAfter2)
        assertEquals("t-dep-1", thirdReady?.id)
    }

    @Test
    fun `test completed dependency unlocking dependent task`() {
        val task1 = AutonomousTask("task-1", "Step 1", dependsOn = emptyList())
        val task2 = AutonomousTask("task-2", "Step 2", dependsOn = listOf("task-1"))
        val plan = AutonomousPlan("Goal", listOf(task1, task2))

        // Before task-1 completion, task-2 is NOT ready
        val readyBefore = engine.getNextReadyTask(plan)
        assertEquals("task-1", readyBefore?.id)

        // After task-1 completion, task-2 IS ready
        val planAfter = plan.copy(tasks = listOf(
            task1.copy(status = AutonomousTaskStatus.COMPLETED),
            task2
        ))
        val readyAfter = engine.getNextReadyTask(planAfter)
        assertEquals("task-2", readyAfter?.id)
    }

    // ==========================================
    // 3. Execution Engine Workflow Tests
    // ==========================================

    @Test
    fun `test successful full execution`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """
            {
              "goal": "Build todo app",
              "tasks": [
                {"id": "t-1", "description": "Create model", "dependsOn": []}
              ]
            }
            """.trimIndent()
        }

        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            CodeChangeProposal(
                summary = "Create file",
                explanation = "exp",
                changes = listOf(
                    FileChange(
                        filePath = "src/Model.kt",
                        operation = FileOperation.CREATE,
                        proposedContent = "data class Todo(val id: String)"
                    )
                )
            )
        }

        engine.start("Build todo app", "MOCK", projectName)

        assertEquals(AutonomousState.COMPLETED, engine.state.value)
        val plan = engine.currentPlan.value
        assertNotNull(plan)
        assertEquals(AutonomousTaskStatus.COMPLETED, plan!!.tasks[0].status)

        // Verify file actually created
        val file = fileRepository.getFileByPath(projectId, "src/Model.kt")
        assertNotNull(file)
        assertEquals("data class Todo(val id: String)", file?.content)
    }

    @Test
    fun `test failed proposal retry`() = runBlocking {
        var attempts = 0
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            attempts++
            if (attempts == 1) {
                throw RuntimeException("Network error on first attempt")
            } else {
                CodeChangeProposal(
                    summary = "Recovered proposal",
                    explanation = "exp",
                    changes = listOf(
                        FileChange(
                            filePath = "src/Recovery.kt",
                            operation = FileOperation.CREATE,
                            proposedContent = "class Recovered"
                        )
                    )
                )
            }
        }

        engine.start("Goal with retry", "MOCK", projectName)

        assertEquals(2, attempts)
        assertEquals(AutonomousState.COMPLETED, engine.state.value)
        assertNotNull(fileRepository.getFileByPath(projectId, "src/Recovery.kt"))
    }

    @Test
    fun `test failed apply retry`() = runBlocking {
        var attempts = 0
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            attempts++
            if (attempts == 1) {
                // Return invalid modify on non-existent file
                CodeChangeProposal(
                    summary = "Invalid modify",
                    explanation = "exp",
                    changes = listOf(
                        FileChange(
                            filePath = "nonexistent.kt",
                            operation = FileOperation.MODIFY,
                            originalContent = "old",
                            proposedContent = "new"
                        )
                    )
                )
            } else {
                // Return valid create
                CodeChangeProposal(
                    summary = "Valid create",
                    explanation = "exp",
                    changes = listOf(
                        FileChange(
                            filePath = "created.kt",
                            operation = FileOperation.CREATE,
                            proposedContent = "valid"
                        )
                    )
                )
            }
        }

        engine.start("Goal with apply retry", "MOCK", projectName)

        assertEquals(2, attempts)
        assertEquals(AutonomousState.COMPLETED, engine.state.value)
        assertNotNull(fileRepository.getFileByPath(projectId, "created.kt"))
    }

    @Test
    fun `test verification failure triggers rollback`() = runBlocking {
        // Pre-create a file
        fileRepository.createFile(projectId, "existing.kt", "original content")
        val existing = fileRepository.getFileByPath(projectId, "existing.kt")
        assertNotNull(existing)

        var attempts = 0
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            attempts++
            if (attempts == 1) {
                // Proposal modifying existing.kt to modified content
                CodeChangeProposal(
                    summary = "Modifying",
                    explanation = "exp",
                    changes = listOf(
                        FileChange(
                            filePath = "existing.kt",
                            operation = FileOperation.MODIFY,
                            originalContent = "original content",
                            proposedContent = "modified content"
                        )
                    )
                )
            } else {
                CodeChangeProposal(
                    summary = "Valid follow-up",
                    explanation = "exp",
                    changes = listOf(
                        FileChange(
                            filePath = "final.kt",
                            operation = FileOperation.CREATE,
                            proposedContent = "done"
                        )
                    )
                )
            }
        }

        // Subclass engine to simulate verification failure on attempt 1
        val customEngine = object : AutonomousExecutionEngine(
            projectId, fileRepository, messageRepository, codeChangeApplier, fakeAIFactory
        ) {
            private var verifyCalls = 0
            override suspend fun verifyChanges(projectId: String, proposal: CodeChangeProposal): Boolean {
                verifyCalls++
                return if (verifyCalls == 1) false else super.verifyChanges(projectId, proposal)
            }
        }

        customEngine.start("Verification test", "MOCK", projectName)

        assertEquals(AutonomousState.COMPLETED, customEngine.state.value)
        // After rollback of attempt 1, existing.kt content should have been preserved
        val restored = fileRepository.getFileByPath(projectId, "existing.kt")
        assertNotNull(restored)
        assertEquals("original content", restored?.content)
        // And second attempt succeeded
        assertNotNull(fileRepository.getFileByPath(projectId, "final.kt"))
    }

    @Test
    fun `test retry limit transitions to BLOCKED`() = runBlocking {
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            throw RuntimeException("Persistent failure")
        }

        engine.start("Failing goal", "MOCK", projectName)

        assertEquals(AutonomousState.BLOCKED, engine.state.value)
        val plan = engine.currentPlan.value
        assertEquals(AutonomousTaskStatus.BLOCKED, plan!!.tasks[0].status)
    }

    @Test
    fun `test max iterations transitions to BLOCKED`() = runBlocking {
        engine.setMaxIterations(2)

        // Provide 3 independent tasks
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """
            {
              "goal": "Many tasks",
              "tasks": [
                {"id": "t-1", "description": "task 1", "dependsOn": []},
                {"id": "t-2", "description": "task 2", "dependsOn": []},
                {"id": "t-3", "description": "task 3", "dependsOn": []}
              ]
            }
            """.trimIndent()
        }

        var taskCounter = 0
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            taskCounter++
            CodeChangeProposal(
                summary = "Task $taskCounter",
                explanation = "exp",
                changes = listOf(
                    FileChange(
                        filePath = "file_$taskCounter.kt",
                        operation = FileOperation.CREATE,
                        proposedContent = "content"
                    )
                )
            )
        }

        engine.start("Max iterations goal", "MOCK", projectName)

        assertEquals(AutonomousState.BLOCKED, engine.state.value)
        assertEquals(2, engine.iteration.value)
    }

    @Test
    fun `test cancellation cleanly transitions to STOPPED`() = runBlocking {
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            delay(500)
            """{"goal": "Slow", "tasks": [{"id": "t-1", "description": "desc"}]}"""
        }

        val job = launch {
            engine.start("Cancel goal", "MOCK", projectName)
        }

        delay(100)
        engine.stop()
        job.join()

        assertEquals(AutonomousState.STOPPED, engine.state.value)
    }

    // ==========================================
    // 4. Proposal Validation & Security Tests
    // ==========================================

    @Test
    fun `test proposal validation rejects traversal paths`() {
        val traversalProposal = CodeChangeProposal(
            summary = "Unsafe traversal",
            explanation = "exp",
            changes = listOf(
                FileChange(
                    filePath = "../../../etc/passwd",
                    operation = FileOperation.CREATE,
                    proposedContent = "unsafe"
                )
            )
        )
        val result = engine.validateProposal(traversalProposal)
        assertTrue(result is ProposalValidationResult.Invalid)
        assertTrue((result as ProposalValidationResult.Invalid).reason.contains("traversal"))
    }

    @Test
    fun `test proposal validation rejects blank path`() {
        val blankProposal = CodeChangeProposal(
            summary = "Blank path",
            explanation = "exp",
            changes = listOf(
                FileChange(
                    filePath = "   ",
                    operation = FileOperation.CREATE,
                    proposedContent = "code"
                )
            )
        )
        val result = engine.validateProposal(blankProposal)
        assertTrue(result is ProposalValidationResult.Invalid)
        assertTrue((result as ProposalValidationResult.Invalid).reason.contains("blank"))
    }

    @Test
    fun `test proposal validation rejects duplicate paths in same proposal`() {
        val duplicateProposal = CodeChangeProposal(
            summary = "Duplicate paths",
            explanation = "exp",
            changes = listOf(
                FileChange(
                    filePath = "src/A.kt",
                    operation = FileOperation.CREATE,
                    proposedContent = "first"
                ),
                FileChange(
                    filePath = "src/A.kt",
                    operation = FileOperation.CREATE,
                    proposedContent = "second"
                )
            )
        )
        val result = engine.validateProposal(duplicateProposal)
        assertTrue(result is ProposalValidationResult.Invalid)
        assertTrue((result as ProposalValidationResult.Invalid).reason.contains("Duplicate"))
    }

    @Test
    fun `test proposal validation rejects rename without target path`() {
        val renameProposal = CodeChangeProposal(
            summary = "Rename missing target",
            explanation = "exp",
            changes = listOf(
                FileChange(
                    filePath = "src/Old.kt",
                    operation = FileOperation.RENAME,
                    newFilePath = null
                )
            )
        )
        val result = engine.validateProposal(renameProposal)
        assertTrue(result is ProposalValidationResult.Invalid)
        assertTrue((result as ProposalValidationResult.Invalid).reason.contains("target path"))
    }

    // ==========================================
    // 5. False Completion (Empty Changes) Tests
    // ==========================================

    @Test
    fun `test false completion prevention - retried when unsatisfied`() = runBlocking {
        var attempts = 0
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            attempts++
            if (attempts == 1) {
                // Empty changes on attempt 1
                CodeChangeProposal(
                    summary = "Empty proposal",
                    explanation = "Nothing changed",
                    changes = emptyList()
                )
            } else {
                // Generates changes on attempt 2
                CodeChangeProposal(
                    summary = "Real changes",
                    explanation = "Added file",
                    changes = listOf(
                        FileChange(
                            filePath = "src/Real.kt",
                            operation = FileOperation.CREATE,
                            proposedContent = "class Real"
                        )
                    )
                )
            }
        }

        // When queried if satisfied, provider returns false on attempt 1
        fakeAIProvider.generateResponseHandler = { prompt, _, _ ->
            if (prompt.contains("The code change proposal contained no changes")) {
                """{"isSatisfied": false, "reason": "Task needs files created"}"""
            } else {
                """{"goal": "Test Goal", "tasks": [{"id": "t-1", "description": "desc", "dependsOn": []}]}"""
            }
        }

        engine.start("False completion test", "MOCK", projectName)

        assertEquals(2, attempts)
        assertEquals(AutonomousState.COMPLETED, engine.state.value)
        assertNotNull(fileRepository.getFileByPath(projectId, "src/Real.kt"))
    }

    @Test
    fun `test valid completion when task already satisfied`() = runBlocking {
        fakeAIProvider.proposeCodeChangesHandler = { _, _ ->
            CodeChangeProposal(
                summary = "Empty proposal",
                explanation = "Already implemented",
                changes = emptyList()
            )
        }

        // When queried, provider confirms satisfied
        fakeAIProvider.generateResponseHandler = { prompt, _, _ ->
            if (prompt.contains("The code change proposal contained no changes")) {
                """{"isSatisfied": true, "reason": "Already exists"}"""
            } else {
                """{"goal": "Already done goal", "tasks": [{"id": "t-1", "description": "desc", "dependsOn": []}]}"""
            }
        }

        engine.start("Already done goal", "MOCK", projectName)

        assertEquals(AutonomousState.COMPLETED, engine.state.value)
        val plan = engine.currentPlan.value
        assertEquals(AutonomousTaskStatus.COMPLETED, plan!!.tasks[0].status)
    }

    // ==========================================
    // 6. Execution History & StateFlow Immutability Tests
    // ==========================================

    @Test
    fun `test execution history records structured events`() = runBlocking {
        engine.start("History test", "MOCK", projectName)

        val history = engine.executionHistory.value
        assertTrue(history.isNotEmpty())

        val eventTypes = history.map { it.type }
        assertTrue(eventTypes.contains(AutonomousEventType.PLAN_CREATED))
        assertTrue(eventTypes.contains(AutonomousEventType.TASK_STARTED))
        assertTrue(eventTypes.contains(AutonomousEventType.PROPOSAL_GENERATED))
        assertTrue(eventTypes.contains(AutonomousEventType.PROPOSAL_APPLIED))
        assertTrue(eventTypes.contains(AutonomousEventType.TASK_COMPLETED))
        assertTrue(eventTypes.contains(AutonomousEventType.RUN_COMPLETED))
    }

    @Test
    fun `test task status update emits new plan instance`() {
        val plan = AutonomousPlan("Goal", listOf(AutonomousTask("t-1", "desc")))
        fakeAIProvider.generateResponseHandler = { _, _, _ ->
            """{"goal": "Goal", "tasks": [{"id": "t-1", "description": "desc"}]}"""
        }

        runBlocking {
            engine.start("Goal", "MOCK", projectName)
        }

        val plan1 = engine.currentPlan.value
        assertEquals(AutonomousTaskStatus.COMPLETED, plan1!!.tasks[0].status)

        // Updating task status creates a new plan copy
        engine.updateTaskStatus("t-1", AutonomousTaskStatus.PENDING)
        val plan2 = engine.currentPlan.value
        assertNotSame(plan1, plan2)
        assertEquals(AutonomousTaskStatus.PENDING, plan2!!.tasks[0].status)
    }
}
