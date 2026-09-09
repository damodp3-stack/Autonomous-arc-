package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubConfigEntity
import com.example.data.GitHubConfigRepository
import com.example.github.GitHubAuthService
import com.example.github.GitHubRepository
import com.example.github.GitHubService
import com.example.github.GitHubUser
import com.example.github.GitHubBranch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch


enum class ChangeType { ADDED, MODIFIED, DELETED, UNCHANGED }

data class FileChange(
    val path: String,
    val changeType: ChangeType,
    val isBinary: Boolean,
    val size: Long,
    val contentBytes: ByteArray? = null,
    val remoteSha: String? = null
)

data class CommitSummary(
    val changes: List<FileChange>,
    val additions: Int,
    val modifications: Int,
    val deletions: Int,
    val totalChangedSize: Long
)

sealed class GitHubPushState {
    object Idle : GitHubPushState()
    object DetectingChanges : GitHubPushState()
    data class ChangesReady(val summary: CommitSummary) : GitHubPushState()
    object NoChanges : GitHubPushState()
    data class Committing(val progress: String) : GitHubPushState()
    object Pushing : GitHubPushState()
    object Success : GitHubPushState()
    data class Conflict(val message: String) : GitHubPushState()
    data class Error(val message: String) : GitHubPushState()
}

sealed class GitHubAuthState {
    object Checking : GitHubAuthState()
    object Unauthenticated : GitHubAuthState()
    data class Authenticated(val user: GitHubUser) : GitHubAuthState()
    data class Error(val message: String) : GitHubAuthState()
}

sealed class GitHubProjectState {
    object Loading : GitHubProjectState()
    object Disconnected : GitHubProjectState()
    data class Connected(val config: GitHubConfigEntity) : GitHubProjectState()
    data class Error(val message: String) : GitHubProjectState()
}

sealed class RepositoryDiscoveryState {
    object Idle : RepositoryDiscoveryState()
    object LoadingRepositories : RepositoryDiscoveryState()
    data class RepositoriesLoaded(val repositories: List<GitHubRepository>) : RepositoryDiscoveryState()
    data class LoadingBranches(val repository: GitHubRepository) : RepositoryDiscoveryState()
    data class BranchesLoaded(val repository: GitHubRepository, val branches: List<GitHubBranch>, val selectedBranch: GitHubBranch?) : RepositoryDiscoveryState()
    data class Conflict(val repository: GitHubRepository, val selectedBranch: GitHubBranch) : RepositoryDiscoveryState()
    data class Cloning(val progress: String) : RepositoryDiscoveryState()
    object Connecting : RepositoryDiscoveryState()
    data class Error(val message: String) : RepositoryDiscoveryState()
}

class GitHubViewModel(
    private val projectId: String,
    private val authService: GitHubAuthService,
    private val githubService: GitHubService,
    private val configRepository: GitHubConfigRepository,
    private val fileRepository: com.example.data.ProjectFileRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<GitHubAuthState>(GitHubAuthState.Checking)
    val authState: StateFlow<GitHubAuthState> = _authState

    private val _projectState = MutableStateFlow<GitHubProjectState>(GitHubProjectState.Loading)
    val projectState: StateFlow<GitHubProjectState> = _projectState

    private val _discoveryState = MutableStateFlow<RepositoryDiscoveryState>(RepositoryDiscoveryState.Idle)
    val discoveryState: StateFlow<RepositoryDiscoveryState> = _discoveryState

    private val _pushState = MutableStateFlow<GitHubPushState>(GitHubPushState.Idle)
    val pushState: StateFlow<GitHubPushState> = _pushState

    init {
        checkAuthStatus()
        observeProjectConfig()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            try {
                if (authService.isAuthenticated()) {
                    val user = authService.getAuthenticatedUser()
                    if (user != null) {
                        _authState.value = GitHubAuthState.Authenticated(user)
                    } else {
                        _authState.value = GitHubAuthState.Unauthenticated
                    }
                } else {
                    _authState.value = GitHubAuthState.Unauthenticated
                }
            } catch (e: Exception) {
                _authState.value = GitHubAuthState.Error(e.message ?: "Auth error")
            }
        }
    }

    fun authenticate(token: String) {
        viewModelScope.launch {
            _authState.value = GitHubAuthState.Checking
            try {
                val success = authService.authenticate(token)
                if (success) {
                    val user = authService.getAuthenticatedUser()
                    if (user != null) {
                        _authState.value = GitHubAuthState.Authenticated(user)
                    } else {
                        _authState.value = GitHubAuthState.Error("Failed to fetch user")
                    }
                } else {
                    _authState.value = GitHubAuthState.Error("Invalid token")
                }
            } catch (e: Exception) {
                _authState.value = GitHubAuthState.Error(e.message ?: "Auth exception")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authService.logout()
            _authState.value = GitHubAuthState.Unauthenticated
            _discoveryState.value = RepositoryDiscoveryState.Idle
        }
    }

    private fun observeProjectConfig() {
        viewModelScope.launch {
            configRepository.getConfigForProject(projectId).collect { config ->
                if (config != null) {
                    _projectState.value = GitHubProjectState.Connected(config)
                } else {
                    _projectState.value = GitHubProjectState.Disconnected
                }
            }
        }
    }

fun fetchRepositories() {
        viewModelScope.launch {
            _discoveryState.value = RepositoryDiscoveryState.LoadingRepositories
            try {
                val repos = githubService.getRepositories()
                _discoveryState.value = RepositoryDiscoveryState.RepositoriesLoaded(repos)
            } catch (e: Exception) {
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to fetch repositories")
            }
        }
    }

    fun selectRepository(repository: GitHubRepository) {
        viewModelScope.launch {
            _discoveryState.value = RepositoryDiscoveryState.LoadingBranches(repository)
            try {
                val owner = repository.fullName.substringBefore("/")
                val branches = githubService.getBranches(owner, repository.name)
                val defaultBranch = branches.find { it.name == repository.defaultBranch } ?: branches.firstOrNull()
                _discoveryState.value = RepositoryDiscoveryState.BranchesLoaded(repository, branches, defaultBranch)
            } catch (e: Exception) {
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to load branches")
            }
        }
    }

    fun selectBranch(branch: GitHubBranch) {
        val currentState = _discoveryState.value
        if (currentState is RepositoryDiscoveryState.BranchesLoaded) {
            _discoveryState.value = currentState.copy(selectedBranch = branch)
        }
    }



    private suspend fun fetchTreeRecursive(owner: String, repo: String, sha: String, currentPath: String = "", depth: Int = 0): List<com.example.github.GitHubTreeItem> {
        if (depth > 20) throw Exception("Repository directory depth too large")
        val tree = githubService.getTree(owner, repo, sha)
        val result = mutableListOf<com.example.github.GitHubTreeItem>()
        for (item in tree.tree) {
            val fullPath = if (currentPath.isEmpty()) item.path else "$currentPath/${item.path}"
            val updatedItem = item.copy(path = fullPath)
            result.add(updatedItem)
            
            if (item.type == "tree" && tree.truncated) {
                // If the root was truncated, we have to fetch sub-trees recursively to ensure completeness
                // In a robust app, we'd paginate or queue this, but for now we do simple DFS
                val subTreeItems = fetchTreeRecursive(owner, repo, item.sha, fullPath, depth + 1)
                result.addAll(subTreeItems)
            }
        }
        return result
    }

    fun connectRepository(force: Boolean = false) {
        val currentState = _discoveryState.value
        val (repository, selectedBranch) = when (currentState) {
            is RepositoryDiscoveryState.BranchesLoaded -> currentState.repository to currentState.selectedBranch
            is RepositoryDiscoveryState.Conflict -> currentState.repository to currentState.selectedBranch
            else -> return
        }

        if (selectedBranch == null) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (!force) {
                val existingFiles = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
                if (existingFiles.isNotEmpty()) {
                    _discoveryState.value = RepositoryDiscoveryState.Conflict(repository, selectedBranch)
                    return@launch
                }
            }

            _discoveryState.value = RepositoryDiscoveryState.Cloning("Preparing clone...")
            val stagingProjectId = "$projectId-staging"
            try {
                fileRepository.clearStagingProject(stagingProjectId)

                val owner = repository.fullName.substringBefore("/")
                val repo = repository.name
                
                _discoveryState.value = RepositoryDiscoveryState.Cloning("Fetching repository tree...")
                val rootTree = githubService.getTree(owner, repo, selectedBranch.commit.sha)
                
                val allTreeItems = if (rootTree.truncated) {
                    fetchTreeRecursive(owner, repo, selectedBranch.commit.sha)
                } else {
                    rootTree.tree
                }
                
                val maxFiles = 2000
                val maxFileSize = 10 * 1024 * 1024 // 10MB
                val maxTotalSize = 100 * 1024 * 1024 // 100MB
                
                var totalSize = 0L
                var fileCount = 0
                val normalizedPaths = mutableSetOf<String>()
                val textExtensions = setOf("txt", "kt", "java", "xml", "json", "md", "csv", "yml", "yaml", "html", "css", "js", "ts", "gradle", "properties", "sh", "bat", "py", "c", "cpp", "h", "hpp", "gitignore", "pro", "toml")
                
                val newEntities = mutableListOf<com.example.data.ProjectFileEntity>()
                val blobs = allTreeItems.filter { it.type == "blob" }
                val trees = allTreeItems.filter { it.type == "tree" }
                val totalBlobs = blobs.size
                
                if (totalBlobs > maxFiles) {
                    throw Exception("Repository has too many files ($totalBlobs > $maxFiles).")
                }
                
                for (item in trees) {
                    var path = item.path.replace("\\\\", "/")
                    path = path.replace(Regex("/+"), "/")
                    if (path.startsWith("/") || path.contains("..") || path.isEmpty()) {
                        throw Exception("Invalid path detected: ${item.path}")
                    }
                    if (!normalizedPaths.add(path)) {
                        throw Exception("Duplicate path collision detected: $path")
                    }
                    val dirSuccess = fileRepository.createStagingDirectory(stagingProjectId, path)
                    if (!dirSuccess) throw Exception("Directory/file collision or staging failure at $path")
                    
                    val name = path.substringAfterLast('/')
                    val parentPath = if (path.contains('/')) path.substringBeforeLast('/') else ""
                    newEntities.add(com.example.data.ProjectFileEntity(
                        projectId = projectId,
                        path = path,
                        name = name,
                        extension = "",
                        content = "",
                        isDirectory = true,
                        parentPath = parentPath
                    ))
                }

                for (item in blobs) {
                    var path = item.path.replace("\\\\", "/")
                    path = path.replace(Regex("/+"), "/")
                    if (path.startsWith("/") || path.contains("..") || path.isEmpty()) {
                        throw Exception("Invalid path detected: ${item.path}")
                    }
                    if (!normalizedPaths.add(path)) {
                        throw Exception("Duplicate path collision detected: $path")
                    }
                    
                    val size = item.size ?: 0
                    if (size > maxFileSize) {
                        throw Exception("File ${item.path} is too large ($size > $maxFileSize bytes).")
                    }
                    totalSize += size
                    if (totalSize > maxTotalSize) {
                        throw Exception("Repository exceeds maximum total size ($maxTotalSize bytes).")
                    }
                    
                    _discoveryState.value = RepositoryDiscoveryState.Cloning("Downloading file: ${item.path} ($fileCount/$totalBlobs)")
                    
                    val blob = githubService.getBlob(owner, repo, item.sha)
                    var bytes = ByteArray(0)
                    if (blob.encoding == "base64") {
                        val cleanBase64 = blob.content.replace("\n", "").replace("\r", "")
                        bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                    } else {
                        bytes = blob.content.toByteArray(kotlin.text.Charsets.UTF_8)
                    }
                    
                    val writeSuccess = fileRepository.writeStagingFileBytes(stagingProjectId, path, bytes)
                    if (!writeSuccess) throw Exception("Binary/download failure at $path")
                    
                    val name = path.substringAfterLast('/')
                    val extension = if (name.contains(".")) name.substringAfterLast('.') else ""
                    val parentPath = if (path.contains('/')) path.substringBeforeLast('/') else ""
                    val isText = textExtensions.contains(extension.lowercase())
                    val dbContent = if (isText) String(bytes, kotlin.text.Charsets.UTF_8) else "[BINARY FILE]"
                    
                    newEntities.add(com.example.data.ProjectFileEntity(
                        projectId = projectId,
                        path = path,
                        name = name,
                        extension = extension,
                        content = dbContent,
                        isDirectory = false,
                        parentPath = parentPath
                    ))
                    fileCount++
                }

                _discoveryState.value = RepositoryDiscoveryState.Connecting
                
                // Atomic replace
                val replaceSuccess = fileRepository.replaceProjectWorkspace(projectId, stagingProjectId, newEntities)
                if (!replaceSuccess) {
                    throw Exception("Replacement failure or Room persistence failure")
                }
                
                val config = com.example.data.GitHubConfigEntity(
                    projectId = projectId,
                    owner = owner,
                    repository = repo,
                    branch = selectedBranch.name,
                    isConnected = true,
                    lastRemoteSha = selectedBranch.commit.sha
                )
                configRepository.saveConfig(config)
                _discoveryState.value = RepositoryDiscoveryState.Idle // close discovery
            } catch (e: Exception) {
                fileRepository.clearStagingProject(stagingProjectId)
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to connect and clone")
            }
        }
    }


    private fun calculateGitSha(bytes: ByteArray): String {
        val header = "blob ${bytes.size}\u0000".toByteArray(kotlin.text.Charsets.UTF_8)
        val content = ByteArray(header.size + bytes.size)
        System.arraycopy(header, 0, content, 0, header.size)
        System.arraycopy(bytes, 0, content, header.size, bytes.size)
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val digest = md.digest(content)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun detectChanges() {
        val config = (_projectState.value as? GitHubProjectState.Connected)?.config
        if (config == null) {
            _pushState.value = GitHubPushState.Error("Not connected")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _pushState.value = GitHubPushState.DetectingChanges
            try {
                val ref = githubService.getRef(config.owner, config.repository, config.branch)
                if (ref.`object`.sha != config.lastRemoteSha && config.lastRemoteSha != null) {
                    _pushState.value = GitHubPushState.Conflict("Remote branch changed since last sync. Pull/reconcile required before pushing.")
                    return@launch
                }
                
                val currentRemoteSha = ref.`object`.sha

                val tree = githubService.getTree(config.owner, config.repository, currentRemoteSha)
                val allRemoteItems = if (tree.truncated) fetchTreeRecursive(config.owner, config.repository, currentRemoteSha) else tree.tree
                val remoteBlobs = allRemoteItems.filter { it.type == "blob" }.associateBy { it.path }

                val localFiles = fileRepository.getFilesForProject(projectId).firstOrNull() ?: emptyList()
                val localBlobs = localFiles.filter { !it.isDirectory }

                val changes = mutableListOf<FileChange>()
                var additions = 0
                var modifications = 0
                var deletions = 0
                var totalChangedSize = 0L

                for (localFile in localBlobs) {
                    val fsFile = fileRepository.fileSystem.getProjectFile(projectId, localFile.path)
                    if (fsFile == null || !fsFile.exists()) continue
                    
                    val bytes = fsFile.readBytes()
                    val localSha = calculateGitSha(bytes)
                    val remoteBlob = remoteBlobs[localFile.path]
                    val isBinary = localFile.content == "[BINARY FILE]"

                    if (remoteBlob == null) {
                        changes.add(FileChange(localFile.path, ChangeType.ADDED, isBinary, bytes.size.toLong(), bytes, null))
                        additions++
                        totalChangedSize += bytes.size
                    } else if (remoteBlob.sha != localSha) {
                        changes.add(FileChange(localFile.path, ChangeType.MODIFIED, isBinary, bytes.size.toLong(), bytes, remoteBlob.sha))
                        modifications++
                        totalChangedSize += bytes.size
                    }
                }

                val localPaths = localBlobs.map { it.path }.toSet()
                for ((path, remoteBlob) in remoteBlobs) {
                    if (!localPaths.contains(path)) {
                        changes.add(FileChange(path, ChangeType.DELETED, false, 0, null, remoteBlob.sha))
                        deletions++
                    }
                }

                if (changes.isEmpty()) {
                    _pushState.value = GitHubPushState.NoChanges
                } else {
                    _pushState.value = GitHubPushState.ChangesReady(CommitSummary(changes, additions, modifications, deletions, totalChangedSize))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _pushState.value = GitHubPushState.Error(e.message ?: "Failed to detect changes")
            }
        }
    }

    fun commitAndPush(message: String) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) {
            _pushState.value = GitHubPushState.Error("Commit message cannot be empty")
            return
        }

        val currentState = _pushState.value
        val summary = (currentState as? GitHubPushState.ChangesReady)?.summary
        if (summary == null || summary.changes.isEmpty()) {
            _pushState.value = GitHubPushState.Error("No changes to commit")
            return
        }

        val config = (_projectState.value as? GitHubProjectState.Connected)?.config
        if (config == null) {
            _pushState.value = GitHubPushState.Error("Not connected")
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _pushState.value = GitHubPushState.Committing("Starting commit process...")
            try {
                val ref = githubService.getRef(config.owner, config.repository, config.branch)
                if (ref.`object`.sha != config.lastRemoteSha && config.lastRemoteSha != null) {
                    _pushState.value = GitHubPushState.Conflict("Remote branch changed since last sync. Pull/reconcile required before pushing.")
                    return@launch
                }
                
                val currentRemoteSha = ref.`object`.sha

                val treeItems = mutableListOf<com.example.github.GitHubCreateTreeItem>()
                var current = 0
                val total = summary.changes.size

                for (change in summary.changes) {
                    current++
                    _pushState.value = GitHubPushState.Committing("Uploading changes ($current/$total)")
                    
                    if (change.changeType == ChangeType.DELETED) {
                        treeItems.add(
                            com.example.github.GitHubCreateTreeItem(
                                path = change.path,
                                mode = "100644",
                                type = "blob",
                                sha = null
                            )
                        )
                    } else {
                        val bytes = change.contentBytes ?: continue
                        val encoding = if (change.isBinary) "base64" else "utf-8"
                        val contentString = if (change.isBinary) {
                            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        } else {
                            String(bytes, kotlin.text.Charsets.UTF_8)
                        }

                        val blobResponse = githubService.createBlob(
                            config.owner, 
                            config.repository, 
                            com.example.github.GitHubCreateBlobRequest(contentString, encoding)
                        )

                        treeItems.add(
                            com.example.github.GitHubCreateTreeItem(
                                path = change.path,
                                mode = "100644",
                                type = "blob",
                                sha = blobResponse.sha
                            )
                        )
                    }
                }

                _pushState.value = GitHubPushState.Committing("Creating tree...")
                val treeResponse = githubService.createTree(
                    config.owner,
                    config.repository,
                    com.example.github.GitHubCreateTreeRequest(
                        baseTree = currentRemoteSha,
                        tree = treeItems
                    )
                )

                _pushState.value = GitHubPushState.Committing("Creating commit...")
                val commitResponse = githubService.createCommit(
                    config.owner,
                    config.repository,
                    com.example.github.GitHubCreateCommitRequest(
                        message = trimmedMessage,
                        tree = treeResponse.sha,
                        parents = listOf(currentRemoteSha)
                    )
                )

                _pushState.value = GitHubPushState.Pushing
                val updateResponse = githubService.updateRef(
                    config.owner,
                    config.repository,
                    config.branch,
                    com.example.github.GitHubUpdateRefRequest(
                        sha = commitResponse.sha,
                        force = false
                    )
                )

                val updatedConfig = config.copy(
                    lastRemoteSha = commitResponse.sha,
                    lastSyncAt = System.currentTimeMillis()
                )
                configRepository.saveConfig(updatedConfig)

                _pushState.value = GitHubPushState.Success

            } catch (e: Exception) {
                e.printStackTrace()
                _pushState.value = GitHubPushState.Error(e.message ?: "Failed to commit and push")
            }
        }
    }

    fun clearPushState() {
        _pushState.value = GitHubPushState.Idle
    }

    fun disconnectRepository() {
        viewModelScope.launch {
            try {
                configRepository.clearConfig(projectId)
            } catch (e: Exception) {
                _projectState.value = GitHubProjectState.Error(e.message ?: "Failed to disconnect")
            }
        }
    }
}
