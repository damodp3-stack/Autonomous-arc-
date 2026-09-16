with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

import re

# We need to change `val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()`
# to `val autonomousState by viewModel.autonomousEngine.state.collectAsStateWithLifecycle()`
# and `val autonomousIteration by viewModel.autonomousEngine.iteration.collectAsStateWithLifecycle()`
# and `val autonomousMaxIterations by viewModel.autonomousEngine.maxIterations.collectAsStateWithLifecycle()`
# and `val autonomousAction by viewModel.autonomousEngine.lastAction.collectAsStateWithLifecycle()`

def replacer(match):
    return """    val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()
    val autonomousState by viewModel.autonomousEngine.state.collectAsStateWithLifecycle()
    val autonomousIteration by viewModel.autonomousEngine.iteration.collectAsStateWithLifecycle()
    val autonomousAction by viewModel.autonomousEngine.lastAction.collectAsStateWithLifecycle()
    val isAutonomousRunning = autonomousState != com.example.ai.AutonomousState.IDLE && autonomousState != com.example.ai.AutonomousState.COMPLETED && autonomousState != com.example.ai.AutonomousState.FAILED && autonomousState != com.example.ai.AutonomousState.BLOCKED && autonomousState != com.example.ai.AutonomousState.STOPPED
"""

content = re.sub(r"    val isAutonomousMode by viewModel\.isAutonomousMode\.collectAsStateWithLifecycle\(\)\n    val isAutonomousRunning by viewModel\.isAutonomousRunning\.collectAsStateWithLifecycle\(\)\n", replacer, content)

# There is also a place where it shows the UI for Autonomous.
# Let's add a small status indicator
old_status = """                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {"""

new_status = """                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isAutonomousRunning) {
                                            Text(
                                                text = "Iter $autonomousIteration | $autonomousAction",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }"""

content = content.replace(old_status, new_status)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
