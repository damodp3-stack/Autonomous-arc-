package com.example.ui




import androidx.lifecycle.ViewModel



import androidx.lifecycle.ViewModelProvider



import androidx.lifecycle.viewModelScope


import com.example.ai.AIProvider


import com.example.data.MessageEntity


import com.example.data.MessageRepository


import com.example.data.ProjectRepository


import kotlinx.coroutines.flow.MutableStateFlow


import kotlinx.coroutines.flow.SharingStarted


import kotlinx.coroutines.flow.StateFlow


import kotlinx.coroutines.flow.asStateFlow


import kotlinx.coroutines.flow.stateIn


import kotlinx.coroutines.flow.update


import kotlinx.coroutines.launch


import com.example.ai.ProjectContext


import com.example.data.ProjectFileEntity


import com.example.data.ProjectFileRepository

enum class ProposalState {
    IDLE, GENERATING, READY_FOR_REVIEW, REVIEWING, APPROVED, APPLYING, APPLIED, REJECTED, FAILED
}

class WorkspaceViewModel(
    val projectId: String,
    private val messageRepository: MessageRepository,
    private val projectRepository: ProjectRepository,
    val fileRepository: ProjectFileRepository,
    val aiFactory: com.example.ai.AIFactory,
    val apiKeyManager: com.example.ai.APIKeyManager,
    val configRepository: com.example.data.AIProviderConfigRepository? = null,
    val usageRepository: com.example.data.UsageRepository? = null
) : ViewModel() {

    private val codeChangeApplier = com.example.ai.CodeChangeApplier(fileRepository)

    val availableProviders = listOf("Gemini", "OpenAI", "Anthropic")
    private val _selectedProvider = MutableStateFlow("Gemini")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _selectedModel = MutableStateFlow(com.example.ai.AIModelRegistry.getDefaultModel("Gemini"))
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _availableModels = MutableStateFlow(com.example.ai.AIModelRegistry.getAvailableModels("Gemini"))
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _latestUsage = MutableStateFlow<com.example.ai.TokenUsage?>(null)
    val latestUsage: StateFlow<com.example.ai.TokenUsage?> = _latestUsage.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    private val _proposalState = MutableStateFlow(ProposalState.IDLE)
    val proposalState: StateFlow<ProposalState> = _proposalState.asStateFlow()

    private val _currentProposal = MutableStateFlow<com.example.ai.CodeChangeProposal?>(null)
    val currentProposal: StateFlow<com.example.ai.CodeChangeProposal?> = _currentProposal.asStateFlow()

    private val _proposalError = MutableStateFlow<String?>(null)
    val proposalError: StateFlow<String?> = _proposalError.asStateFlow()

    private val _applyResult = MutableStateFlow<com.example.ai.ApplyResult?>(null)
    val applyResult: StateFlow<com.example.ai.ApplyResult?> = _applyResult.asStateFlow()

    fun setProvider(providerName: String) {
        _selectedProvider.value = providerName
        val cached = aiFactory.modelSelectionRepository.getCachedModels(providerName)
        _availableModels.value = if (cached.isNotEmpty()) cached.map { it.id } else emptyList()
        viewModelScope.launch {
            val savedModel = configRepository?.getConfigByProviderType(providerName)?.selectedModel
            val newModel = savedModel?.ifBlank { null }
                ?: cached.firstOrNull { it.isVerifiedLive }?.id
                ?: cached.firstOrNull()?.id
                ?: com.example.ai.AIModelRegistry.getDefaultModel(providerName)
            _selectedModel.value = newModel
            configRepository?.setActiveProviderType(providerName)
        }
    }

    fun setModel(modelName: String) {
        val normalized = com.example.ai.ModelIdNormalizer.normalize(modelName)
        val resolved = com.example.ai.AIModelRegistry.resolveModel(_selectedProvider.value, normalized)
        _selectedModel.value = resolved
        viewModelScope.launch {
            configRepository?.updateModelForProvider(_selectedProvider.value, resolved)
        }
    }

    fun saveProviderSettings(providerType: String, apiKey: String, model: String) {
        val normalized = com.example.ai.ModelIdNormalizer.normalize(model)
        val resolvedModel = com.example.ai.AIModelRegistry.resolveModel(providerType, normalized)
        apiKeyManager.saveApiKey(providerType, apiKey.trim())
        viewModelScope.launch {
            configRepository?.updateModelForProvider(providerType, resolvedModel)
            if (_selectedProvider.value.equals(providerType, ignoreCase = true)) {
                _selectedModel.value = resolvedModel
            }
        }
    }

    fun testConnection(providerType: String, apiKey: String, model: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val result = aiFactory.testConnection(providerType, apiKey, model)
            _connectionTestResult.value = if (result.isSuccess) {
                result.getOrNull()
            } else {
                "Error: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
            _isTestingConnection.value = false
        }
    }

    fun refreshModelsForProvider(providerType: String, apiKey: String) {
        viewModelScope.launch {
            val result = aiFactory.modelSelectionRepository.refreshModels(providerType, apiKey)
            if (result.isSuccess) {
                val list = result.getOrNull().orEmpty()
                if (list.isNotEmpty()) {
                    _availableModels.value = list.map { it.id }
                }
            }
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    val messages: StateFlow<List<MessageEntity>> = messageRepository.getMessagesForProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val files: StateFlow<List<ProjectFileEntity>> = fileRepository.getFilesForProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedFile = MutableStateFlow<ProjectFileEntity?>(null)
    val selectedFile: StateFlow<ProjectFileEntity?> = _selectedFile.asStateFlow()

    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _projectName = MutableStateFlow("Loading...")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _isAutonomousMode = MutableStateFlow(false)
    val isAutonomousMode: StateFlow<Boolean> = _isAutonomousMode.asStateFlow()

    val autonomousEngine = com.example.ai.AutonomousExecutionEngine(
        projectId, fileRepository, messageRepository, codeChangeApplier, aiFactory
    )

    


    fun toggleAutonomousMode() {
        _isAutonomousMode.value = !_isAutonomousMode.value
    }
    
    fun startAutonomousRun(goal: String) {
        if (goal.isBlank()) return
        viewModelScope.launch {
            autonomousEngine.start(goal, _selectedProvider.value, _projectName.value, _selectedModel.value)
        }
    }

    fun stopAutonomousRun() {
        autonomousEngine.stop()
    }


    init {
        viewModelScope.launch {
            fileRepository.syncProjectFilesToSystem(projectId)
            projectRepository.getProject(projectId).collect { project ->
                if (project != null) {
                    _projectName.value = project.name
                }
            }
        }
        viewModelScope.launch {
            configRepository?.initializeDefaultConfigsIfNeeded()
            configRepository?.getActiveConfig()?.collect { active ->
                if (active != null) {
                    val pName = when (active.providerType.uppercase()) {
                        "OPENAI" -> "OpenAI"
                        "ANTHROPIC" -> "Anthropic"
                        else -> "Gemini"
                    }
                    _selectedProvider.value = pName
                    _availableModels.value = com.example.ai.AIModelRegistry.getAvailableModels(pName)
                    val resolved = com.example.ai.AIModelRegistry.resolveModel(pName, active.selectedModel)
                    _selectedModel.value = resolved
                    if (resolved != active.selectedModel) {
                        viewModelScope.launch {
                            configRepository.updateModelForProvider(pName, resolved)
                        }
                    }
                }
            }
        }
    }

    fun selectFile(file: ProjectFileEntity) {
        _selectedFile.value = file
        _editorContent.value = file.content
    }

    fun closeFile() {
        _selectedFile.value = null
        _editorContent.value = ""
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    fun saveCurrentFile() {
        val currentFile = _selectedFile.value ?: return
        viewModelScope.launch {
            fileRepository.updateFileContent(currentFile.id, _editorContent.value)
            _selectedFile.value = fileRepository.getFile(currentFile.id)
        }
    }

    fun createFile(path: String) {
        viewModelScope.launch {
            val newFile = fileRepository.createFile(projectId, path)
            if (newFile != null) {
                selectFile(newFile)
            }
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            if (_selectedFile.value?.id == fileId) {
                _selectedFile.value = null
                _editorContent.value = ""
            }
            fileRepository.deleteFile(fileId)
        }
    }

    fun updateProjectName(name: String) {
        _projectName.value = name
        viewModelScope.launch {
            projectRepository.renameProject(projectId, name)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (_isAutonomousMode.value) {
            startAutonomousRun(text)
            return
        }
        viewModelScope.launch {
            messageRepository.insert(MessageEntity(projectId = projectId, text = text, isUser = true))
            _isBuilding.value = true
            try {
                val aiProvider = aiFactory.getProvider(_projectName.value, _selectedProvider.value, _selectedModel.value)
                val projectContext = ProjectContext(
                    projectName = _projectName.value,
                    files = files.value,
                    currentOpenFile = _selectedFile.value
                )
                val aiResponse = aiProvider.generateResponse(text, messages.value, projectContext)
                val usage = aiProvider.getLatestUsage()
                _latestUsage.value = usage
                usageRepository?.recordUsage(
                    provider = _selectedProvider.value,
                    model = _selectedModel.value,
                    tokens = usage,
                    status = "SUCCESS",
                    projectId = projectId
                )
                messageRepository.insert(MessageEntity(
                    projectId = projectId,
                    text = aiResponse,
                    isUser = false
                ))
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                val is404ModelError = errorMsg.contains("404") || errorMsg.contains("not found", ignoreCase = true) || errorMsg.contains("unsupported", ignoreCase = true)
                if (is404ModelError) {
                    val cached = aiFactory.modelSelectionRepository.getCachedModels(_selectedProvider.value)
                    val available = if (cached.isEmpty()) {
                        val key = apiKeyManager.getApiKey(_selectedProvider.value).orEmpty()
                        if (key.isNotBlank()) {
                            aiFactory.modelSelectionRepository.refreshModels(_selectedProvider.value, key).getOrNull().orEmpty()
                        } else emptyList()
                    } else cached
                    val fallback = aiFactory.modelSelectionRepository.getCompatibleFallback(_selectedProvider.value, _selectedModel.value, available)
                    if (fallback != null && fallback.id != _selectedModel.value) {
                        val oldModel = _selectedModel.value
                        _selectedModel.value = fallback.id
                        configRepository?.updateModelForProvider(_selectedProvider.value, fallback.id)
                        messageRepository.insert(MessageEntity(
                            projectId = projectId,
                            text = "Model Fallback: Selected model '$oldModel' was unavailable on the API (HTTP 404). Automatically switched to compatible live model '${fallback.id}'.",
                            isUser = false
                        ))
                    } else if (fallback == null) {
                        messageRepository.insert(MessageEntity(
                            projectId = projectId,
                            text = "Model Unavailable: Selected model '${_selectedModel.value}' was rejected by the API (HTTP 404), and no compatible alternative is currently discovered. Please refresh models in Settings.",
                            isUser = false
                        ))
                    }
                }

                usageRepository?.recordUsage(
                    provider = _selectedProvider.value,
                    model = _selectedModel.value,
                    tokens = null,
                    status = "ERROR",
                    error = e.message,
                    projectId = projectId
                )
                messageRepository.insert(MessageEntity(
                    projectId = projectId,
                    text = "Error: ${e.message}",
                    isUser = false
                ))
            } finally {
                _isBuilding.value = false
            }
        }
    }

    fun proposeChange(request: String) {
        if (request.isBlank()) return
        viewModelScope.launch {
            messageRepository.insert(MessageEntity(projectId = projectId, text = request, isUser = true))
            _proposalState.value = ProposalState.GENERATING
            _currentProposal.value = null
            _proposalError.value = null
            try {
                val aiProvider = aiFactory.getProvider(_projectName.value, _selectedProvider.value, _selectedModel.value)
                val projectContext = ProjectContext(
                    projectName = _projectName.value,
                    files = files.value,
                    currentOpenFile = _selectedFile.value
                )
                val proposal = aiProvider.proposeCodeChanges(request, projectContext)
                _currentProposal.value = proposal
                val usage = proposal.tokenUsage ?: aiProvider.getLatestUsage()
                _latestUsage.value = usage
                usageRepository?.recordUsage(
                    provider = _selectedProvider.value,
                    model = _selectedModel.value,
                    tokens = usage,
                    status = "SUCCESS",
                    projectId = projectId
                )
                
                messageRepository.insert(MessageEntity(
                    projectId = projectId,
                    text = "AI proposed changes\n\nSummary: ${proposal.summary}",
                    isUser = false
                ))
                
                _proposalState.value = ProposalState.READY_FOR_REVIEW
            } catch (e: Exception) {
                val errorMsg = e.message ?: "An unknown error occurred"
                val is404ModelError = errorMsg.contains("404") || errorMsg.contains("not found", ignoreCase = true) || errorMsg.contains("unsupported", ignoreCase = true)
                if (is404ModelError) {
                    val cached = aiFactory.modelSelectionRepository.getCachedModels(_selectedProvider.value)
                    val available = if (cached.isEmpty()) {
                        val key = apiKeyManager.getApiKey(_selectedProvider.value).orEmpty()
                        if (key.isNotBlank()) {
                            aiFactory.modelSelectionRepository.refreshModels(_selectedProvider.value, key).getOrNull().orEmpty()
                        } else emptyList()
                    } else cached
                    val fallback = aiFactory.modelSelectionRepository.getCompatibleFallback(_selectedProvider.value, _selectedModel.value, available)
                    if (fallback != null && fallback.id != _selectedModel.value) {
                        val oldModel = _selectedModel.value
                        _selectedModel.value = fallback.id
                        configRepository?.updateModelForProvider(_selectedProvider.value, fallback.id)
                        messageRepository.insert(MessageEntity(
                            projectId = projectId,
                            text = "Model Fallback: Selected model '$oldModel' was unavailable on the API (HTTP 404). Automatically switched to compatible live model '${fallback.id}'.",
                            isUser = false
                        ))
                    } else if (fallback == null) {
                        messageRepository.insert(MessageEntity(
                            projectId = projectId,
                            text = "Model Unavailable: Selected model '${_selectedModel.value}' was rejected by the API (HTTP 404), and no compatible alternative is currently discovered. Please refresh models in Settings.",
                            isUser = false
                        ))
                    }
                }
                _proposalError.value = errorMsg
                _proposalState.value = ProposalState.FAILED
                usageRepository?.recordUsage(
                    provider = _selectedProvider.value,
                    model = _selectedModel.value,
                    tokens = null,
                    status = "ERROR",
                    error = e.message,
                    projectId = projectId
                )
            }
        }
    }

    fun reviewProposal() {
        if (_proposalState.value == ProposalState.READY_FOR_REVIEW) {
            _proposalState.value = ProposalState.REVIEWING
        }
    }

    fun rejectProposal() {
        _proposalState.value = ProposalState.REJECTED
    }
    
    fun dismissProposal() {
        _proposalState.value = ProposalState.IDLE
        _currentProposal.value = null
        _proposalError.value = null
        _applyResult.value = null
    }

    fun approveAndApplyProposal() {
        val proposal = _currentProposal.value ?: return
        if (_proposalState.value == ProposalState.APPLYING) return
        
        viewModelScope.launch {
            _proposalState.value = ProposalState.APPROVED
            _proposalState.value = ProposalState.APPLYING
            _applyResult.value = null
            
            val result = codeChangeApplier.applyProposal(projectId, proposal)
            _applyResult.value = result
            
            if (result is com.example.ai.ApplyResult.Success) {
                _proposalState.value = ProposalState.APPLIED
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
            } else {
                _proposalState.value = ProposalState.FAILED
            }
        }
    }
    
    fun rollbackProposal() {
        val result = _applyResult.value as? com.example.ai.ApplyResult.Success ?: return
        viewModelScope.launch {
            try {
                codeChangeApplier.rollback(projectId, result.createdFileIds, result.snapshot)
                _proposalState.value = ProposalState.IDLE
                _currentProposal.value = null
                _applyResult.value = null
                
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
            } catch (e: Exception) {
                _proposalError.value = "Rollback failed: ${e.message}"
                _proposalState.value = ProposalState.FAILED
            }
        }
    }
}

class WorkspaceViewModelFactory(
    val projectId: String,
    private val messageRepository: MessageRepository,
    private val projectRepository: ProjectRepository,
    val fileRepository: ProjectFileRepository,
    private val aiFactory: com.example.ai.AIFactory,
    private val apiKeyManager: com.example.ai.APIKeyManager,
    private val configRepository: com.example.data.AIProviderConfigRepository? = null,
    private val usageRepository: com.example.data.UsageRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(
                projectId,
                messageRepository,
                projectRepository,
                fileRepository,
                aiFactory,
                apiKeyManager,
                configRepository,
                usageRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
