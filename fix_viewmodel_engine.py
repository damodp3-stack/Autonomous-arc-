with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

import re

# We need to add `AutonomousExecutionEngine` initialization to WorkspaceViewModel

init_engine = """    private val _isAutonomousMode = MutableStateFlow(false)
    val isAutonomousMode: StateFlow<Boolean> = _isAutonomousMode.asStateFlow()

    val autonomousEngine = com.example.ai.AutonomousExecutionEngine(
        projectId, fileRepository, messageRepository, codeChangeApplier, aiFactory
    )
"""

content = re.sub(r'    private val _isAutonomousMode = MutableStateFlow\(false\)\n    val isAutonomousMode: StateFlow<Boolean> = _isAutonomousMode\.asStateFlow\(\)', init_engine, content)

# Remove old `_isAutonomousRunning` stuff
content = re.sub(r'    private val _isAutonomousRunning = MutableStateFlow\(false\)\n    val isAutonomousRunning: StateFlow<Boolean> = _isAutonomousRunning\.asStateFlow\(\)', '', content)
content = re.sub(r'        _isAutonomousRunning\.value = false\n', '        autonomousEngine.stop()\n', content)

old_start = """    private fun startAutonomousRun(request: String) {
        viewModelScope.launch {
            messageRepository.insert(MessageEntity(projectId = projectId, text = request, isUser = true))
            _isAutonomousRunning.value = true
            
            var loopCount = 0
            val maxLoops = 5
            var currentContextRequest = "Task: $request\\nThis is an autonomous loop. Analyze the current context and propose the next batch of changes. If the task is fully complete, return an empty 'changes' array."

            while (loopCount < maxLoops && _isAutonomousRunning.value) {
                loopCount++
                _proposalState.value = ProposalState.GENERATING
                _currentProposal.value = null
                _proposalError.value = null

                try {
                    val aiProvider = aiFactory.getProvider(_projectName.value, _selectedProvider.value)
                    val projectContext = ProjectContext(
                        projectName = _projectName.value,
                        files = files.value,
                        currentOpenFile = _selectedFile.value
                    )
                    
                    val proposal = aiProvider.proposeCodeChanges(currentContextRequest, projectContext)
                    
                    if (proposal.changes.isEmpty()) {
                        messageRepository.insert(MessageEntity(
                            projectId = projectId,
                            text = "Autonomous Run Completed.\\n\\nSummary: ${proposal.summary}",
                            isUser = false
                        ))
                        _proposalState.value = ProposalState.IDLE
                        break
                    }

                    // Apply automatically
                    _proposalState.value = ProposalState.APPLYING
                    val result = codeChangeApplier.applyProposal(projectId, proposal)
                    _applyResult.value = result

                    if (result is com.example.ai.ApplyResult.Success) {
                        messageRepository.insert(MessageEntity(
                            projectId = projectId,
                            text = "Autonomous Step $loopCount Applied:\\n\\nSummary: ${proposal.summary}",
                            isUser = false
                        ))
                        
                        val currentFile = _selectedFile.value
                        if (currentFile != null) {
                            val updatedFile = fileRepository.getFile(currentFile.id)
                            if (updatedFile != null) {
                                _editorContent.value = updatedFile.content
                                _selectedFile.value = updatedFile
                            } else {
                                _selectedFile.value = null
                                _editorContent.value = ""
                            }
                        }
                        
                        currentContextRequest = "Previous step applied successfully: ${proposal.summary}. Continue with the next step for: $request. If the task is fully complete, return an empty 'changes' array."
                    } else {
                        // Failed to apply
                        _proposalState.value = ProposalState.FAILED
                        _proposalError.value = "Failed to apply step $loopCount: ${result.javaClass.simpleName}"
                        _isAutonomousRunning.value = false
                        break
                    }
                } catch (e: Exception) {
                    _proposalError.value = e.message ?: "An unknown error occurred"
                    _proposalState.value = ProposalState.FAILED
                    _isAutonomousRunning.value = false
                    break
                }
            }
            
            if (loopCount >= maxLoops && _isAutonomousRunning.value) {
                 messageRepository.insert(MessageEntity(
                    projectId = projectId,
                    text = "Autonomous Run paused after reaching maximum iteration limit ($maxLoops).",
                    isUser = false
                ))
            }
            
            _isAutonomousRunning.value = false
        }
    }"""

new_start = """    private fun startAutonomousRun(request: String) {
        viewModelScope.launch {
            messageRepository.insert(MessageEntity(projectId = projectId, text = request, isUser = true))
            autonomousEngine.start(request, _selectedProvider.value, _projectName.value)
            
            // Reload the selected file if it changed
            val currentFile = _selectedFile.value
            if (currentFile != null) {
                val updatedFile = fileRepository.getFile(currentFile.id)
                if (updatedFile != null) {
                    _editorContent.value = updatedFile.content
                    _selectedFile.value = updatedFile
                } else {
                    _selectedFile.value = null
                    _editorContent.value = ""
                }
            }
        }
    }"""

content = content.replace(old_start, new_start)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)
