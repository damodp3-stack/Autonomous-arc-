import re
import os

for filename in ['app/src/test/java/com/example/github/GitHubCloneTest.kt', 'app/src/test/java/com/example/github/GitHubPushTest.kt']:
    with open(filename, 'r') as f:
        content = f.read()

    # Find all while/for loops waiting for projectState.value is Connected
    content = re.sub(
        r'for\s*\(i\s*in\s*0\.\.500\)\s*\{\s*ShadowLooper\.idleMainLooper\(\)\s*if\s*\(viewModel\.projectState\.value\s*is\s*GitHubProjectState\.Connected\)\s*break\s*Thread\.sleep\(10\)\s*\}',
        'for (i in 0..500) { ShadowLooper.idleMainLooper(); val state = viewModel.discoveryState.value; if (state is RepositoryDiscoveryState.Idle || state is RepositoryDiscoveryState.Error) break; Thread.sleep(10) }',
        content
    )
    
    with open(filename, 'w') as f:
        f.write(content)
