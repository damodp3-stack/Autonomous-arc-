with open('app/src/main/java/com/example/ui/AppNavigation.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val messageRepository = MessageRepository(database.messageDao())',
    'val messageRepository = MessageRepository(database.messageDao())\n    val aiProviderConfigRepository = com.example.data.AIProviderConfigRepository(database.aiProviderConfigDao())\n    val apiKeyManager = com.example.ai.SecureAPIKeyManager(context)\n    val aiFactory = com.example.ai.AIFactory(aiProviderConfigRepository, apiKeyManager)'
)

content = content.replace(
    '''            // We retrieve the project name synchronously if possible, or just pass the ID to GeminiAIProvider. 
            // In a real app we'd pass the actual project context, but for now we'll pass projectId.
            val providers = mapOf(
                "Gemini" to GeminiAIProvider(projectName = projectId),
                "Mock" to MockAIProvider()
            )''',
    ''
)

content = content.replace(
    'providers = providers',
    'aiFactory = aiFactory'
)

with open('app/src/main/java/com/example/ui/AppNavigation.kt', 'w') as f:
    f.write(content)
