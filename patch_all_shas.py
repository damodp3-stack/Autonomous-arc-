with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

content = content.replace('"readme-sha"', '"1eb1f61f661d2df336885bc0b21fc58f90483889"')
content = content.replace('"main-sha"', '"ae973bcd537de20e5993b49f7a406f38a8ad0c8e"')
content = content.replace('"icon-sha"', '"0dd1608e45a9c4d35bfc1e6f266a796364aa8754"')

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
