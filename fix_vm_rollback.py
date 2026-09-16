with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'codeChangeApplier.rollback(result.createdFileIds, result.snapshot)',
    'codeChangeApplier.rollback(projectId, result.createdFileIds, result.snapshot)'
)

with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'w') as f:
    f.write(content)
