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
    IDLE, GENERATING, READY, ERROR
}

class WorkspaceViewModel(
    private val projectId: String,
    private val messageRepository: MessageRepository,
    private val projectRepository: ProjectRepository,
    private val fileRepository: ProjectFileRepository,
    private val providers: Map<String, AIProvider>
) : ViewModel() {

    val availableProviders = providers.keys.toList()

    private val _selectedProvider = MutableStateFlow(availableProviders.firstOrNull() ?: "Mock")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _proposalState = MutableStateFlow(ProposalState.IDLE)
    val proposalState: StateFlow<ProposalState> = _proposalState.asStateFlow()

    private val _currentProposal = MutableStateFlow<com.example.ai.CodeChangeProposal?>(null)
    val currentProposal: StateFlow<com.example.ai.CodeChangeProposal?> = _currentProposal.asStateFlow()

    private val _proposalError = MutableStateFlow<String?>(null)
    val proposalError: StateFlow<String?> = _proposalError.asStateFlow()

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

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    fun saveCurrentFile() {
        val currentFile = _selectedFile.value ?: return
        viewModelScope.launch {
            fileRepository.updateFileContent(currentFile.id, _editorContent.value)
            // Reload the file to get the updated entity
            _selectedFile.value = fileRepository.getFile(currentFile.id)
        }
    }

    fun createFile(path: String) {
        viewModelScope.launch {
            val newFile = fileRepository.createFile(projectId, path)
            selectFile(newFile)
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
            // 1. Save user message
            messageRepository.insert(MessageEntity(projectId = projectId, text = text, isUser = true))
            
            // 2. Simulate AI processing
            _isBuilding.value = true
            
            val aiProvider = providers[_selectedProvider.value] ?: providers.values.first()
            
            val projectContext = ProjectContext(
                projectName = _projectName.value,
                files = files.value,
                currentOpenFile = _selectedFile.value
            )
            
            val aiResponse = aiProvider.generateResponse(text, messages.value, projectContext)
            
            // 3. Save AI response
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
                _proposalState.value = ProposalState.READY
            } catch (e: Exception) {
                _proposalError.value = e.message ?: "An unknown error occurred"
                _proposalState.value = ProposalState.ERROR
            }
        }
    }

    fun clearProposal() {
        _proposalState.value = ProposalState.IDLE
        _currentProposal.value = null
        _proposalError.value = null
    }
}

class WorkspaceViewModelFactory(
    private val projectId: String,
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
