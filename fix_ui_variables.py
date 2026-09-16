with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

import re
old_states = """    val proposalError by viewModel.proposalError.collectAsStateWithLifecycle()
    val applyResult by viewModel.applyResult.collectAsStateWithLifecycle()"""

new_states = """    val proposalError by viewModel.proposalError.collectAsStateWithLifecycle()
    val applyResult by viewModel.applyResult.collectAsStateWithLifecycle()
    val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()
    val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()"""

# I need to find the correct spot for WorkspaceScreen
# Let's replace the whole thing just in case it was injected wrongly
content = content.replace("    val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()\n    val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()", "")

content = content.replace(old_states, new_states)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
