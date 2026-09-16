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
            var consecutiveFailures = 0
            val aiProvider = aiFactory.getProvider(projectName, providerName)
            
            var loopCount = 0
            var currentContextRequest = "Task: $goal\nThis is an autonomous loop. Analyze the current context and propose the next batch of changes. If the task is fully complete, return an empty 'changes' array."

            while (isActive && loopCount < _maxIterations.value) {
                loopCount++
                _iteration.value = loopCount
                _state.value = AutonomousState.GENERATING
                _lastAction.value = "Generating proposal for step $loopCount"

                val files = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
                val projectContext = ProjectContext(projectName, files, null)

                val proposal: CodeChangeProposal
                try {
                    proposal = aiProvider.proposeCodeChanges(currentContextRequest, projectContext)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    consecutiveFailures++
                    _lastError.value = "Generation failed: ${e.message}"
                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        _state.value = AutonomousState.BLOCKED
                        _lastAction.value = "Blocked after $consecutiveFailures generation failures."
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run BLOCKED: ${_lastError.value}", isUser = false))
                        return@coroutineScope
                    }
                    continue
                }

                if (proposal.changes.isEmpty()) {
                    _state.value = AutonomousState.COMPLETED
                    _lastAction.value = "Task completed successfully."
                    messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run Completed.\n\nSummary: ${proposal.summary}", isUser = false))
                    return@coroutineScope
                }

                _state.value = AutonomousState.APPLYING
                _lastAction.value = "Applying: ${proposal.summary}"

                val result = codeChangeApplier.applyProposal(projectId, proposal)
                
                if (result is ApplyResult.Success) {
                    _state.value = AutonomousState.VERIFYING
                    _lastAction.value = "Verifying step $loopCount"
                    
                    val verifyResult = verifyChanges(projectId, proposal)
                    if (verifyResult) {
                        _state.value = AutonomousState.CONTINUING
                        _lastAction.value = "Applied and verified step $loopCount"
                        consecutiveFailures = 0
                        
                        messageRepository.insert(MessageEntity(
                            projectId = projectId,
                            text = "Autonomous Step $loopCount Applied:\n\nSummary: ${proposal.summary}",
                            isUser = false
                        ))
                        
                        currentContextRequest = "Previous step applied successfully: ${proposal.summary}. Continue with the next step for: $goal. If the task is fully complete, return an empty 'changes' array."
                    } else {
                        // Verification failed, rollback
                        codeChangeApplier.rollback(projectId, result.createdFileIds, result.snapshot)
                        consecutiveFailures++
                        _lastError.value = "Verification failed for step $loopCount, rolled back."
                        if (consecutiveFailures >= maxConsecutiveFailures) {
                            _state.value = AutonomousState.BLOCKED
                            _lastAction.value = "Blocked after $consecutiveFailures failures."
                            messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run BLOCKED: Verification failed.", isUser = false))
                            return@coroutineScope
                        }
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
                    _lastError.value = "Failed to apply: $errorMsg"
                    
                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        _state.value = AutonomousState.BLOCKED
                        _lastAction.value = "Blocked after $consecutiveFailures apply failures."
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run BLOCKED: Failed to apply step $loopCount: $errorMsg", isUser = false))
                        return@coroutineScope
                    }
                    currentContextRequest = "Previous attempt failed to apply with error: $errorMsg. Please retry or adjust your approach."
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
}
