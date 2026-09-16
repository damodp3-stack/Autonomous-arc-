import re
with open('app/src/main/java/com/example/ai/GeminiAIProvider.kt', 'r') as f:
    content = f.read()

validation_logic = """
                // Validate proposal
                val validatedChanges = proposal.changes.map { change ->
                    val path = change.filePath
                    if (path.contains("../") || path.startsWith("/") || path.contains("\\\\")) {
                        throw IllegalStateException("Security Error: Invalid path in AI proposal ($path).")
                    }
                    when (change.operation) {
                        FileOperation.RENAME -> {
                            val newPath = change.newFilePath
                            if (newPath.isNullOrBlank()) {
                                throw IllegalStateException("Error: RENAME operation requires newFilePath.")
                            }
                            if (newPath.contains("../") || newPath.startsWith("/") || newPath.contains("\\\\")) {
                                throw IllegalStateException("Security Error: Invalid newFilePath in AI proposal ($newPath).")
                            }
                        }
                        FileOperation.CREATE -> {
                            if (change.proposedContent == null) {
                                throw IllegalStateException("Error: CREATE operation requires proposedContent.")
                            }
                        }
                        FileOperation.MODIFY -> {
                            if (change.originalContent == null || change.proposedContent == null) {
                                throw IllegalStateException("Error: MODIFY operation requires originalContent and proposedContent.")
                            }
                        }
                        FileOperation.DELETE -> {
                            // originalContent is optional but good if provided
                        }
                    }
                    change
                }
                proposal.copy(changes = validatedChanges)
"""

old_validation = """
                // Validate paths
                val validatedChanges = proposal.changes.map { change ->
                    if (change.filePath.contains("../") || change.filePath.startsWith("/")) {
                        throw IllegalStateException("Security Error: Path traversal detected in AI proposal.")
                    }
                    change
                }
                proposal.copy(changes = validatedChanges)
"""

content = content.replace(old_validation.strip(), validation_logic.strip())

context_logic = """
                if (projectContext.files.isNotEmpty()) {
                    append("Other files in project:\\n")
                    var currentContextSize = 0
                    val MAX_CONTEXT_SIZE = 100_000
                    projectContext.files.forEach { file ->
                        if (file.id != projectContext.currentOpenFile?.id) {
                            val fileHeader = "\\n--- ${file.path} ---\\n"
                            val fileContentSize = file.content.length
                            if (currentContextSize + fileContentSize < MAX_CONTEXT_SIZE) {
                                append(fileHeader)
                                append(file.content)
                                append("\\n")
                                currentContextSize += fileContentSize
                            } else {
                                append("- ${file.path} (Content omitted due to size limits)\\n")
                            }
                        }
                    }
                }
"""

old_context = """
                if (projectContext.files.isNotEmpty()) {
                    append("Other files in project:\\n")
                    projectContext.files.forEach { file ->
                        if (file.id != projectContext.currentOpenFile?.id) {
                            append("- ${file.path}\\n")
                        }
                    }
                }
"""

content = content.replace(old_context.strip(), context_logic.strip())

with open('app/src/main/java/com/example/ai/GeminiAIProvider.kt', 'w') as f:
    f.write(content)
