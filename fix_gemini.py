with open('app/src/main/java/com/example/ai/GeminiAIProvider.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'class GeminiAIProvider(private val projectName: String) : AIProvider {',
    'class GeminiAIProvider(private val projectName: String, private val providedApiKey: String? = null, private val model: String = "gemini-1.5-pro") : AIProvider {'
)
content = content.replace(
    'val apiKey = BuildConfig.GEMINI_API_KEY',
    'val apiKey = providedApiKey ?: BuildConfig.GEMINI_API_KEY'
)

# Also fix the URL if it hardcodes the model
content = content.replace(
    '@POST("v1beta/models/gemini-1.5-pro:generateContent")',
    '@POST("v1beta/models/{model}:generateContent")'
)

content = content.replace(
    'suspend fun generateContent(',
    'suspend fun generateContent(\n        @retrofit2.http.Path("model") model: String,\n'
)

with open('app/src/main/java/com/example/ai/GeminiAIProvider.kt', 'w') as f:
    f.write(content)
