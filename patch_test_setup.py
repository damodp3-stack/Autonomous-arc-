import re

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

content = re.sub(
    r'ShadowLooper\.idleMainLooper\(\)\n        for \(i in 0\.\.10\).*?\}',
    'for (i in 0..500) { ShadowLooper.idleMainLooper(); if (viewModel.projectState.value is GitHubProjectState.Connected) break; Thread.sleep(10) }',
    content,
    flags=re.DOTALL
)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)
