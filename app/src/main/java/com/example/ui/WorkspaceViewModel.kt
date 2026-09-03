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

class WorkspaceViewModel(
    private val projectId: String,
    private val messageRepository: MessageRepository,
    private val projectRepository: ProjectRepository,
    private val aiProvider: AIProvider
) : ViewModel() {

    val messages: StateFlow<List<MessageEntity>> = messageRepository.getMessagesForProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
            
            val aiResponse = aiProvider.generateResponse(text, messages.value)
            
            // 3. Save AI response
            messageRepository.insert(MessageEntity(
                projectId = projectId,
                text = aiResponse,
                isUser = false
            ))
            _isBuilding.value = false
        }
    }
}

class WorkspaceViewModelFactory(
    private val projectId: String,
    private val messageRepository: MessageRepository,
    private val projectRepository: ProjectRepository,
    private val aiProvider: AIProvider
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(projectId, messageRepository, projectRepository, aiProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
