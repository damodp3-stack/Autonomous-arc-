with open('app/src/main/java/com/example/ai/CodeChangeProposal.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'enum class FileOperation {\n    CREATE, MODIFY, DELETE\n}',
    'enum class FileOperation {\n    CREATE, MODIFY, DELETE, RENAME\n}'
)

content = content.replace(
    'val operation: FileOperation,',
    'val operation: FileOperation,\n    val newFilePath: String? = null,'
)

with open('app/src/main/java/com/example/ai/CodeChangeProposal.kt', 'w') as f:
    f.write(content)
