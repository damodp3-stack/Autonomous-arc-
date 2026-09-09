with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(
    r'while \(viewModel\.projectState\.value !is GitHubProjectState\.Connected\) \{ kotlinx\.coroutines\.delay\(10\) \}',
    'for (i in 0..10) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }',
    content
)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)
