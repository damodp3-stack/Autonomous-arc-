with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('        }\n    }\n        \n@Composable\nfun MessageBubble(', '        }\n    }\n}\n\n@Composable\nfun MessageBubble(')

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
