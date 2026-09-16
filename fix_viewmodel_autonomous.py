with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

new_states = """    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _isAutonomousMode = MutableStateFlow(false)
    val isAutonomousMode: StateFlow<Boolean> = _isAutonomousMode.asStateFlow()
    
    private val _isAutonomousRunning = MutableStateFlow(false)
    val isAutonomousRunning: StateFlow<Boolean> = _isAutonomousRunning.asStateFlow()

    fun toggleAutonomousMode() {
        _isAutonomousMode.value = !_isAutonomousMode.value
    }
    
    fun stopAutonomousRun() {
        _isAutonomousRunning.value = false
    }
"""

content = content.replace("    private val _isBuilding = MutableStateFlow(false)\n    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()", new_states)

old_propose = """    fun proposeChange(request: String) {
        if (request.isBlank()) return
        viewModelScope.launch {
            messageRepository.insert(MessageEntity(projectId = projectId, text = request, isUser = true))
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

                val proposal = aiProvider.proposeCodeChanges(request, projectContext)
                _currentProposal.value = proposal
                
                messageRepository.insert(MessageEntity(
                    projectId = projectId,
                    text = "AI proposed changes\\n\\nSummary: ${proposal.summary}",
                    isUser = false
                ))
                
                _proposalState.value = ProposalState.READY_FOR_REVIEW
            } catch (e: Exception) {
                _proposalError.value = e.message ?: "An unknown error occurred"
                _proposalState.value = ProposalState.FAILED
            }
        }
    }"""

new_propose = """    fun proposeChange(request: String) {
        if (request.isBlank()) return
        if (_isAutonomousMode.value) {
            startAutonomousRun(request)
            return
        }
        viewModelScope.launch {
            messageRepository.insert(MessageEntity(projectId = projectId, text = request, isUser = true))
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

                val proposal = aiProvider.proposeCodeChanges(request, projectContext)
                _currentProposal.value = proposal
                
                messageRepository.insert(MessageEntity(
                    projectId = projectId,
                    text = "AI proposed changes\\n\\nSummary: ${proposal.summary}",
                    isUser = false
                ))
                
                _proposalState.value = ProposalState.READY_FOR_REVIEW
            } catch (e: Exception) {
                _proposalError.value = e.message ?: "An unknown error occurred"
                _proposalState.value = ProposalState.FAILED
            }
        }
    }
    
    private fun startAutonomousRun(request: String) {
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

content = content.replace(old_propose, new_propose)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)
