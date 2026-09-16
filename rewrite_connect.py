import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

bad = r'fun connectRepository\(force: Boolean = false\) \{[\s\S]*?_discoveryState\.value = RepositoryDiscoveryState\.Error\(e\.message \?: "Failed to connect and clone"\)\s*\}\s*\}\s*\}'

good = """fun connectRepository(force: Boolean = false) {
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
    }"""

content = re.sub(bad, good, content)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)

