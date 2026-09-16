with open('app/src/main/java/com/example/ui/AppNavigation.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '                    aiFactory = aiFactory\n                )\n            )',
    '                    aiFactory = aiFactory,\n                    apiKeyManager = apiKeyManager\n                )\n            )'
)

with open('app/src/main/java/com/example/ui/AppNavigation.kt', 'w') as f:
    f.write(content)
