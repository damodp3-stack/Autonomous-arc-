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
    private val fileRepository: ProjectFileRepository,
    private val providers: Map<String, AIProvider>
) : ViewModel() {

    private val codeChangeApplier = com.example.ai.CodeChangeApplier(fileRepository)

    val availableProviders = providers.keys.toList()
    private val _selectedProvider = MutableStateFlow(availableProviders.firstOrNull() ?: "Mock")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _proposalState = MutableStateFlow(ProposalState.IDLE)
    val proposalState: StateFlow<ProposalState> = _proposalState.asStateFlow()

    private val _currentProposal = MutableStateFlow<com.example.ai.CodeChangeProposal?>(null)
    val currentProposal: StateFlow<com.example.ai.CodeChangeProposal?> = _currentProposal.asStateFlow()

    private val _proposalError = MutableStateFlow<String?>(null)
    val proposalError: StateFlow<String?> = _proposalError.asStateFlow()

    private val _applyResult = MutableStateFlow<com.example.ai.ApplyResult?>(null)
    val applyResult: StateFlow<com.example.ai.ApplyResult?> = _applyResult.asStateFlow()

    fun setProvider(providerName: String) {
        if (providers.containsKey(providerName)) {
            _selectedProvider.value = providerName
        }
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

    init {
        viewModelScope.launch {
            fileRepository.syncProjectFilesToSystem(projectId)
            projectRepository.getProject(projectId).collect { project ->
                if (project != null) {
                    _projectName.value = project.name
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
        viewModelScope.launch {
            messageRepository.insert(MessageEntity(projectId = projectId, text = text, isUser = true))
            _isBuilding.value = true
            val aiProvider = providers[_selectedProvider.value] ?: providers.values.first()
            val projectContext = ProjectContext(
                projectName = _projectName.value,
                files = files.value,
                currentOpenFile = _selectedFile.value
            )
            val aiResponse = aiProvider.generateResponse(text, messages.value, projectContext)
            messageRepository.insert(MessageEntity(
                projectId = projectId,
                text = aiResponse,
                isUser = false
            ))
            _isBuilding.value = false
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
                val aiProvider = providers[_selectedProvider.value] ?: providers.values.first()
                val projectContext = ProjectContext(
                    projectName = _projectName.value,
                    files = files.value,
                    currentOpenFile = _selectedFile.value
                )
                val proposal = aiProvider.proposeCodeChanges(request, projectContext)
                _currentProposal.value = proposal
                
                messageRepository.insert(MessageEntity(
                    projectId = projectId,
                    text = "AI proposed changes\n\nSummary: ${proposal.summary}",
                    isUser = false
                ))
                
                _proposalState.value = ProposalState.READY_FOR_REVIEW
            } catch (e: Exception) {
                _proposalError.value = e.message ?: "An unknown error occurred"
                _proposalState.value = ProposalState.FAILED
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
                codeChangeApplier.rollback(result.createdFileIds, result.snapshot)
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
    private val fileRepository: ProjectFileRepository,
    private val providers: Map<String, AIProvider>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(projectId, messageRepository, projectRepository, fileRepository, providers) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
