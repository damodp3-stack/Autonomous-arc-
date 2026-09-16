with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

import re

# I will find the exact spot in WorkspaceScreen
# Search for `val applyResult by viewModel.applyResult.collectAsStateWithLifecycle()`

def replacer(match):
    return match.group(0) + "\n    val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()\n    val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()\n"

content = re.sub(r"val applyResult by viewModel\.applyResult\.collectAsStateWithLifecycle\(\)", replacer, content)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)

