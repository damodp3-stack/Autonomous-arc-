import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    vm_content = f.read()

models = """enum class ChangeType { ADDED, MODIFIED, DELETED, UNCHANGED }

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
)"""

vm_content = vm_content.replace(models, "import com.example.github.*\n")

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(vm_content)

with open('app/src/main/java/com/example/github/GitHubModels.kt', 'r') as f:
    models_content = f.read()

models_content += "\n" + models + "\n"

with open('app/src/main/java/com/example/github/GitHubModels.kt', 'w') as f:
    f.write(models_content)

