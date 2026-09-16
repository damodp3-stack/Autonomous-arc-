import re
with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

# Replace constructor params
content = re.sub(
    r'private val providers: Map<String, AIProvider>',
    r'private val aiFactory: com.example.ai.AIFactory',
    content
)

content = content.replace(
    'val aiProvider = providers[_selectedProvider.value] ?: providers.values.first()',
    'val aiProvider = aiFactory.getProvider(projectId)'
)

# And factory params
content = re.sub(
    r'private val providers: Map<String, AIProvider>',
    r'private val aiFactory: com.example.ai.AIFactory',
    content
)

content = content.replace(
    'providers = providers',
    'aiFactory = aiFactory'
)

content = content.replace(
    'providers)',
    'aiFactory)'
)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)
