with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'r') as f:
    content = f.read()

import re

new_content = """                        val content = if (blob.encoding == "base64") {
                            val cleanBase64 = blob.content.replace("\\n", "").replace("\\r", "")
                            String(android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT), kotlin.text.Charsets.UTF_8)
                        } else {
                            blob.content
                        }"""

content = re.sub(r'                        val content = if \(blob\.encoding == "base64"\) \{[\s\S]*?\} else \{[\s\S]*?\}', new_content, content)

with open('app/src/main/java/com/example/ui/GitHubViewModel.kt', 'w') as f:
    f.write(content)
