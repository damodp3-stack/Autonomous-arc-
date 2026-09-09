import re

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

replacement = """
                        val blob = githubService.getBlob(owner, repo, item.sha)
                        val content = if (blob.encoding == "base64") {
                            val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")
                            String(android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT), kotlin.text.Charsets.UTF_8)
                        } else {
                            blob.content
                        }
"""

# Try to find the block
content = re.sub(r'                        val blob = githubService\.getBlob\(owner, repo, item\.sha\)[\s\S]*?\} else \{[\s\S]*?\}', replacement.strip(), content)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)

