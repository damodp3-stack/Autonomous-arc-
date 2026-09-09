with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

content = content.replace('"   \\\\n  "', '"   \\n  "')

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)
