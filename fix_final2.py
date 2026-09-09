with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    lines = f.readlines()

lines.insert(925, "}\n")

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.writelines(lines)
