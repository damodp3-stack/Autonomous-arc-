with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if 'replace("", "").replace("", "")' in line:
        lines[i] = '                        val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")\n'
    elif 'replace("\\\\", "/")' in line:
        pass
    elif 'replace("\\", "/")' in line:
        lines[i] = line.replace('replace("\\", "/")', 'replace("\\\\\\\\", "/")') # \\\\ in source file

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.writelines(lines)
