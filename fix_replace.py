with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if 'val content = if (blob.encoding == "base64") {' in line:
        skip = True
        new_lines.append(line)
        new_lines.append('                            val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")\n')
        new_lines.append('                            String(android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT), kotlin.text.Charsets.UTF_8)\n')
        new_lines.append('                        } else {\n')
        new_lines.append('                            blob.content\n')
        new_lines.append('                        }\n')
        continue
    if skip and 'fileRepository.createFile(' in line:
        skip = False
    
    if not skip:
        new_lines.append(line)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.writelines(new_lines)

