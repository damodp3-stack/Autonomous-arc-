package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MessageEntity
import com.example.data.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainViewModel(private val repository: MessageRepository) : ViewModel() {

    val messages: StateFlow<List<MessageEntity>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _projectName = MutableStateFlow("Untitled Project")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    fun updateProjectName(name: String) {
        _projectName.value = name
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // 1. Save user message
            repository.insert(MessageEntity(text = text, isUser = true))
            
            // 2. Simulate AI processing
            _isBuilding.value = true
            delay(1000)
            
            // 3. Save AI response
            repository.insert(MessageEntity(
                text = "Building modular architecture for '${_projectName.value}'.\nI've added the initial scaffolding for your request.\n\n```kotlin\n// TODO: Implement requested features\n```\n\nWhat's next?",
                isUser = false
            ))
            _isBuilding.value = false
        }
    }
}

class MainViewModelFactory(private val repository: MessageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
