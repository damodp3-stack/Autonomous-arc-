with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

import re

# Add apiKeyManager to WorkspaceViewModel constructor
content = content.replace(
    'class WorkspaceViewModel(\n    val projectId: String,\n    private val messageRepository: MessageRepository,\n    private val projectRepository: ProjectRepository,\n    val fileRepository: ProjectFileRepository,\n    private val aiFactory: com.example.ai.AIFactory\n) : ViewModel() {',
    'class WorkspaceViewModel(\n    val projectId: String,\n    private val messageRepository: MessageRepository,\n    private val projectRepository: ProjectRepository,\n    val fileRepository: ProjectFileRepository,\n    private val aiFactory: com.example.ai.AIFactory,\n    val apiKeyManager: com.example.ai.APIKeyManager\n) : ViewModel() {'
)

# Add apiKeyManager to WorkspaceViewModelFactory constructor
content = content.replace(
    'class WorkspaceViewModelFactory(\n    val projectId: String,\n    private val messageRepository: MessageRepository,\n    private val projectRepository: ProjectRepository,\n    val fileRepository: ProjectFileRepository,\n    private val aiFactory: com.example.ai.AIFactory\n) : ViewModelProvider.Factory {',
    'class WorkspaceViewModelFactory(\n    val projectId: String,\n    private val messageRepository: MessageRepository,\n    private val projectRepository: ProjectRepository,\n    val fileRepository: ProjectFileRepository,\n    private val aiFactory: com.example.ai.AIFactory,\n    private val apiKeyManager: com.example.ai.APIKeyManager\n) : ViewModelProvider.Factory {'
)

# Add apiKeyManager to ViewModel instantiation in factory
content = content.replace(
    'return WorkspaceViewModel(projectId, messageRepository, projectRepository, fileRepository, aiFactory) as T',
    'return WorkspaceViewModel(projectId, messageRepository, projectRepository, fileRepository, aiFactory, apiKeyManager) as T'
)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)
