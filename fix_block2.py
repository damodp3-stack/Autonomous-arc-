import re

with open('app/src/main/java/com/example/github/RealGitHubSyncService.kt', 'r') as f:
    content = f.read()

bad = r'val cleanBase64 = blob\.content.*?replace.*'
good = 'val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")'
content = re.sub(bad, good, content)

with open('app/src/main/java/com/example/github/RealGitHubSyncService.kt', 'w') as f:
    f.write(content)
