with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val availableProviders = providers.keys.toList()',
    'val availableProviders = listOf("Gemini", "OpenAI", "Anthropic", "Mock")'
)

content = content.replace(
    '''    fun setProvider(providerName: String) {
        if (providers.containsKey(providerName)) {
            _selectedProvider.value = providerName
        }
    }''',
    '''    fun setProvider(providerName: String) {
        _selectedProvider.value = providerName
    }'''
)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)
