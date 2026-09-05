with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    lines = f.readlines()

package_idx = -1
for i, line in enumerate(lines):
    if line.startswith('package '):
        package_idx = i
        break

if package_idx > 0:
    imports = lines[:package_idx]
    package_line = lines[package_idx]
    rest = lines[package_idx+1:]
    
    with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
        f.write(package_line)
        for imp in imports:
            f.write(imp)
        for r in rest:
            f.write(r)
