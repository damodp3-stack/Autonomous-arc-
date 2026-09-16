import re

with open('app/src/test/java/com/example/github/GitHubCloneTest.kt', 'r') as f:
    content = f.read()

bad = """        for (i in 0..500) {
            ShadowLooper.idleMainLooper()
            if (viewModel.projectState.value is GitHubProjectState.Connected) break
            Thread.sleep(10)
        }"""
good = """        for (i in 0..500) {
            ShadowLooper.idleMainLooper()
            val state = viewModel.discoveryState.value
            if (state is RepositoryDiscoveryState.Idle || state is RepositoryDiscoveryState.Error) break
            Thread.sleep(10)
        }"""

content = content.replace(bad, good)

# Also fix the standalone wait loops in tests like `test duplicate normalized path`
bad2 = """        for (i in 0..50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (vm.projectState.value is com.example.ui.GitHubProjectState.Connected || vm.discoveryState.value is com.example.ui.RepositoryDiscoveryState.Error) break
            Thread.sleep(10)
        }"""
good2 = """        for (i in 0..500) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            val state = vm.discoveryState.value
            if (state is com.example.ui.RepositoryDiscoveryState.Idle || state is com.example.ui.RepositoryDiscoveryState.Error) break
            Thread.sleep(10)
        }"""
content = content.replace(bad2, good2)

with open('app/src/test/java/com/example/github/GitHubCloneTest.kt', 'w') as f:
    f.write(content)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content2 = f.read()

content2 = content2.replace(bad, good)
content2 = content2.replace(bad2, good2)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content2)

