with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '''                                            val opColor = when(change.operation) {
                                                com.example.ai.FileOperation.CREATE -> Color(0xFF4CAF50)
                                                com.example.ai.FileOperation.MODIFY -> Color(0xFF2196F3)
                                                com.example.ai.FileOperation.DELETE -> Color(0xFFF44336)
                                            }''',
    '''                                            val opColor = when(change.operation) {
                                                com.example.ai.FileOperation.CREATE -> Color(0xFF4CAF50)
                                                com.example.ai.FileOperation.MODIFY -> Color(0xFF2196F3)
                                                com.example.ai.FileOperation.DELETE -> Color(0xFFF44336)
                                                com.example.ai.FileOperation.RENAME -> Color(0xFFFF9800)
                                            }'''
)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
