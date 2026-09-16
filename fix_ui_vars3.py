with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

# Let's insert the missing state properties in WorkspaceScreen function.
import re

def insert_states(match):
    return match.group(0) + """
    val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()
    val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()
"""

# The best place to insert it is right after `val applyResult = ...`
content = re.sub(r'val applyResult by viewModel\.applyResult\.collectAsStateWithLifecycle\(\)', insert_states, content)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)

