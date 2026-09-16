with open('app/src/main/java/com/example/ai/OpenAIProvider.kt', 'r') as f:
    content = f.read()

content = content.replace("com.example.BuildConfig.OPENAI_API_KEY", '""')
with open('app/src/main/java/com/example/ai/OpenAIProvider.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ai/AnthropicProvider.kt', 'r') as f:
    content = f.read()

content = content.replace("com.example.BuildConfig.ANTHROPIC_API_KEY", '""')
with open('app/src/main/java/com/example/ai/AnthropicProvider.kt', 'w') as f:
    f.write(content)

import os
import glob
for path in glob.glob("app/src/main/java/com/example/ai/*RetrofitClient.kt"):
    with open(path, 'r') as f:
        content = f.read()
    content = content.replace("import okhttp.OkHttpClient", "import okhttp3.OkHttpClient")
    with open(path, 'w') as f:
        f.write(content)
