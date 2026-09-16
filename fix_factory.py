with open('app/src/main/java/com/example/ai/AIFactory.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'import com.example.data.AIProviderConfigRepository',
    'import com.example.data.AIProviderConfigRepository\nimport kotlinx.coroutines.flow.firstOrNull\nimport com.example.data.AIProviderConfigEntity'
)

content = content.replace(
    'val apiKey = keyManager.getApiKey(activeConfig.id)',
    'val apiKey = keyManager.getApiKey(activeConfig.id) ?: ""'
)

with open('app/src/main/java/com/example/ai/AIFactory.kt', 'w') as f:
    f.write(content)
