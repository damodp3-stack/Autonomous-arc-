import re

with open('app/src/main/java/com/example/github/GitHubApi.kt', 'r') as f:
    content = f.read()

content = content.replace('@GET("user/repos?sort=updated")', '@GET("user/repos?sort=updated&per_page=100")')
content = content.replace('@GET("repos/{owner}/{repo}/branches")', '@GET("repos/{owner}/{repo}/branches?per_page=100")')

with open('app/src/main/java/com/example/github/GitHubApi.kt', 'w') as f:
    f.write(content)
