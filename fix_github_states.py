with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

import re

new_discovery_states = """
import com.example.github.GitHubBranch

sealed class RepositoryDiscoveryState {
    object Idle : RepositoryDiscoveryState()
    object LoadingRepositories : RepositoryDiscoveryState()
    data class RepositoriesLoaded(val repositories: List<GitHubRepository>) : RepositoryDiscoveryState()
    data class LoadingBranches(val repository: GitHubRepository) : RepositoryDiscoveryState()
    data class BranchesLoaded(val repository: GitHubRepository, val branches: List<GitHubBranch>, val selectedBranch: GitHubBranch?) : RepositoryDiscoveryState()
    object Connecting : RepositoryDiscoveryState()
    data class Error(val message: String) : RepositoryDiscoveryState()
}
"""

content = re.sub(r'sealed class RepositoryDiscoveryState \{.*?\}', new_discovery_states.strip(), content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)

