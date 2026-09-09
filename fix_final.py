with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    lines = f.readlines()

open_braces = 0
for i, line in enumerate(lines):
    open_braces += line.count('{') - line.count('}')
    if "@Composable" in line:
        print(f"[{open_braces}] {i+1}: {line.strip()}")
