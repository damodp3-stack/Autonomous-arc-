package com.example.ai

import com.example.data.MessageEntity
import com.example.data.MessageRepository
import com.example.data.ProjectFileEntity
import com.example.data.ProjectFileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    var status: AutonomousTaskStatus = AutonomousTaskStatus.PENDING
)

data class AutonomousPlan(
    val goal: String,
    val tasks: List<AutonomousTask>
)

enum class AutonomousState {
    IDLE, PLANNING, GENERATING, VALIDATING, APPLYING, VERIFYING, CONTINUING, COMPLETED, FAILED, BLOCKED, STOPPED
}

class AutonomousExecutionEngine(
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

    private val _lastAction = MutableStateFlow<String?>("Ready")
    val lastAction: StateFlow<String?> = _lastAction.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _currentPlan = MutableStateFlow<AutonomousPlan?>(null)
    val currentPlan: StateFlow<AutonomousPlan?> = _currentPlan.asStateFlow()

    private val _currentTaskIndex = MutableStateFlow(-1)
    val currentTaskIndex: StateFlow<Int> = _currentTaskIndex.asStateFlow()

    private val maxConsecutiveFailures = 2
    private var runJob: Job? = null

    suspend fun start(goal: String, providerName: String, projectName: String) = coroutineScope {
        if (_state.value != AutonomousState.IDLE && _state.value != AutonomousState.COMPLETED && _state.value != AutonomousState.STOPPED && _state.value != AutonomousState.FAILED && _state.value != AutonomousState.BLOCKED) {
            return@coroutineScope
        }

        _iteration.value = 0
        _lastError.value = null
        _lastAction.value = "Starting autonomous run for goal"
        
        runJob = coroutineContext[Job]

        try {
            val aiProvider = aiFactory.getProvider(projectName, providerName)

            _state.value = AutonomousState.PLANNING
            _lastAction.value = "Creating execution plan..."

            val files = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
            val projectContext = ProjectContext(projectName, files, null)

            // Generate Plan
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
            val planJsonString = try {
                aiProvider.generateResponse(planPrompt, emptyList(), projectContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AutonomousState.FAILED
                _lastError.value = "Failed to fetch plan: ${e.message}"
                messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run FAILED to fetch plan: ${e.message}", isUser = false))
                return@coroutineScope
            }

            val plan = try {
                parseAndValidatePlan(planJsonString)
            } catch (e: Exception) {
                _state.value = AutonomousState.FAILED
                _lastError.value = "Failed to parse plan: ${e.message}"
                messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run FAILED to parse plan: ${e.message}\n\nResponse was:\n$planJsonString", isUser = false))
                return@coroutineScope
            }
            
            _currentPlan.value = plan
            messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Plan Created: ${plan.tasks.size} tasks.", isUser = false))

            var loopCount = 0
            
            while (isActive && loopCount < _maxIterations.value) {
                val currentPlanVal = _currentPlan.value ?: break
                
                val nextTask = getNextReadyTask(currentPlanVal)
                if (nextTask == null) {
                    if (currentPlanVal.tasks.all { it.status == AutonomousTaskStatus.COMPLETED }) {
                        _state.value = AutonomousState.COMPLETED
                        _lastAction.value = "All tasks completed successfully."
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run Completed Successfully.", isUser = false))
                        return@coroutineScope
                    } else if (currentPlanVal.tasks.any { it.status == AutonomousTaskStatus.BLOCKED || it.status == AutonomousTaskStatus.FAILED }) {
                        _state.value = AutonomousState.BLOCKED
                        _lastAction.value = "Execution blocked due to failed tasks."
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run BLOCKED. Some tasks failed or are unreachable.", isUser = false))
                        return@coroutineScope
                    } else {
                        // This shouldn't happen unless there's a bug or cycle, but handle it
                        _state.value = AutonomousState.BLOCKED
                        _lastAction.value = "Execution blocked. No ready tasks found."
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run BLOCKED. Dependency deadlock.", isUser = false))
                        return@coroutineScope
                    }
                }
                
                val taskIndex = currentPlanVal.tasks.indexOf(nextTask)
                _currentTaskIndex.value = taskIndex
                
                loopCount++
                _iteration.value = loopCount
                
                nextTask.status = AutonomousTaskStatus.RUNNING
                
                var consecutiveFailures = 0
                var currentContextRequest = buildTaskPrompt(currentPlanVal, nextTask)
                
                while (isActive && consecutiveFailures < maxConsecutiveFailures) {
                    _state.value = AutonomousState.GENERATING
                    _lastAction.value = "Generating proposal for task: ${nextTask.id}"
                    
                    val currentFiles = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
                    val currentProjectContext = ProjectContext(projectName, currentFiles, null)
                    
                    val proposal = try {
                        aiProvider.proposeCodeChanges(currentContextRequest, currentProjectContext)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        consecutiveFailures++
                        _lastError.value = "Generation failed: ${e.message}"
                        currentContextRequest = buildRetryPrompt(currentPlanVal, nextTask, "Generation failed: ${e.message}")
                        continue
                    }
                    
                    if (proposal.changes.isEmpty()) {
                        // Task requires no changes or AI thinks it's done
                        nextTask.status = AutonomousTaskStatus.COMPLETED
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Task ${nextTask.id} completed (No changes needed).", isUser = false))
                        break // Break out of task retry loop, proceed to next task
                    }
                    
                    _state.value = AutonomousState.APPLYING
                    _lastAction.value = "Applying task ${nextTask.id}: ${proposal.summary}"
                    val result = codeChangeApplier.applyProposal(projectId, proposal)
                    
                    if (result is ApplyResult.Success) {
                        _state.value = AutonomousState.VERIFYING
                        _lastAction.value = "Verifying task ${nextTask.id}"
                        
                        val verifyResult = verifyChanges(projectId, proposal)
                        if (verifyResult) {
                            nextTask.status = AutonomousTaskStatus.COMPLETED
                            _state.value = AutonomousState.CONTINUING
                            _lastAction.value = "Applied and verified task ${nextTask.id}"
                            
                            messageRepository.insert(MessageEntity(
                                projectId = projectId,
                                text = "Task ${nextTask.id} Applied:\n\nSummary: ${proposal.summary}",
                                isUser = false
                            ))
                            break // Break out of task retry loop, proceed to next task
                        } else {
                            // Verification failed, rollback
                            codeChangeApplier.rollback(projectId, result.createdFileIds, result.snapshot)
                            consecutiveFailures++
                            _lastError.value = "Verification failed for task ${nextTask.id}, rolled back."
                            currentContextRequest = buildRetryPrompt(currentPlanVal, nextTask, "Verification failed (e.g., changes were not saved correctly). Please revise.")
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
                        _lastError.value = "Failed to apply task ${nextTask.id}: $errorMsg"
                        currentContextRequest = buildRetryPrompt(currentPlanVal, nextTask, "Failed to apply with error: $errorMsg. Please retry or adjust your approach.")
                    }
                }
                
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    nextTask.status = AutonomousTaskStatus.BLOCKED
                    _state.value = AutonomousState.BLOCKED
                    _lastAction.value = "Task ${nextTask.id} blocked after $consecutiveFailures failures."
                    messageRepository.insert(MessageEntity(projectId = projectId, text = "Task ${nextTask.id} BLOCKED: Exceeded retry limit.", isUser = false))
                    return@coroutineScope
                }
            }
            
            if (loopCount >= _maxIterations.value) {
                _state.value = AutonomousState.BLOCKED
                _lastAction.value = "Reached max iterations (${_maxIterations.value})."
                messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run paused after reaching maximum iteration limit (${_maxIterations.value}).", isUser = false))
            }
            
        } catch (e: CancellationException) {
            _state.value = AutonomousState.STOPPED
            _lastAction.value = "Execution stopped by user."
            messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run STOPPED.", isUser = false))
            throw e
        } catch (e: Exception) {
            _state.value = AutonomousState.FAILED
            _lastError.value = "Unexpected error: ${e.message}"
            messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run FAILED: ${e.message}", isUser = false))
        }
    }

    fun stop() {
        if (_state.value != AutonomousState.IDLE && _state.value != AutonomousState.STOPPED && _state.value != AutonomousState.COMPLETED && _state.value != AutonomousState.BLOCKED && _state.value != AutonomousState.FAILED) {
            _state.value = AutonomousState.STOPPED
            _lastAction.value = "Stopping..."
            runJob?.cancel()
        }
    }

    fun setMaxIterations(max: Int) {
        _maxIterations.value = max
    }

    private suspend fun verifyChanges(projectId: String, proposal: CodeChangeProposal): Boolean {
        val files = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
        val filePaths = files.map { it.path }
        
        for (change in proposal.changes) {
            when (change.operation) {
                FileOperation.CREATE -> {
                    if (!filePaths.contains(change.filePath)) return false
                }
                FileOperation.DELETE -> {
                    if (filePaths.contains(change.filePath)) return false
                }
                FileOperation.RENAME -> {
                    val newPath = change.newFilePath ?: return false
                    if (filePaths.contains(change.filePath)) return false
                    if (!filePaths.contains(newPath)) return false
                }
                FileOperation.MODIFY -> {
                    if (!filePaths.contains(change.filePath)) return false
                }
            }
        }
        return true
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
    
    private fun buildRetryPrompt(plan: AutonomousPlan, task: AutonomousTask, error: String): String {
        return """
${buildTaskPrompt(plan, task)}

PREVIOUS ATTEMPT FAILED:
$error

Please analyze the failure reason and adjust your approach.
"""
    }
    
    fun parseAndValidatePlan(jsonString: String): AutonomousPlan {
        val cleanStr = jsonString.replace(Regex("```json\\s*"), "").replace(Regex("```\\s*"), "").trim()
        val json = JSONObject(cleanStr)
        val goal = json.getString("goal")
        val tasksArray = json.getJSONArray("tasks")
        
        val tasks = mutableListOf<AutonomousTask>()
        for (i in 0 until tasksArray.length()) {
            val tObj = tasksArray.getJSONObject(i)
            val id = tObj.getString("id")
            val desc = tObj.getString("description")
            val dependsOn = mutableListOf<String>()
            if (tObj.has("dependsOn")) {
                val depArray = tObj.getJSONArray("dependsOn")
                for (j in 0 until depArray.length()) {
                    dependsOn.add(depArray.getString(j))
                }
            }
            tasks.add(AutonomousTask(id, desc, dependsOn))
        }
        
        if (tasks.isEmpty()) throw Exception("Plan tasks cannot be empty")
        if (tasks.size > 20) throw Exception("Too many tasks (max 20)")
        
        val taskIds = tasks.map { it.id }.toSet()
        if (taskIds.size != tasks.size) throw Exception("Duplicate task IDs found")
        
        for (t in tasks) {
            if (t.id.isBlank()) throw Exception("Task ID cannot be blank")
            if (t.description.isBlank()) throw Exception("Task description cannot be blank")
            for (dep in t.dependsOn) {
                if (!taskIds.contains(dep)) throw Exception("Task ${t.id} depends on unknown task $dep")
            }
        }
        
        if (hasCycle(tasks)) throw Exception("Dependency cycle detected in plan")
        
        return AutonomousPlan(goal, tasks)
    }
    
    private fun hasCycle(tasks: List<AutonomousTask>): Boolean {
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
    
    private fun getNextReadyTask(plan: AutonomousPlan): AutonomousTask? {
        val completedIds = plan.tasks.filter { it.status == AutonomousTaskStatus.COMPLETED }.map { it.id }.toSet()
        return plan.tasks.find { t ->
            t.status == AutonomousTaskStatus.PENDING && t.dependsOn.all { completedIds.contains(it) }
        }
    }
}
