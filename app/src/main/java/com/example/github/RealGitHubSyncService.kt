package com.example.github

import com.example.data.GitHubConfigRepository
import com.example.data.ProjectFileRepository
import kotlinx.coroutines.flow.firstOrNull
import java.security.MessageDigest

class RealGitHubSyncService(
    private val githubService: GitHubService,
    private val fileRepository: ProjectFileRepository,
    private val configRepository: GitHubConfigRepository
) : GitHubSyncService {

    private fun calculateGitSha(content: ByteArray): String {
        val prefix = "blob ${content.size}\u0000".toByteArray(kotlin.text.Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-1")
        md.update(prefix)
        md.update(content)
        val digest = md.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun fetchTreeRecursive(
        owner: String,
        repo: String,
        treeSha: String,
        depth: Int = 0
    ): List<GitHubTreeItem> {
        if (depth > 10) throw Exception("Repository depth too large")
        val tree = githubService.getTree(owner, repo, treeSha)
        val allItems = mutableListOf<GitHubTreeItem>()
        for (item in tree.tree) {
            allItems.add(item)
            if (item.type == "tree" && item.sha != null) {
                val subItems = fetchTreeRecursive(owner, repo, item.sha, depth + 1)
                val nested = subItems.map { subItem ->
                    subItem.copy(path = "${item.path}/${subItem.path}")
                }
                allItems.addAll(nested)
            }
        }
        return allItems
    }

    override suspend fun detectChanges(projectId: String): CommitSummary? {
        val config = configRepository.getConfigForProject(projectId).firstOrNull() ?: return null
        
        val ref = githubService.getRef(config.owner, config.repository, config.branch)
        if (ref.`object`.sha != config.lastRemoteSha && config.lastRemoteSha != null) {
            throw Exception("Remote branch changed since last sync. Pull/reconcile required before pushing.")
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

        val maxFileSize = 10 * 1024 * 1024 // 10MB
        val maxTotalSize = 100 * 1024 * 1024 // 100MB

        for (localFile in localBlobs) {
            var path = localFile.path.replace("\\\\", "/")
            path = path.replace(Regex("/+"), "/")
            if (path.startsWith("/") || path.contains("..") || path.isEmpty()) {
                throw Exception("Invalid path detected: ${localFile.path}")
            }
            val fsFile = fileRepository.fileSystem.getProjectFile(projectId, localFile.path)
            if (fsFile == null || !fsFile.exists()) continue
            
            val bytes = fsFile.readBytes()
            if (bytes.size > maxFileSize) {
                throw Exception("File ${localFile.path} is too large (${bytes.size} > $maxFileSize bytes).")
            }
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

            if (totalChangedSize > maxTotalSize) {
                throw Exception("Total changes exceed maximum size ($maxTotalSize bytes).")
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
            return null
        }
        return CommitSummary(changes, additions, modifications, deletions, totalChangedSize)
    }

    override suspend fun push(projectId: String, commitMessage: String, summary: CommitSummary, progress: (String) -> Unit): SyncResult {
        try {
            val config = configRepository.getConfigForProject(projectId).firstOrNull()
                ?: return SyncResult.Error("Not connected")
            
            progress("Checking remote branch...")
            val ref = githubService.getRef(config.owner, config.repository, config.branch)
            if (ref.`object`.sha != config.lastRemoteSha && config.lastRemoteSha != null) {
                return SyncResult.Conflict("Remote branch changed since last sync. Pull/reconcile required before pushing.")
            }
            
            val currentRemoteSha = ref.`object`.sha
            val treeItems = mutableListOf<GitHubCreateTreeItem>()
            
            var current = 0
            val total = summary.changes.size
            for (change in summary.changes) {
                current++
                progress("Uploading changes ($current/$total)")
                
                if (change.changeType == ChangeType.DELETED) {
                    treeItems.add(
                        GitHubCreateTreeItem(
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
                        GitHubCreateBlobRequest(contentString, encoding)
                    )
                    treeItems.add(
                        GitHubCreateTreeItem(
                            path = change.path,
                            mode = "100644",
                            type = "blob",
                            sha = blobResponse.sha
                        )
                    )
                }
            }
            
            progress("Creating tree...")
            val treeResponse = githubService.createTree(
                config.owner,
                config.repository,
                GitHubCreateTreeRequest(
                    baseTree = currentRemoteSha,
                    tree = treeItems
                )
            )
            
            progress("Creating commit...")
            val commitResponse = githubService.createCommit(
                config.owner,
                config.repository,
                GitHubCreateCommitRequest(
                    message = commitMessage,
                    tree = treeResponse.sha,
                    parents = listOf(currentRemoteSha)
                )
            )
            
            progress("Updating branch...")
            val updateResponse = githubService.updateRef(
                config.owner,
                config.repository,
                config.branch,
                GitHubUpdateRefRequest(
                    sha = commitResponse.sha,
                    force = false
                )
            )
            
            progress("Saving sync state...")
            val updatedConfig = config.copy(
                lastRemoteSha = commitResponse.sha,
                lastSyncAt = System.currentTimeMillis()
            )
            configRepository.saveConfig(updatedConfig)
            
            return SyncResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            return SyncResult.Error(e.message ?: "Failed to push")
        }
    }

    override suspend fun pull(projectId: String, progress: (String) -> Unit): SyncResult {
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
                
                progress("Downloading file: ${item.path} ($fileCount/$totalBlobs)")
                
                val blob = githubService.getBlob(config.owner, config.repository, item.sha)
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
    }

    override suspend fun sync(projectId: String, progress: (String) -> Unit): SyncResult {
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
    }
}
