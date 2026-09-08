with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

init_old = """    init {
        viewModelScope.launch {
            projectRepository.getProject(projectId).collect { project ->
                if (project != null) {
                    _projectName.value = project.name
                }
            }
        }
    }"""
    
init_new = """    init {
        viewModelScope.launch {
            fileRepository.syncProjectFilesToSystem(projectId)
            projectRepository.getProject(projectId).collect { project ->
                if (project != null) {
                    _projectName.value = project.name
                }
            }
        }
    }"""

content = content.replace(init_old, init_new)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)
