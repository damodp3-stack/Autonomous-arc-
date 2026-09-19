with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

# I want to find the UI code that renders the autonomous running bar.
# It's inside a TopAppBar or a Box?
# Let's search for "if (isAutonomousRunning)"

old_bar = """                                        if (isAutonomousRunning) {
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

new_bar = """                                        if (isAutonomousRunning) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                if (autonomousPlan != null) {
                                                    Text("Goal: ${autonomousPlan!!.goal}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                                    if (autonomousTaskIndex >= 0 && autonomousTaskIndex < autonomousPlan!!.tasks.size) {
                                                        val currentTask = autonomousPlan!!.tasks[autonomousTaskIndex]
                                                        Text(
                                                            text = "Task ${autonomousTaskIndex + 1}/${autonomousPlan!!.tasks.size}: ${currentTask.description}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "State: ${autonomousState.name} | Iter: $autonomousIteration",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = autonomousAction ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }"""

content = content.replace(old_bar, new_bar)

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)

