with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "val cleanBase64 = blob.content.replace" in line:
        lines[i] = '                            val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")\n'
    if '", "").replace("\\n", "")' in line:
        lines[i] = '' # delete it

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.writelines(lines)
