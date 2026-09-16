import re

with open('app/src/main/java/com/example/github/RealGitHubSyncService.kt', 'r') as f:
    content = f.read()

pull_impl = """    override suspend fun pull(projectId: String, progress: (String) -> Unit): SyncResult {
        val config = configRepository.getConfigForProject(projectId).firstOrNull() ?: return SyncResult.Error("Not connected")
        
        val stagingProjectId = "$projectId-staging"
        try {
            fileRepository.clearStagingProject(stagingProjectId)
            
            progress("Fetching current remote ref...")
            val ref = githubService.getRef(config.owner, config.repository, config.branch)
            val currentRemoteSha = ref.`object`.sha
            
            progress("Fetching repository tree...")
            val rootTree = githubService.getTree(config.owner, config.repository, currentRemoteSha)
            val allTreeItems = if (rootTree.truncated) {
                fetchTreeRecursive(config.owner, config.repository, currentRemoteSha)
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
                var path = item.path.replace("\\\\\\\\", "/")
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
                var path = item.path.replace("\\\\\\\\", "/")
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
                
                progress("Downloading file: ${item.path} ($fileCount/$totalBlobs)")
                
                val blob = githubService.getBlob(config.owner, config.repository, item.sha)
                var bytes = ByteArray(0)
                if (blob.encoding == "base64") {
                    val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")
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
            
            progress("Replacing local workspace...")
            val replaceSuccess = fileRepository.replaceProjectWorkspace(projectId, stagingProjectId, newEntities)
            if (!replaceSuccess) {
                throw Exception("Replacement failure or Room persistence failure")
            }
            
            val updatedConfig = config.copy(
                lastRemoteSha = currentRemoteSha,
                lastSyncAt = System.currentTimeMillis()
            )
            configRepository.saveConfig(updatedConfig)
            
            return SyncResult.Success
        } catch (e: Exception) {
            fileRepository.clearStagingProject(stagingProjectId)
            return SyncResult.Error(e.message ?: "Failed to pull")
        }
    }"""

sync_impl = """    override suspend fun sync(projectId: String, progress: (String) -> Unit): SyncResult {
        try {
            val config = configRepository.getConfigForProject(projectId).firstOrNull() ?: return SyncResult.Error("Not connected")
            
            progress("Checking remote branch...")
            val ref = githubService.getRef(config.owner, config.repository, config.branch)
            val currentRemoteSha = ref.`object`.sha
            
            val remoteHasChanges = currentRemoteSha != config.lastRemoteSha
            
            val localSummary = detectChanges(projectId)
            val localHasChanges = localSummary != null && localSummary.changes.isNotEmpty()
            
            if (!remoteHasChanges && !localHasChanges) {
                return SyncResult.Success
            } else if (!remoteHasChanges && localHasChanges) {
                return SyncResult.Conflict("Local changes detected. Please commit and push.")
            } else if (remoteHasChanges && !localHasChanges) {
                return pull(projectId, progress)
            } else {
                return SyncResult.Conflict("Both local and remote have changes. Cannot auto-merge.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return SyncResult.Error(e.message ?: "Failed to sync")
        }
    }"""

# replace pull
content = re.sub(r'override suspend fun pull.*?return SyncResult\.Error\("Not implemented"\)\s*\}', pull_impl, content, flags=re.DOTALL)
# replace sync
content = re.sub(r'override suspend fun sync.*?return SyncResult\.Error\("Not implemented"\)\s*\}', sync_impl, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/github/RealGitHubSyncService.kt', 'w') as f:
    f.write(content)

