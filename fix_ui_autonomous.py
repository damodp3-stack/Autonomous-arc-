with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

import re

# Insert state collectors
old_states = """    val proposalState by viewModel.proposalState.collectAsStateWithLifecycle()
    val currentProposal by viewModel.currentProposal.collectAsStateWithLifecycle()
    val proposalError by viewModel.proposalError.collectAsStateWithLifecycle()
    val applyResult by viewModel.applyResult.collectAsStateWithLifecycle()"""

new_states = """    val proposalState by viewModel.proposalState.collectAsStateWithLifecycle()
    val currentProposal by viewModel.currentProposal.collectAsStateWithLifecycle()
    val proposalError by viewModel.proposalError.collectAsStateWithLifecycle()
    val applyResult by viewModel.applyResult.collectAsStateWithLifecycle()
    val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()
    val isAutonomousRunning by viewModel.isAutonomousRunning.collectAsStateWithLifecycle()"""

content = content.replace(old_states, new_states)

# Update UI
old_row = """                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton("""

new_row = """                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isAutonomousRunning) {
                                            Button(
                                                onClick = { viewModel.stopAutonomousRun() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text("STOP AUTO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                            }
                                        } else {
                                            TextButton(
                                                onClick = { viewModel.toggleAutonomousMode() },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = if (isAutonomousMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Text(if (isAutonomousMode) "AUTO ON" else "AUTO OFF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        TextButton("""

content = content.replace(old_row, new_row)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
