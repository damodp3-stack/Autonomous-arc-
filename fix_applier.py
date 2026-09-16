with open('app/src/main/java/com/example/ai/CodeChangeApplier.kt', 'r') as f:
    content = f.read()

# Add newFilePath normalization
content = content.replace(
    'normalizedChanges.add(change.copy(filePath = normalizedPath))',
    '''val normalizedNewPath = change.newFilePath?.let { normalizePath(it) }
            if (change.operation == FileOperation.RENAME && normalizedNewPath == null) {
                return ApplyResult.ValidationError("Security Error: Invalid or traversal path detected for rename target - ${change.newFilePath}")
            }
            if (normalizedNewPath != null && !seenPaths.add(normalizedNewPath)) {
                return ApplyResult.ValidationError("Duplicate path detected in proposal: $normalizedNewPath")
            }
            normalizedChanges.add(change.copy(filePath = normalizedPath, newFilePath = normalizedNewPath))'''
)

content = content.replace(
    'when (change.operation) {',
    '''when (change.operation) {
                FileOperation.RENAME -> {
                    if (existingFile == null) {
                        return ApplyResult.ValidationError("File not found for rename: $path")
                    }
                    val targetFile = repository.getFileByPath(projectId, change.newFilePath!!)
                    if (targetFile != null) {
                        return ApplyResult.Conflict("Target file already exists for rename", change.newFilePath!!)
                    }
                    snapshot[change.newFilePath!!] = null
                }'''
)

content = content.replace(
    'when (change.operation) {',
    '''when (change.operation) {
                    FileOperation.RENAME -> {
                        val success = repository.renameFile(existingFile!!.id, change.newFilePath!!)
                        if (!success) {
                            throw Exception("Failed to rename file on filesystem")
                        }
                    }'''
)

with open('app/src/main/java/com/example/ai/CodeChangeApplier.kt', 'w') as f:
    f.write(content)
