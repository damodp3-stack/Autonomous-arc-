with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

content = content.replace('var currentRefSha = "initial-sha"', 'var currentRefSha = "abcdef123456"')

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
