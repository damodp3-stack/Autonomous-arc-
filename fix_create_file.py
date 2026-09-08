with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

old_create = """    fun createFile(path: String) {
        viewModelScope.launch {
            val newFile = fileRepository.createFile(projectId, path)
            selectFile(newFile)
        }
    }"""

new_create = """    fun createFile(path: String) {
        viewModelScope.launch {
            val newFile = fileRepository.createFile(projectId, path)
            if (newFile != null) {
                selectFile(newFile)
            }
        }
    }"""

content = content.replace(old_create, new_create)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ai/CodeChangeApplier.kt', 'r') as f:
    content = f.read()

old_create2 = """                    FileOperation.CREATE -> {
                        val newFile = repository.createFile(projectId, path, change.proposedContent)
                        createdFileIds.add(newFile.id)
                    }"""

new_create2 = """                    FileOperation.CREATE -> {
                        val newFile = repository.createFile(projectId, path, change.proposedContent)
                        if (newFile != null) {
                            createdFileIds.add(newFile.id)
                        } else {
                            throw Exception("Failed to write to filesystem")
                        }
                    }"""

content = content.replace(old_create2, new_create2)

with open('app/src/main/java/com/example/ai/CodeChangeApplier.kt', 'w') as f:
    f.write(content)

