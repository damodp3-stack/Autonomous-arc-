import re
with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

setup_method = """    private suspend fun setupClonedProject(viewModel: GitHubViewModel, repoName: String) {
        viewModel.fetchRepositories()
        ShadowLooper.idleMainLooper()
        
        val state1 = viewModel.discoveryState.value as RepositoryDiscoveryState.RepositoriesLoaded
        val repo = state1.repositories.find { it.name == repoName }!!
        
        viewModel.selectRepository(repo)
        ShadowLooper.idleMainLooper()
        
        viewModel.connectRepository(force = true)
        
        for (i in 0..500) {
            ShadowLooper.idleMainLooper()
            if (viewModel.projectState.value is GitHubProjectState.Connected) break
            Thread.sleep(10)
        }
    }"""

content = re.sub(r'    private suspend fun setupClonedProject.*?    \}', setup_method, content, flags=re.DOTALL)
with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)
