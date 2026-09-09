with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(
    r'import com\.example\.ui\.GitHubViewModel',
    'import com.example.ui.GitHubViewModel\nimport com.example.ui.GitHubProjectState',
    content
)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)
