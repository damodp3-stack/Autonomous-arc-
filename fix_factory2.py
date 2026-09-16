with open('app/src/main/java/com/example/ai/AIFactory.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '.kotlinx.coroutines.flow.firstOrNull()',
    '.firstOrNull()'
)

with open('app/src/main/java/com/example/ai/AIFactory.kt', 'w') as f:
    f.write(content)
