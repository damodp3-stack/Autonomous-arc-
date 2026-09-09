import re

# Fix GitHubIntegrationDialog.kt imports
with open('app/src/main/java/com/example/ui/GitHubIntegrationDialog.kt', 'r') as f:
    content = f.read()

# Replace if it's somehow broken
content = content.replace("import com.example.github.GitHubRepository\nimport com.example.github.GitHubBranch\nimport com.example.github.GitHubBranch", "import com.example.github.GitHubRepository\nimport com.example.github.GitHubBranch")
content = content.replace("import com.example.github.GitHubRepository\nimport com.example.github.GitHubRepository\nimport com.example.github.GitHubBranch", "import com.example.github.GitHubRepository\nimport com.example.github.GitHubBranch")

# Make sure it's there
if 'import com.example.github.GitHubBranch' not in content:
    content = content.replace('import com.example.github.GitHubRepository', 'import com.example.github.GitHubRepository\nimport com.example.github.GitHubBranch')

# Clean up parameter types if they were fully qualified by mistake
content = content.replace("onSelectRepository: (GitHubRepository) -> Unit", "onSelectRepository: (com.example.github.GitHubRepository) -> Unit")
content = content.replace("onSelectBranch: (GitHubBranch) -> Unit", "onSelectBranch: (com.example.github.GitHubBranch) -> Unit")

with open('app/src/main/java/com/example/ui/GitHubIntegrationDialog.kt', 'w') as f:
    f.write(content)

# Fix GitHubViewModel.kt imports
with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace("import com.example.github.GitHubBranch\n\nsealed class RepositoryDiscoveryState", "sealed class RepositoryDiscoveryState")
content = content.replace("import com.example.github.GitHubUser", "import com.example.github.GitHubUser\nimport com.example.github.GitHubBranch")

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)

