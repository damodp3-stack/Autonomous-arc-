with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('replace("\\", "/")', 'replace("\\\\", "/")')
content = content.replace('replace("", "").replace("", "")', 'replace("\\n", "").replace("\\r", "")')

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)
