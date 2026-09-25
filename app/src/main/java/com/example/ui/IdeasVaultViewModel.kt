package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.IdeaEntity
import com.example.data.IdeaRepository
import com.example.data.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IdeasVaultViewModel(
    private val ideaRepository: IdeaRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _statusFilter = MutableStateFlow("ALL")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val allIdeas = ideaRepository.getAllIdeas()

    val filteredIdeas: StateFlow<List<IdeaEntity>> = combine(
        allIdeas,
        _statusFilter,
        _searchQuery
    ) { ideas, status, query ->
        ideas.filter { idea ->
            val matchesStatus = status == "ALL" || idea.status.equals(status, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    idea.title.contains(query, ignoreCase = true) ||
                    idea.description.contains(query, ignoreCase = true) ||
                    idea.tags.contains(query, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setStatusFilter(status: String) {
        _statusFilter.value = status
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createIdea(
        title: String,
        description: String,
        tags: List<String> = emptyList(),
        status: String = "DRAFT"
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            ideaRepository.createIdea(
                title = title,
                description = description,
                tags = tags,
                status = status
            )
        }
    }

    fun updateIdea(idea: IdeaEntity) {
        viewModelScope.launch {
            ideaRepository.updateIdea(idea)
        }
    }

    fun deleteIdea(id: String) {
        viewModelScope.launch {
            ideaRepository.deleteIdea(id)
        }
    }

    fun convertToProject(ideaId: String, onProjectCreated: (String) -> Unit) {
        viewModelScope.launch {
            val newId = ideaRepository.convertToProject(ideaId, projectRepository)
            if (newId != null) {
                onProjectCreated(newId)
            }
        }
    }
}

class IdeasVaultViewModelFactory(
    private val ideaRepository: IdeaRepository,
    private val projectRepository: ProjectRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IdeasVaultViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return IdeasVaultViewModel(ideaRepository, projectRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
