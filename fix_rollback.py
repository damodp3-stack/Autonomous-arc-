with open('app/src/main/java/com/example/ai/CodeChangeApplier.kt', 'r') as f:
    content = f.read()

old_rollback = """    suspend fun rollback(
        createdFileIds: List<String>,
        snapshot: Map<String, ProjectFileEntity?>
    ) {"""

new_rollback = """    suspend fun rollback(
        projectId: String,
        createdFileIds: List<String>,
        snapshot: Map<String, ProjectFileEntity?>
    ) {"""

content = content.replace(old_rollback, new_rollback)

old_catch = """                // Application error during a change
                val rollbackSuccess = try {
                    rollback(createdFileIds, snapshot)
                    true"""

new_catch = """                // Application error during a change
                val rollbackSuccess = try {
                    rollback(projectId, createdFileIds, snapshot)
                    true"""

content = content.replace(old_catch, new_catch)

old_rollback_body = """            } else {
                // For RENAME target, the entity in snapshot is null.
                // The new file was created. We need to delete it.
                // But wait, renameFile modifies the existing file's path. We just restored the original file above.
                // So the old path is back. But we need to delete the new path file!
                // Actually, renameFile modifies the entity in the DB. restoreFile(entity) restores the old entity.
                // So the file in DB with new path is gone because it was overwritten by restoreFile?
                // Wait, restoreFile uses the old ID. If Room REPLACE is used, it overwrites the record with the old path.
                // What about the filesystem? restoreFile writes to disk at the old path.
                // So the file at the new path on disk remains! We should delete it.
                // But we don't have its ID if we only have the snapshot.
                // We'll need the repository to clean it up.
                // Actually, let's leave this for now. The requirement was to just implement the foundation.
            }"""

new_rollback_body = """            } else {
                val path = it.key
                repository.fileSystem.deleteFile(projectId, path)
                // If it was somehow recorded in DB under a new ID, we'd delete it, but for RENAME the ID is reused.
                // For CREATE, createdFileIds handles deletion.
            }"""

content = content.replace(old_rollback_body, new_rollback_body)
# Also fix the `for ((_, entity) in snapshot)` to `for (it in snapshot)` if we use `it.key` or we can just change to `for ((path, entity) in snapshot)`

content = content.replace("for ((_, entity) in snapshot) {", "for ((path, entity) in snapshot) {")
content = content.replace("val path = it.key", "") # Because path is already bound

with open('app/src/main/java/com/example/ai/CodeChangeApplier.kt', 'w') as f:
    f.write(content)
