with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(
    r'kotlinx\.coroutines\.delay\(100\)\n    \}',
    'kotlinx.coroutines.delay(100)\n        while (viewModel.projectState.value !is GitHubProjectState.Connected) { kotlinx.coroutines.delay(10) }\n    }',
    content
)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)
