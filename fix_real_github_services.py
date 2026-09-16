import re

with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'r') as f:
    content = f.read()

bad = r'    override suspend fun sync\(projectId: String\): SyncResult \{[\s\S]*?override suspend fun push\(projectId: String, commitMessage: String\): SyncResult \{\s*return SyncResult.Success\s*\}'

content = re.sub(bad, '', content)

with open('app/src/main/java/com/example/github/RealGitHubServices.kt', 'w') as f:
    f.write(content)

