with open('app/src/main/java/com/example/ai/AIFactory.kt', 'r') as f:
    content = f.read()

import re

old_factory = """        // Just return Gemini or Mock for now since we haven't written OpenAI yet, but we will support it.
        if (uiSelectedProvider == "Mock") {
            return MockAIProvider()
        }

        val config = configRepository.getConfig(projectName, uiSelectedProvider ?: "Gemini").firstOrNull()
        
        val key = keyManager.getApiKey(uiSelectedProvider ?: "Gemini")

        return when (uiSelectedProvider) {
            "Gemini" -> GeminiAIProvider(projectName = projectName, providedApiKey = key)
            "OpenAI" -> OpenAIProvider(projectName = projectName, providedApiKey = key) // Will be real later
            "Anthropic" -> AnthropicProvider(projectName = projectName, providedApiKey = key) // Will be real later
            else -> GeminiAIProvider(projectName = projectName, providedApiKey = key)
        }"""

new_factory = """        if (uiSelectedProvider == "Mock") {
            return MockAIProvider()
        }

        val config = configRepository.getConfig(projectName, uiSelectedProvider ?: "Gemini").firstOrNull()
        
        val key = keyManager.getApiKey(uiSelectedProvider ?: "Gemini")

        return when (uiSelectedProvider) {
            "Gemini" -> GeminiAIProvider(projectName = projectName, providedApiKey = key)
            "OpenAI" -> OpenAIProvider(projectName = projectName, providedApiKey = key)
            "Anthropic" -> AnthropicProvider(projectName = projectName, providedApiKey = key)
            else -> GeminiAIProvider(projectName = projectName, providedApiKey = key)
        }"""

content = content.replace(old_factory, new_factory)

with open('app/src/main/java/com/example/ai/AIFactory.kt', 'w') as f:
    f.write(content)
