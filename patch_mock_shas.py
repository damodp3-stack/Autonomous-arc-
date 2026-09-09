with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'r') as f:
    content = f.read()

content = content.replace('"sha1"', '"1eb1f61f661d2df336885bc0b21fc58f90483889"')
content = content.replace('"sha3"', '"f9af4e06c9ffb13264ce1ff181e6446bdebe1c5c"')
content = content.replace('"sha4"', '"0dd1608e45a9c4d35bfc1e6f266a796364aa8754"')

with open('app/src/main/java/com/example/github/MockGitHubService.kt', 'w') as f:
    f.write(content)
