with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'r') as f:
    content = f.read()

# Add the variable
target = 'val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()'
content = content.replace(target, 'var showGitHubDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }\n    ' + target)

# Add the import
target_import = 'import androidx.compose.material.icons.filled.Close'
content = content.replace(target_import, target_import + '\nimport androidx.compose.material.icons.filled.CloudQueue')

with open('app/src/main/java/com/example/ui/WorkspaceScreen.kt', 'w') as f:
    f.write(content)
