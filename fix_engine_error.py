with open('app/src/main/java/com/example/ai/AutonomousExecutionEngine.kt', 'r') as f:
    content = f.read()

import re

old_error_handling = """                    val errorMsg = (result as ApplyResult.Error).message
                    consecutiveFailures++
                    _lastError.value = "Failed to apply: $errorMsg"
                    
                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        _state.value = AutonomousState.BLOCKED
                        _lastAction.value = "Blocked after $consecutiveFailures apply failures."
                        messageRepository.insert(MessageEntity(projectId = projectId, text = "Autonomous Run BLOCKED: Failed to apply step $loopCount: $errorMsg", isUser = false))
                        return@coroutineScope
                    }
                    currentContextRequest = "Previous attempt failed to apply with error: $errorMsg. Please retry or adjust your approach.\"
"""

new_error_handling = """                    val errorMsg = when(result) {
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
                    currentContextRequest = "Previous attempt failed to apply with error: $errorMsg. Please retry or adjust your approach.\"
"""

content = content.replace(old_error_handling, new_error_handling)

with open('app/src/main/java/com/example/ai/AutonomousExecutionEngine.kt', 'w') as f:
    f.write(content)
