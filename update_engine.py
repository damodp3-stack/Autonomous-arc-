with open('app/src/main/java/com/example/ai/AutonomousExecutionEngine.kt', 'r') as f:
    content = f.read()

import re

# We need to add structured planning.
# 4. Structured plan
# AutonomousPlan, AutonomousTask, AutonomousTaskStatus

models = """
enum class AutonomousTaskStatus {
    PENDING, RUNNING, COMPLETED, FAILED, BLOCKED
}

data class AutonomousTask(
    val id: String,
    val description: String,
    var status: AutonomousTaskStatus = AutonomousTaskStatus.PENDING
)

data class AutonomousPlan(
    val goal: String,
    val tasks: List<AutonomousTask>
)

"""

# Insert models before enum class AutonomousState
content = content.replace("enum class AutonomousState {", models + "enum class AutonomousState {")

# Expose task state
task_state = """    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _currentPlan = MutableStateFlow<AutonomousPlan?>(null)
    val currentPlan: StateFlow<AutonomousPlan?> = _currentPlan.asStateFlow()

    private val _currentTaskIndex = MutableStateFlow(-1)
    val currentTaskIndex: StateFlow<Int> = _currentTaskIndex.asStateFlow()
"""
content = content.replace("    private val _lastError = MutableStateFlow<String?>(null)\n    val lastError: StateFlow<String?> = _lastError.asStateFlow()\n", task_state)


start_body_old = """        try {
            var consecutiveFailures = 0
            val aiProvider = aiFactory.getProvider(projectName, providerName)
            
            var loopCount = 0
            var currentContextRequest = "Task: $goal\\nThis is an autonomous loop. Analyze the current context and propose the next batch of changes. If the task is fully complete, return an empty 'changes' array."

            while (isActive && loopCount < _maxIterations.value) {"""

start_body_new = """        try {
            var consecutiveFailures = 0
            val aiProvider = aiFactory.getProvider(projectName, providerName)
            
            _state.value = AutonomousState.PLANNING
            _lastAction.value = "Creating execution plan..."
            
            val files = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
            var projectContext = ProjectContext(projectName, files, null)
            
            // Generate Plan
            val planPrompt = "Goal: $goal\\nCreate a structured implementation plan. Return a CodeChangeProposal where the 'summary' contains the plan, and 'changes' contains one FileChange representing the plan.xml or similar, or just return an empty 'changes' array."
            val planProposal = try {
                aiProvider.proposeCodeChanges(planPrompt, projectContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                 _state.value = AutonomousState.FAILED
                _lastError.value = "Failed to create plan: ${e.message}"
                messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run FAILED to plan: ${e.message}", isUser = false))
                return@coroutineScope
            }
            
            // For now, create a single task if we can't parse a complex JSON plan from the provider yet
            val initialPlan = AutonomousPlan(
                goal = goal,
                tasks = listOf(
                    AutonomousTask(id = "task-1", description = "Execute implementation for: $goal")
                )
            )
            
            _currentPlan.value = initialPlan
            _currentTaskIndex.value = 0
            
            var loopCount = 0
            var currentContextRequest = "Task: ${initialPlan.tasks[0].description}\\nThis is an autonomous loop. Analyze the current context and propose the next batch of changes for this specific task. If the task is fully complete, return an empty 'changes' array."

            while (isActive && loopCount < _maxIterations.value && _currentTaskIndex.value < initialPlan.tasks.size) {"""

content = content.replace(start_body_old, start_body_new)


# Loop completion logic

loop_complete_old = """                if (proposal.changes.isEmpty()) {
                    _state.value = AutonomousState.COMPLETED
                    _lastAction.value = "Task completed successfully."
                    messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run Completed.\\n\\nSummary: ${proposal.summary}", isUser = false))
                    return@coroutineScope
                }"""

loop_complete_new = """                if (proposal.changes.isEmpty()) {
                    val currentPlanVal = _currentPlan.value
                    if (currentPlanVal != null) {
                        val taskIdx = _currentTaskIndex.value
                        if (taskIdx >= 0 && taskIdx < currentPlanVal.tasks.size) {
                            currentPlanVal.tasks[taskIdx].status = AutonomousTaskStatus.COMPLETED
                        }
                        
                        _currentTaskIndex.value = taskIdx + 1
                        
                        if (_currentTaskIndex.value >= currentPlanVal.tasks.size) {
                            _state.value = AutonomousState.COMPLETED
                            _lastAction.value = "All tasks completed successfully."
                            messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run Completed.\\n\\nSummary: ${proposal.summary}", isUser = false))
                            return@coroutineScope
                        } else {
                            val nextTask = currentPlanVal.tasks[_currentTaskIndex.value]
                            nextTask.status = AutonomousTaskStatus.RUNNING
                            currentContextRequest = "Next Task: ${nextTask.description}\\nAnalyze the current context and propose the next batch of changes for this task. If the task is fully complete, return an empty 'changes' array."
                            continue
                        }
                    } else {
                        _state.value = AutonomousState.COMPLETED
                        _lastAction.value = "Task completed successfully."
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run Completed.\\n\\nSummary: ${proposal.summary}", isUser = false))
                        return@coroutineScope
                    }
                }"""
                
content = content.replace(loop_complete_old, loop_complete_new)


# Update the context retrieval in the loop
context_old = """                val files = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
                val projectContext = ProjectContext(projectName, files, null)

                val proposal: CodeChangeProposal"""

context_new = """                val currentFiles = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
                val currentProjectContext = ProjectContext(projectName, currentFiles, null)

                val proposal: CodeChangeProposal"""
content = content.replace(context_old, context_new)
content = content.replace("aiProvider.proposeCodeChanges(currentContextRequest, projectContext)", "aiProvider.proposeCodeChanges(currentContextRequest, currentProjectContext)")

with open('app/src/main/java/com/example/ai/AutonomousExecutionEngine.kt', 'w') as f:
    f.write(content)
