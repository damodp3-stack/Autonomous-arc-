package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubConfigEntity
import com.example.data.GitHubConfigRepository
import com.example.github.GitHubAuthService
import com.example.github.GitHubRepository
import com.example.github.GitHubService
import com.example.github.GitHubUser
import com.example.github.GitHubBranch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch


import com.example.github.*


sealed class GitHubPushState {
    object Idle : GitHubPushState()
    object DetectingChanges : GitHubPushState()
    data class ChangesReady(val summary: CommitSummary) : GitHubPushState()
    object NoChanges : GitHubPushState()
    data class Committing(val progress: String) : GitHubPushState()
    object Pushing : GitHubPushState()
    object Success : GitHubPushState()
    data class Conflict(val message: String) : GitHubPushState()
    data class Error(val message: String) : GitHubPushState()
}

sealed class GitHubAuthState {
    object Checking : GitHubAuthState()
    object Unauthenticated : GitHubAuthState()
    data class Authenticated(val user: GitHubUser) : GitHubAuthState()
    data class Error(val message: String) : GitHubAuthState()
}

sealed class GitHubProjectState {
    object Loading : GitHubProjectState()
    object Disconnected : GitHubProjectState()
    data class Connected(val config: GitHubConfigEntity) : GitHubProjectState()
    data class Error(val message: String) : GitHubProjectState()
}

sealed class RepositoryDiscoveryState {
    object Idle : RepositoryDiscoveryState()
    object LoadingRepositories : RepositoryDiscoveryState()
    data class RepositoriesLoaded(val repositories: List<GitHubRepository>) : RepositoryDiscoveryState()
    data class LoadingBranches(val repository: GitHubRepository) : RepositoryDiscoveryState()
    data class BranchesLoaded(val repository: GitHubRepository, val branches: List<GitHubBranch>, val selectedBranch: GitHubBranch?) : RepositoryDiscoveryState()
    data class Conflict(val repository: GitHubRepository, val selectedBranch: GitHubBranch) : RepositoryDiscoveryState()
    data class Cloning(val progress: String) : RepositoryDiscoveryState()
    object Connecting : RepositoryDiscoveryState()
    data class Error(val message: String) : RepositoryDiscoveryState()
}

class GitHubViewModel(
    private val projectId: String,
    private val authService: GitHubAuthService,
    private val githubService: GitHubService,
    private val configRepository: GitHubConfigRepository,
    private val syncService: GitHubSyncService
) : ViewModel() {

    private val _authState = MutableStateFlow<GitHubAuthState>(GitHubAuthState.Checking)
    val authState: StateFlow<GitHubAuthState> = _authState

    private val _projectState = MutableStateFlow<GitHubProjectState>(GitHubProjectState.Loading)
    val projectState: StateFlow<GitHubProjectState> = _projectState

    private val _discoveryState = MutableStateFlow<RepositoryDiscoveryState>(RepositoryDiscoveryState.Idle)
    val discoveryState: StateFlow<RepositoryDiscoveryState> = _discoveryState

    private val _pushState = MutableStateFlow<GitHubPushState>(GitHubPushState.Idle)
    val pushState: StateFlow<GitHubPushState> = _pushState

    init {
        checkAuthStatus()
        observeProjectConfig()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            try {
                if (authService.isAuthenticated()) {
                    val user = authService.getAuthenticatedUser()
                    if (user != null) {
                        _authState.value = GitHubAuthState.Authenticated(user)
                    } else {
                        _authState.value = GitHubAuthState.Unauthenticated
                    }
                } else {
                    _authState.value = GitHubAuthState.Unauthenticated
                }
            } catch (e: Exception) {
                _authState.value = GitHubAuthState.Error(e.message ?: "Auth error")
            }
        }
    }

    fun authenticate(token: String) {
        viewModelScope.launch {
            _authState.value = GitHubAuthState.Checking
            try {
                val success = authService.authenticate(token)
                if (success) {
                    val user = authService.getAuthenticatedUser()
                    if (user != null) {
                        _authState.value = GitHubAuthState.Authenticated(user)
                    } else {
                        _authState.value = GitHubAuthState.Error("Failed to fetch user")
                    }
                } else {
                    _authState.value = GitHubAuthState.Error("Invalid token")
                }
            } catch (e: Exception) {
                _authState.value = GitHubAuthState.Error(e.message ?: "Auth exception")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authService.logout()
            _authState.value = GitHubAuthState.Unauthenticated
            _discoveryState.value = RepositoryDiscoveryState.Idle
        }
    }

    private fun observeProjectConfig() {
        viewModelScope.launch {
            configRepository.getConfigForProject(projectId).collect { config ->
                if (config != null) {
                    _projectState.value = GitHubProjectState.Connected(config)
                } else {
                    _projectState.value = GitHubProjectState.Disconnected
                }
            }
        }
    }

fun fetchRepositories() {
        viewModelScope.launch {
            _discoveryState.value = RepositoryDiscoveryState.LoadingRepositories
            try {
                val repos = githubService.getRepositories()
                _discoveryState.value = RepositoryDiscoveryState.RepositoriesLoaded(repos)
            } catch (e: Exception) {
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to fetch repositories")
            }
        }
    }

    fun selectRepository(repository: GitHubRepository) {
        viewModelScope.launch {
            _discoveryState.value = RepositoryDiscoveryState.LoadingBranches(repository)
            try {
                val owner = repository.fullName.substringBefore("/")
                val branches = githubService.getBranches(owner, repository.name)
                val defaultBranch = branches.find { it.name == repository.defaultBranch } ?: branches.firstOrNull()
                _discoveryState.value = RepositoryDiscoveryState.BranchesLoaded(repository, branches, defaultBranch)
            } catch (e: Exception) {
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to load branches")
            }
        }
    }

    fun selectBranch(branch: GitHubBranch) {
        val currentState = _discoveryState.value
        if (currentState is RepositoryDiscoveryState.BranchesLoaded) {
            _discoveryState.value = currentState.copy(selectedBranch = branch)
        }
    }



    private suspend fun fetchTreeRecursive(owner: String, repo: String, sha: String, currentPath: String = "", depth: Int = 0): List<com.example.github.GitHubTreeItem> {
        if (depth > 20) throw Exception("Repository directory depth too large")
        val tree = githubService.getTree(owner, repo, sha)
        val result = mutableListOf<com.example.github.GitHubTreeItem>()
        for (item in tree.tree) {
            val fullPath = if (currentPath.isEmpty()) item.path else "$currentPath/${item.path}"
            val updatedItem = item.copy(path = fullPath)
            result.add(updatedItem)
            
            if (item.type == "tree" && tree.truncated) {
                // If the root was truncated, we have to fetch sub-trees recursively to ensure completeness
                // In a robust app, we'd paginate or queue this, but for now we do simple DFS
                val subTreeItems = fetchTreeRecursive(owner, repo, item.sha, fullPath, depth + 1)
                result.addAll(subTreeItems)
            }
        }
        return result
    }

    fun connectRepository(force: Boolean = false) {
        val currentState = _discoveryState.value
        val (repository, selectedBranch) = when (currentState) {
            is RepositoryDiscoveryState.BranchesLoaded -> currentState.repository to currentState.selectedBranch
            is RepositoryDiscoveryState.Conflict -> currentState.repository to currentState.selectedBranch
            else -> return
        }
        if (selectedBranch == null) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _discoveryState.value = RepositoryDiscoveryState.Cloning("Preparing clone...")
            try {
                val owner = repository.fullName.substringBefore("/")
                val repo = repository.name
                
                val config = com.example.data.GitHubConfigEntity(
                    projectId = projectId,
                    owner = owner,
                    repository = repo,
                    branch = selectedBranch.name,
                    isConnected = true,
                    lastRemoteSha = selectedBranch.commit.sha
                )
                configRepository.saveConfig(config)
                
                val result = syncService.pull(projectId) { progress ->
                    _discoveryState.value = RepositoryDiscoveryState.Cloning(progress)
                }
                
                if (result is SyncResult.Success) {
                    _discoveryState.value = RepositoryDiscoveryState.Idle
                } else {
                    configRepository.clearConfig(projectId)
                    _discoveryState.value = RepositoryDiscoveryState.Error((result as? SyncResult.Error)?.message ?: "Failed to clone")
                }
            } catch (e: Exception) {
                configRepository.clearConfig(projectId)
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to connect and clone")
            }
        }
    }


    fun detectChanges() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _pushState.value = GitHubPushState.DetectingChanges
            try {
                val summary = syncService.detectChanges(projectId)
                if (summary == null) {
                    _pushState.value = GitHubPushState.NoChanges
                } else {
                    _pushState.value = GitHubPushState.ChangesReady(summary)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (e.message?.contains("Remote branch changed") == true) {
                    _pushState.value = GitHubPushState.Conflict(e.message!!)
                } else {
                    _pushState.value = GitHubPushState.Error(e.message ?: "Failed to detect changes")
                }
            }
        }
    }

    fun commitAndPush(message: String) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) {
            _pushState.value = GitHubPushState.Error("Commit message cannot be empty")
            return
        }

        val currentState = _pushState.value
        val summary = (currentState as? GitHubPushState.ChangesReady)?.summary
        if (summary == null || summary.changes.isEmpty()) {
            _pushState.value = GitHubPushState.Error("No changes to commit")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _pushState.value = GitHubPushState.Committing("Starting commit process...")
            val result = syncService.push(projectId, trimmedMessage, summary) { progress ->
                _pushState.value = GitHubPushState.Committing(progress)
            }
            
            when (result) {
                is SyncResult.Success -> {
                    _pushState.value = GitHubPushState.Success
                }
                is SyncResult.Conflict -> {
                    _pushState.value = GitHubPushState.Conflict(result.message)
                }
                is SyncResult.Error -> {
                    _pushState.value = GitHubPushState.Error(result.message)
                }
            }
        }
    }

    fun clearPushState() {
        _pushState.value = GitHubPushState.Idle
    }

    fun disconnectRepository() {
        viewModelScope.launch {
            try {
                configRepository.clearConfig(projectId)
            } catch (e: Exception) {
                _projectState.value = GitHubProjectState.Error(e.message ?: "Failed to disconnect")
            }
        }
    }
}
