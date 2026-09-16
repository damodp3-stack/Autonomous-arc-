with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()" in line:
        pass # Skip it entirely
    elif "val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()" in line:
        pass # Skip it entirely
    else:
        new_lines.append(line)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.writelines(new_lines)
