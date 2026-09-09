import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

replacement = """
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
                val existingFiles = kotlinx.coroutines.flow.firstOrNull(fileRepository.getFilesForProject(projectId)) ?: emptyList()
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
                
                var totalSize = 0
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
                    fileRepository.createStagingDirectory(stagingProjectId, path)
                    
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
                        val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")
                        bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                    } else {
                        bytes = blob.content.toByteArray(kotlin.text.Charsets.UTF_8)
                    }
                    
                    fileRepository.writeStagingFileBytes(stagingProjectId, path, bytes)
                    
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
                    throw Exception("Failed to atomically replace project workspace")
                }
                
                val config = com.example.data.GitHubConfigEntity(
                    projectId = projectId,
                    owner = owner,
                    repository = repo,
                    branch = selectedBranch.name,
                    isConnected = true
                )
                configRepository.saveConfig(config)
                _discoveryState.value = RepositoryDiscoveryState.Idle // close discovery
            } catch (e: Exception) {
                fileRepository.clearStagingProject(stagingProjectId)
                _discoveryState.value = RepositoryDiscoveryState.Error(e.message ?: "Failed to connect and clone")
            }
        }
    }
"""

content = re.sub(r'    fun connectRepository\([\s\S]*?fun disconnectRepository', replacement + '\n    fun disconnectRepository', content)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)
