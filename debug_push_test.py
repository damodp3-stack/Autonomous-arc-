import re

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

bad = """        val state1 = viewModel.discoveryState.value as RepositoryDiscoveryState.RepositoriesLoaded"""
good = """        val state1 = viewModel.discoveryState.value as? RepositoryDiscoveryState.RepositoriesLoaded
        if (state1 == null) {
            println("DISCOVERY STATE WAS " + viewModel.discoveryState.value)
            throw Exception("Expected RepositoriesLoaded but was " + viewModel.discoveryState.value)
        }"""

content = content.replace(bad, good)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)

