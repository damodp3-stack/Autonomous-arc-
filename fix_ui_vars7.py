with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

import re

# Remove the incorrectly placed ones at the bottom
content = content.replace("    val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()\n    val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()\n", "")

# Insert in the correct place
def replacer(match):
    return match.group(0) + "\n    val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()\n    val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()\n"

content = re.sub(r"val proposalError by viewModel\.proposalError\.collectAsStateWithLifecycle\(\)", replacer, content)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)

