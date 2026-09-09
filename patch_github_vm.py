import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

# Replace fetchRepositories and connectRepository
methods_replacement = """
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

    fun connectRepository() {
        val currentState = _discoveryState.value
        if (currentState is RepositoryDiscoveryState.BranchesLoaded) {
            val repository = currentState.repository
            val selectedBranch = currentState.selectedBranch ?: return

            viewModelScope.launch {
                _discoveryState.value = RepositoryDiscoveryState.Connecting
                try {
                    val config = GitHubConfigEntity(
                        projectId = projectId,
                        owner = repository.fullName.substringBefore("/"),
                        repository = repository.name,
                        branch = selectedBranch.name,
                        isConnected = true
                    )
                    configRepository.saveConfig(config)
                    _discoveryState.value = RepositoryDiscoveryState.Idle // close discovery
                } catch (e: Exception) {
                    _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to connect")
                }
            }
        }
    }
"""

content = re.sub(r'    fun fetchRepositories\(\) \{.*?(?=    fun disconnectRepository\(\) \{)', methods_replacement.strip() + "\n\n", content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)
