import re

with open('app/src/main/java/com/example/github/RealGitHubSyncService.kt', 'r') as f:
    content = f.read()

content = content.replace('"blob ${content.size}\\0"', '"blob ${content.size}\\u0000"')
content = content.replace('replace("", "").replace("", "")', 'replace("\\n", "").replace("\\r", "")')
content = content.replace('replace("\\\\\\\\", "/")', 'replace("\\\\\\\\", "/")')

with open('app/src/main/java/com/example/github/RealGitHubSyncService.kt', 'w') as f:
    f.write(content)

