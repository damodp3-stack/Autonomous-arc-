with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'r') as f:
    content = f.read()

content = content.replace("fun getProjectRoot        if", "fun getProjectRoot(projectId: String): java.io.File {\n        if")

with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'w') as f:
    f.write(content)
