with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

import re

# Add task index and plan
#    val autonomousAction by viewModel.autonomousEngine.lastAction.collectAsStateWithLifecycle()

replacer = """    val autonomousAction by viewModel.autonomousEngine.lastAction.collectAsStateWithLifecycle()
    val autonomousPlan by viewModel.autonomousEngine.currentPlan.collectAsStateWithLifecycle()
    val autonomousTaskIndex by viewModel.autonomousEngine.currentTaskIndex.collectAsStateWithLifecycle()
"""
content = content.replace("    val autonomousAction by viewModel.autonomousEngine.lastAction.collectAsStateWithLifecycle()\n", replacer)

old_status = """                                        if (isAutonomousRunning) {
                                            Text(
                                                text = "Iter $autonomousIteration | $autonomousAction",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }"""

new_status = """                                        if (isAutonomousRunning) {
                                            val taskText = if (autonomousPlan != null && autonomousTaskIndex >= 0 && autonomousTaskIndex < autonomousPlan!!.tasks.size) {
                                                "Task ${autonomousTaskIndex + 1}/${autonomousPlan!!.tasks.size} | "
                                            } else ""
                                            Text(
                                                text = "Iter $autonomousIteration | $taskText$autonomousAction",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }"""

content = content.replace(old_status, new_status)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)

