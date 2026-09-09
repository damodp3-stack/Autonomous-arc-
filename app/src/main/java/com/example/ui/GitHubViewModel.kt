package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubConfigEntity
import com.example.data.GitHubConfigRepository
import com.example.github.GitHubAuthService
import com.example.github.GitHubRepository
import com.example.github.GitHubService
import com.example.github.GitHubUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    object Loading : RepositoryDiscoveryState()
    data class Success(val repositories: List<GitHubRepository>) : RepositoryDiscoveryState()
    data class Error(val message: String) : RepositoryDiscoveryState()
}

class GitHubViewModel(
    private val projectId: String,
    private val authService: GitHubAuthService,
    private val githubService: GitHubService,
    private val configRepository: GitHubConfigRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<GitHubAuthState>(GitHubAuthState.Checking)
    val authState: StateFlow<GitHubAuthState> = _authState

    private val _projectState = MutableStateFlow<GitHubProjectState>(GitHubProjectState.Loading)
    val projectState: StateFlow<GitHubProjectState> = _projectState

    private val _discoveryState = MutableStateFlow<RepositoryDiscoveryState>(RepositoryDiscoveryState.Idle)
    val discoveryState: StateFlow<RepositoryDiscoveryState> = _discoveryState

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
            _discoveryState.value = RepositoryDiscoveryState.Loading
            try {
                val repos = githubService.getRepositories()
                _discoveryState.value = RepositoryDiscoveryState.Success(repos)
            } catch (e: Exception) {
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to fetch repositories")
            }
        }
    }

    fun connectRepository(repository: GitHubRepository) {
        viewModelScope.launch {
            try {
                val config = GitHubConfigEntity(
                    projectId = projectId,
                    owner = repository.fullName.substringBefore("/"),
                    repository = repository.name,
                    branch = repository.defaultBranch,
                    isConnected = true
                )
                configRepository.saveConfig(config)
                _discoveryState.value = RepositoryDiscoveryState.Idle // close discovery
            } catch (e: Exception) {
                _projectState.value = GitHubProjectState.Error(e.message ?: "Failed to connect")
            }
        }
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
