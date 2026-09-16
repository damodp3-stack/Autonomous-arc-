import re
with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val aiProvider = aiFactory.getProvider(projectId)',
    'val aiProvider = aiFactory.getProvider(_projectName.value)'
)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)
