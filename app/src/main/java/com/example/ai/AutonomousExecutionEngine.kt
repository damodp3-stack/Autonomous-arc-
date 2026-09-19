package com.example.ai

import com.example.data.MessageEntity
import com.example.data.MessageRepository
import com.example.data.ProjectFileEntity
import com.example.data.ProjectFileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject

enum class AutonomousTaskStatus {
    PENDING, RUNNING, COMPLETED, FAILED, BLOCKED
}

data class AutonomousTask(
    val id: String,
    val description: String,
    val dependsOn: List<String> = emptyList(),
    val status: AutonomousTaskStatus = AutonomousTaskStatus.PENDING
)

data class AutonomousPlan(
    val goal: String,
    val tasks: List<AutonomousTask>
)

enum class AutonomousState {
    IDLE, PLANNING, GENERATING, VALIDATING, APPLYING, VERIFYING, CONTINUING, COMPLETED, FAILED, BLOCKED, STOPPED
}

enum class AutonomousEventType {
    PLAN_CREATED,
    TASK_STARTED,
    PROPOSAL_GENERATED,
    PROPOSAL_VALIDATION_FAILED,
    PROPOSAL_APPLIED,
    VERIFICATION_FAILED,
    ROLLBACK,
    RETRY,
    TASK_COMPLETED,
    TASK_BLOCKED,
    RUN_COMPLETED,
    RUN_STOPPED,
    RUN_FAILED
}

data class AutonomousEvent(
    val type: AutonomousEventType,
    val taskId: String? = null,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class ProposalValidationResult {
    object Valid : ProposalValidationResult()
    data class Invalid(val reason: String) : ProposalValidationResult()
}

open class AutonomousExecutionEngine(
    private val projectId: String,
    private val fileRepository: ProjectFileRepository,
    private val messageRepository: MessageRepository,
    private val codeChangeApplier: CodeChangeApplier,
    private val aiFactory: AIFactory
) {
    private val _state = MutableStateFlow(AutonomousState.IDLE)
    val state: StateFlow<AutonomousState> = _state.asStateFlow()

    private val _iteration = MutableStateFlow(0)
    val iteration: StateFlow<Int> = _iteration.asStateFlow()

    private val _maxIterations = MutableStateFlow(10)
    val maxIterations: StateFlow<Int> = _maxIterations.asStateFlow()

    private val _retryCount = MutableStateFlow(0)
    val retryCount: StateFlow<Int> = _retryCount.asStateFlow()

    private val _lastAction = MutableStateFlow<String?>("Ready")
    val lastAction: StateFlow<String?> = _lastAction.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _currentPlan = MutableStateFlow<AutonomousPlan?>(null)
    val currentPlan: StateFlow<AutonomousPlan?> = _currentPlan.asStateFlow()

    private val _currentTaskIndex = MutableStateFlow(-1)
    val currentTaskIndex: StateFlow<Int> = _currentTaskIndex.asStateFlow()

    private val _executionHistory = MutableStateFlow<List<AutonomousEvent>>(emptyList())
    val executionHistory: StateFlow<List<AutonomousEvent>> = _executionHistory.asStateFlow()

    val maxConsecutiveFailures = 2
    private var runJob: Job? = null

    fun updateTaskStatus(taskId: String, newStatus: AutonomousTaskStatus) {
        val plan = _currentPlan.value ?: return
        val updatedTasks = plan.tasks.map { task ->
            if (task.id == taskId) task.copy(status = newStatus) else task
        }
        _currentPlan.value = plan.copy(tasks = updatedTasks)
    }

    private fun recordEvent(type: AutonomousEventType, taskId: String? = null, details: String) {
        val event = AutonomousEvent(type = type, taskId = taskId, details = details)
        _executionHistory.value = _executionHistory.value + event
    }

    suspend fun start(goal: String, providerName: String, projectName: String) = coroutineScope {
        if (_state.value != AutonomousState.IDLE &&
            _state.value != AutonomousState.COMPLETED &&
            _state.value != AutonomousState.STOPPED &&
            _state.value != AutonomousState.FAILED &&
            _state.value != AutonomousState.BLOCKED) {
            return@coroutineScope
        }

        _iteration.value = 0
        _retryCount.value = 0
        _lastError.value = null
        _lastAction.value = "Starting autonomous run for goal"
        _executionHistory.value = emptyList()

        runJob = coroutineContext[Job]

        try {
            coroutineContext.ensureActive()
            val aiProvider = aiFactory.getProvider(projectName, providerName)

            _state.value = AutonomousState.PLANNING
            _lastAction.value = "Creating execution plan..."
            recordEvent(AutonomousEventType.TASK_STARTED, null, "Creating execution plan for: $goal")

            val files = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
            val projectContext = ProjectContext(projectName, files, null)

            val planPrompt = """Goal: $goal
Create a structured implementation plan. Return strict machine-readable JSON matching this exact schema:
{
  "goal": "original goal",
  "tasks": [
    {
      "id": "task-1",
      "description": "Create the required data model",
      "dependsOn": []
    }
  ]
}
Return ONLY valid JSON. Do not include markdown blocks or other text.
"""
            coroutineContext.ensureActive()
            val planJsonString = try {
                aiProvider.generateResponse(planPrompt, emptyList(), projectContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AutonomousState.FAILED
                _lastError.value = "Failed to fetch plan: ${e.message}"
                recordEvent(AutonomousEventType.RUN_FAILED, null, "Failed to fetch plan: ${e.message}")
                messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run FAILED to fetch plan: ${e.message}", isUser = false))
                return@coroutineScope
            }

            val plan = try {
                parseAndValidatePlan(planJsonString)
            } catch (e: Exception) {
                _state.value = AutonomousState.FAILED
                _lastError.value = "Failed to parse plan: ${e.message}"
                recordEvent(AutonomousEventType.RUN_FAILED, null, "Failed to parse plan: ${e.message}")
                messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run FAILED to parse plan: ${e.message}\n\nResponse was:\n$planJsonString", isUser = false))
                return@coroutineScope
            }

            _currentPlan.value = plan
            recordEvent(AutonomousEventType.PLAN_CREATED, null, "Plan created with ${plan.tasks.size} tasks")
            messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Plan Created: ${plan.tasks.size} tasks.", isUser = false))

            var globalIteration = 0

            while (isActive && globalIteration < _maxIterations.value) {
                coroutineContext.ensureActive()
                val currentPlanVal = _currentPlan.value ?: break

                val nextTask = getNextReadyTask(currentPlanVal)
                if (nextTask == null) {
                    if (currentPlanVal.tasks.all { it.status == AutonomousTaskStatus.COMPLETED }) {
                        _state.value = AutonomousState.COMPLETED
                        _lastAction.value = "All tasks completed successfully."
                        recordEvent(AutonomousEventType.RUN_COMPLETED, null, "All tasks completed successfully")
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run Completed Successfully.", isUser = false))
                        return@coroutineScope
                    } else if (currentPlanVal.tasks.any { it.status == AutonomousTaskStatus.BLOCKED || it.status == AutonomousTaskStatus.FAILED }) {
                        _state.value = AutonomousState.BLOCKED
                        _lastAction.value = "Execution blocked due to failed tasks."
                        recordEvent(AutonomousEventType.RUN_FAILED, null, "Execution blocked due to failed tasks")
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run BLOCKED. Some tasks failed or are unreachable.", isUser = false))
                        return@coroutineScope
                    } else {
                        _state.value = AutonomousState.BLOCKED
                        _lastAction.value = "Execution blocked. No ready tasks found."
                        recordEvent(AutonomousEventType.RUN_FAILED, null, "Dependency deadlock")
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run BLOCKED. Dependency deadlock.", isUser = false))
                        return@coroutineScope
                    }
                }

                val taskIndex = currentPlanVal.tasks.indexOfFirst { it.id == nextTask.id }
                _currentTaskIndex.value = taskIndex

                updateTaskStatus(nextTask.id, AutonomousTaskStatus.RUNNING)
                recordEvent(AutonomousEventType.TASK_STARTED, nextTask.id, "Started task ${nextTask.id}: ${nextTask.description}")

                var consecutiveFailures = 0
                val currentFiles = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
                var currentContextRequest = buildTaskPrompt(currentPlanVal, nextTask)
                var wasRolledBack = false
                var previousSummary: String? = null

                while (isActive && consecutiveFailures < maxConsecutiveFailures && globalIteration < _maxIterations.value) {
                    coroutineContext.ensureActive()
                    globalIteration++
                    _iteration.value = globalIteration
                    _retryCount.value = consecutiveFailures

                    _state.value = AutonomousState.GENERATING
                    _lastAction.value = "Generating proposal for task: ${nextTask.id} (Attempt ${consecutiveFailures + 1})"

                    val loopFiles = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
                    val currentProjectContext = ProjectContext(projectName, loopFiles, null)

                    val proposal = try {
                        aiProvider.proposeCodeChanges(currentContextRequest, currentProjectContext)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        consecutiveFailures++
                        _retryCount.value = consecutiveFailures
                        _lastError.value = "Generation failed: ${e.message}"
                        recordEvent(AutonomousEventType.RETRY, nextTask.id, "Generation failed: ${e.message}")
                        currentContextRequest = buildRetryPrompt(
                            plan = _currentPlan.value ?: currentPlanVal,
                            task = nextTask,
                            error = "Generation failed: ${e.message}",
                            files = loopFiles,
                            wasRolledBack = false,
                            previousSummary = null
                        )
                        continue
                    }

                    coroutineContext.ensureActive()
                    recordEvent(AutonomousEventType.PROPOSAL_GENERATED, nextTask.id, proposal.summary)
                    previousSummary = proposal.summary

                    // 1. Check for empty changes (False completion prevention)
                    if (proposal.changes.isEmpty()) {
                        _state.value = AutonomousState.VALIDATING
                        _lastAction.value = "Verifying completion of task: ${nextTask.id}"

                        val isSatisfied = checkTaskCompletion(aiProvider, nextTask, _currentPlan.value ?: currentPlanVal, currentProjectContext)
                        if (isSatisfied) {
                            updateTaskStatus(nextTask.id, AutonomousTaskStatus.COMPLETED)
                            recordEvent(AutonomousEventType.TASK_COMPLETED, nextTask.id, "Completed (Verified satisfied with existing files)")
                            messageRepository.insert(MessageEntity(projectId = projectId, text = "Task ${nextTask.id} completed (No changes needed, verified satisfied).", isUser = false))
                            break
                        } else {
                            consecutiveFailures++
                            _retryCount.value = consecutiveFailures
                            _lastError.value = "Task ${nextTask.id} returned no changes but is not satisfied."
                            recordEvent(AutonomousEventType.RETRY, nextTask.id, "Empty changes returned and task not satisfied")
                            currentContextRequest = buildRetryPrompt(
                                plan = _currentPlan.value ?: currentPlanVal,
                                task = nextTask,
                                error = "You proposed an empty set of changes, but the task is NOT satisfied by existing files. Please propose concrete code changes.",
                                files = loopFiles,
                                wasRolledBack = false,
                                previousSummary = proposal.summary
                            )
                            continue
                        }
                    }

                    // 2. Separate Validation Stage
                    _state.value = AutonomousState.VALIDATING
                    _lastAction.value = "Validating proposal for task: ${nextTask.id}"

                    val validationResult = validateProposal(proposal)
                    if (validationResult is ProposalValidationResult.Invalid) {
                        consecutiveFailures++
                        _retryCount.value = consecutiveFailures
                        _lastError.value = "Validation failed: ${validationResult.reason}"
                        recordEvent(AutonomousEventType.PROPOSAL_VALIDATION_FAILED, nextTask.id, validationResult.reason)
                        recordEvent(AutonomousEventType.RETRY, nextTask.id, "Validation failed: ${validationResult.reason}")
                        currentContextRequest = buildRetryPrompt(
                            plan = _currentPlan.value ?: currentPlanVal,
                            task = nextTask,
                            error = "Proposal validation failed: ${validationResult.reason}. Please correct the paths or operations and retry.",
                            files = loopFiles,
                            wasRolledBack = false,
                            previousSummary = proposal.summary
                        )
                        continue
                    }

                    // 3. Applying Stage
                    _state.value = AutonomousState.APPLYING
                    _lastAction.value = "Applying task ${nextTask.id}: ${proposal.summary}"
                    val result = codeChangeApplier.applyProposal(projectId, proposal)

                    if (result is ApplyResult.Success) {
                        recordEvent(AutonomousEventType.PROPOSAL_APPLIED, nextTask.id, "Applied ${proposal.changes.size} changes")

                        // 4. Verifying Stage
                        _state.value = AutonomousState.VERIFYING
                        _lastAction.value = "Verifying task ${nextTask.id}"

                        val verifyResult = verifyChanges(projectId, proposal)
                        if (verifyResult) {
                            updateTaskStatus(nextTask.id, AutonomousTaskStatus.COMPLETED)
                            _state.value = AutonomousState.CONTINUING
                            _lastAction.value = "Applied and verified task ${nextTask.id}"
                            recordEvent(AutonomousEventType.TASK_COMPLETED, nextTask.id, "Applied and verified: ${proposal.summary}")

                            messageRepository.insert(MessageEntity(
                                projectId = projectId,
                                text = "Task ${nextTask.id} Applied & Verified:\n\nSummary: ${proposal.summary}",
                                isUser = false
                            ))
                            break
                        } else {
                            // Verification failed -> Rollback
                            codeChangeApplier.rollback(projectId, result.createdFileIds, result.snapshot)
                            wasRolledBack = true
                            consecutiveFailures++
                            _retryCount.value = consecutiveFailures
                            _lastError.value = "Verification failed for task ${nextTask.id}, rolled back."
                            recordEvent(AutonomousEventType.VERIFICATION_FAILED, nextTask.id, "Verification failed for task ${nextTask.id}")
                            recordEvent(AutonomousEventType.ROLLBACK, nextTask.id, "Rolled back changes for task ${nextTask.id}")
                            recordEvent(AutonomousEventType.RETRY, nextTask.id, "Verification failure retry")

                            currentContextRequest = buildRetryPrompt(
                                plan = _currentPlan.value ?: currentPlanVal,
                                task = nextTask,
                                error = "Verification failed: The applied changes did not produce the expected files or content on the filesystem. All changes were rolled back.",
                                files = loopFiles,
                                wasRolledBack = true,
                                previousSummary = proposal.summary
                            )
                        }
                    } else {
                        val errorMsg = when(result) {
                            is ApplyResult.ValidationError -> result.message
                            is ApplyResult.Conflict -> "${result.message} at ${result.filePath}"
                            is ApplyResult.ApplyError -> result.message
                            is ApplyResult.RollbackError -> "Rollback error: ${result.rollbackError} (Original: ${result.originalError})"
                            else -> "Unknown error"
                        }
                        consecutiveFailures++
                        _retryCount.value = consecutiveFailures
                        _lastError.value = "Failed to apply task ${nextTask.id}: $errorMsg"
                        recordEvent(AutonomousEventType.RETRY, nextTask.id, "Apply failed: $errorMsg")
                        currentContextRequest = buildRetryPrompt(
                            plan = _currentPlan.value ?: currentPlanVal,
                            task = nextTask,
                            error = "Failed to apply changes: $errorMsg",
                            files = loopFiles,
                            wasRolledBack = false,
                            previousSummary = proposal.summary
                        )
                    }
                }

                if (consecutiveFailures >= maxConsecutiveFailures) {
                    updateTaskStatus(nextTask.id, AutonomousTaskStatus.BLOCKED)
                    _state.value = AutonomousState.BLOCKED
                    _lastAction.value = "Task ${nextTask.id} blocked after $consecutiveFailures failures."
                    recordEvent(AutonomousEventType.TASK_BLOCKED, nextTask.id, "Blocked after $consecutiveFailures failures")
                    messageRepository.insert(MessageEntity(projectId = projectId, text = "Task ${nextTask.id} BLOCKED: Exceeded retry limit.", isUser = false))
                    return@coroutineScope
                }
            }

            if (globalIteration >= _maxIterations.value) {
                _state.value = AutonomousState.BLOCKED
                _lastAction.value = "Reached max iterations (${_maxIterations.value})."
                recordEvent(AutonomousEventType.RUN_FAILED, null, "Reached max iterations limit (${_maxIterations.value})")
                messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run paused after reaching maximum iteration limit (${_maxIterations.value}).", isUser = false))
            }

        } catch (e: CancellationException) {
            _state.value = AutonomousState.STOPPED
            _lastAction.value = "Execution stopped by user."
            recordEvent(AutonomousEventType.RUN_STOPPED, null, "Execution stopped by user")
            messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run STOPPED.", isUser = false))
            throw e
        } catch (e: Exception) {
            _state.value = AutonomousState.FAILED
            _lastError.value = "Unexpected error: ${e.message}"
            recordEvent(AutonomousEventType.RUN_FAILED, null, "Unexpected error: ${e.message}")
            messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run FAILED: ${e.message}", isUser = false))
        }
    }

    fun stop() {
        if (_state.value != AutonomousState.IDLE &&
            _state.value != AutonomousState.STOPPED &&
            _state.value != AutonomousState.COMPLETED &&
            _state.value != AutonomousState.BLOCKED &&
            _state.value != AutonomousState.FAILED) {
            _state.value = AutonomousState.STOPPED
            _lastAction.value = "Execution stopped by user."
            recordEvent(AutonomousEventType.RUN_STOPPED, null, "Execution stopped by user")
            runJob?.cancel()
        }
    }

    fun setMaxIterations(max: Int) {
        _maxIterations.value = max
    }

    fun validateProposal(proposal: CodeChangeProposal): ProposalValidationResult {
        if (proposal.changes.isEmpty()) {
            return ProposalValidationResult.Valid
        }

        val seenPaths = mutableSetOf<String>()

        for (change in proposal.changes) {
            val path = change.filePath
            if (path.isBlank()) {
                return ProposalValidationResult.Invalid("File path cannot be blank")
            }
            if (path.contains("\u0000")) {
                return ProposalValidationResult.Invalid("File path contains null byte: $path")
            }

            val normalized = codeChangeApplier.normalizePath(path)
                ?: return ProposalValidationResult.Invalid("Security Error: Invalid or traversal path detected: $path")

            if (!seenPaths.add(normalized)) {
                return ProposalValidationResult.Invalid("Duplicate conflicting change for path: $normalized")
            }

            when (change.operation) {
                FileOperation.CREATE -> {
                    // CREATE path is normalized
                }
                FileOperation.MODIFY -> {
                    // MODIFY path is normalized
                }
                FileOperation.DELETE -> {
                    // DELETE path is normalized
                }
                FileOperation.RENAME -> {
                    val newPath = change.newFilePath
                    if (newPath.isNullOrBlank()) {
                        return ProposalValidationResult.Invalid("Rename requires a non-empty target path (newFilePath) for: $path")
                    }
                    if (newPath.contains("\u0000")) {
                        return ProposalValidationResult.Invalid("Rename newFilePath contains null byte: $newPath")
                    }
                    val normalizedNew = codeChangeApplier.normalizePath(newPath)
                        ?: return ProposalValidationResult.Invalid("Security Error: Invalid or traversal rename target path: $newPath")
                    if (normalized == normalizedNew) {
                        return ProposalValidationResult.Invalid("Rename source and target paths cannot be identical: $path")
                    }
                    if (!seenPaths.add(normalizedNew)) {
                        return ProposalValidationResult.Invalid("Duplicate conflicting target path: $normalizedNew")
                    }
                }
            }
        }
        return ProposalValidationResult.Valid
    }

    open suspend fun verifyChanges(projectId: String, proposal: CodeChangeProposal): Boolean {
        val files = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
        val fileMap = files.associateBy { it.path }

        for (change in proposal.changes) {
            val normalizedPath = codeChangeApplier.normalizePath(change.filePath) ?: return false
            when (change.operation) {
                FileOperation.CREATE -> {
                    val file = fileMap[normalizedPath] ?: return false
                    if (change.proposedContent.isNotEmpty() && file.content.isEmpty()) {
                        return false
                    }
                }
                FileOperation.DELETE -> {
                    if (fileMap.containsKey(normalizedPath)) {
                        return false
                    }
                }
                FileOperation.RENAME -> {
                    val newPath = change.newFilePath?.let { codeChangeApplier.normalizePath(it) } ?: return false
                    if (fileMap.containsKey(normalizedPath)) return false
                    if (!fileMap.containsKey(newPath)) return false
                }
                FileOperation.MODIFY -> {
                    val file = fileMap[normalizedPath] ?: return false
                    if (file.content != change.proposedContent) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private suspend fun checkTaskCompletion(
        aiProvider: AIProvider,
        task: AutonomousTask,
        plan: AutonomousPlan,
        projectContext: ProjectContext
    ): Boolean {
        val fileList = projectContext.files.joinToString("\n") { "- ${it.path} (${it.content.length} chars)" }
        val prompt = """Goal: ${plan.goal}
Task: ${task.id} - ${task.description}
Project Files:
$fileList

The code change proposal contained no changes.
Is this specific task ALREADY fully satisfied by the project files above?
Respond with ONLY a JSON object:
{"isSatisfied": true, "reason": "why"} or {"isSatisfied": false, "reason": "why"}
"""
        return try {
            val response = aiProvider.generateResponse(prompt, emptyList(), projectContext)
            val clean = response.replace(Regex("```json\\s*"), "").replace(Regex("```\\s*"), "").trim()
            val json = JSONObject(clean)
            json.optBoolean("isSatisfied", false)
        } catch (e: Exception) {
            false
        }
    }

    private fun buildTaskPrompt(plan: AutonomousPlan, task: AutonomousTask): String {
        val completed = plan.tasks.filter { it.status == AutonomousTaskStatus.COMPLETED }
        val completedStr = if (completed.isEmpty()) "None" else completed.joinToString("\n") { "- ${it.id}: ${it.description}" }

        return """
Goal: ${plan.goal}

Completed Tasks:
$completedStr

Current Task to Implement:
- ID: ${task.id}
- Description: ${task.description}

Please analyze the current project context and propose the code changes required to implement this specific task.
If the task requires no code changes (e.g. it was already implemented), return an empty 'changes' array.
"""
    }

    private fun buildRetryPrompt(
        plan: AutonomousPlan,
        task: AutonomousTask,
        error: String,
        files: List<ProjectFileEntity>,
        wasRolledBack: Boolean,
        previousSummary: String?
    ): String {
        val planSummary = plan.tasks.joinToString("\n") { "[${it.status}] ${it.id}: ${it.description}" }
        val completed = plan.tasks.filter { it.status == AutonomousTaskStatus.COMPLETED }
        val completedStr = if (completed.isEmpty()) "None" else completed.joinToString("\n") { "- ${it.id}: ${it.description}" }
        val fileSummary = if (files.isEmpty()) "No files in project yet." else files.joinToString("\n") { "- ${it.path} (${it.content.length} bytes)" }
        val rollbackStr = if (wasRolledBack) "Yes, previous changes failed verification and were rolled back to the prior snapshot." else "No (changes were not applied or rejected before applying)."

        return """
ORIGINAL GOAL:
${plan.goal}

COMPLETE PLAN:
$planSummary

COMPLETED TASKS:
$completedStr

CURRENT TASK:
- ID: ${task.id}
- Description: ${task.description}

CURRENT PROJECT STATE (Files):
$fileSummary

PREVIOUS ATTEMPT FAILED:
- Exact Error: $error
- Rollback Occurred: $rollbackStr
- Previous Proposal Summary: ${previousSummary ?: "None"}

Please carefully analyze the error and the current state of files, and propose the corrected code changes required to implement this specific task.
"""
    }

    fun parseAndValidatePlan(jsonString: String): AutonomousPlan {
        val cleanStr = jsonString.replace(Regex("```json\\s*"), "").replace(Regex("```\\s*"), "").trim()
        if (cleanStr.isEmpty()) {
            throw IllegalArgumentException("Plan JSON string cannot be empty")
        }
        val json = try {
            JSONObject(cleanStr)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed JSON: ${e.message}", e)
        }

        if (!json.has("goal") || json.isNull("goal")) {
            throw IllegalArgumentException("Missing required field: 'goal'")
        }
        val goal = json.getString("goal")
        if (goal.isBlank()) {
            throw IllegalArgumentException("Goal cannot be blank")
        }

        if (!json.has("tasks") || json.isNull("tasks")) {
            throw IllegalArgumentException("Missing required field: 'tasks'")
        }
        val tasksArray = json.getJSONArray("tasks")
        if (tasksArray.length() == 0) {
            throw IllegalArgumentException("Task list cannot be empty")
        }
        if (tasksArray.length() > 20) {
            throw IllegalArgumentException("Too many tasks: maximum allowed is 20 (got ${tasksArray.length()})")
        }

        val tasks = mutableListOf<AutonomousTask>()
        for (i in 0 until tasksArray.length()) {
            val tObj = tasksArray.getJSONObject(i)
            if (!tObj.has("id") || tObj.isNull("id")) {
                throw IllegalArgumentException("Task at index $i missing 'id'")
            }
            val id = tObj.getString("id")
            if (id.isBlank()) {
                throw IllegalArgumentException("Task ID at index $i cannot be blank")
            }

            if (!tObj.has("description") || tObj.isNull("description")) {
                throw IllegalArgumentException("Task $id missing 'description'")
            }
            val desc = tObj.getString("description")
            if (desc.isBlank()) {
                throw IllegalArgumentException("Task description for $id cannot be blank")
            }

            val dependsOn = mutableListOf<String>()
            if (tObj.has("dependsOn") && !tObj.isNull("dependsOn")) {
                val depArray = tObj.getJSONArray("dependsOn")
                for (j in 0 until depArray.length()) {
                    dependsOn.add(depArray.getString(j))
                }
            }
            tasks.add(AutonomousTask(id = id, description = desc, dependsOn = dependsOn))
        }

        val taskIds = tasks.map { it.id }
        val uniqueIds = taskIds.toSet()
        if (uniqueIds.size != tasks.size) {
            throw IllegalArgumentException("Duplicate task IDs detected")
        }

        for (t in tasks) {
            for (dep in t.dependsOn) {
                if (!uniqueIds.contains(dep)) {
                    throw IllegalArgumentException("Task ${t.id} depends on unknown task: $dep")
                }
            }
        }

        if (hasCycle(tasks)) {
            throw IllegalArgumentException("Dependency cycle detected in plan")
        }

        return AutonomousPlan(goal, tasks)
    }

    fun hasCycle(tasks: List<AutonomousTask>): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val taskMap = tasks.associateBy { it.id }

        fun dfs(taskId: String): Boolean {
            if (recursionStack.contains(taskId)) return true
            if (visited.contains(taskId)) return false
            visited.add(taskId)
            recursionStack.add(taskId)
            val task = taskMap[taskId] ?: return false
            for (dep in task.dependsOn) {
                if (dfs(dep)) return true
            }
            recursionStack.remove(taskId)
            return false
        }

        for (task in tasks) {
            if (dfs(task.id)) return true
        }
        return false
    }

    fun getNextReadyTask(plan: AutonomousPlan): AutonomousTask? {
        val completedIds = plan.tasks.filter { it.status == AutonomousTaskStatus.COMPLETED }.map { it.id }.toSet()
        return plan.tasks.find { t ->
            t.status == AutonomousTaskStatus.PENDING && t.dependsOn.all { completedIds.contains(it) }
        }
    }
}
