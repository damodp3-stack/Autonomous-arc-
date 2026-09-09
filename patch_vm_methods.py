import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

methods = """
    private fun calculateGitSha(bytes: ByteArray): String {
        val header = "blob ${bytes.size}\\u0000".toByteArray(kotlin.text.Charsets.UTF_8)
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
"""

content = content.replace("    fun disconnectRepository()", methods + "\n    fun disconnectRepository()")

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)
