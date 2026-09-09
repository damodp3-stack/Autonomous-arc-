with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(r'\n\s*delay\(\d+\)', '', content)
content = re.sub(r'\n\s*kotlinx\.coroutines\.delay\(\d+\)', '', content)

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
