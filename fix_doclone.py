import re

with open('app/src/test/java/com/example/github/GitHubCloneTest.kt', 'r') as f:
    content = f.read()

bad = """        viewModel.connectRepository(force = true) // force to bypass conflict state for empty project
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(100)
    }"""

good = """        viewModel.connectRepository(force = true) // force to bypass conflict state for empty project
        for (i in 0..500) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            val state = viewModel.discoveryState.value
            if (state is com.example.ui.RepositoryDiscoveryState.Idle || state is com.example.ui.RepositoryDiscoveryState.Error) break
            Thread.sleep(10)
        }
    }"""

content = content.replace(bad, good)

with open('app/src/test/java/com/example/github/GitHubCloneTest.kt', 'w') as f:
    f.write(content)

