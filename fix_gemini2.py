with open('app/src/main/java/com/example/ai/GeminiAIProvider.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '@POST("v1beta/models/gemini-3.5-flash:generateContent")',
    '@POST("v1beta/models/{model}:generateContent")'
)

content = content.replace(
    'GeminiRetrofitClient.service.generateContent(apiKey, request)',
    'GeminiRetrofitClient.service.generateContent(model, apiKey, request)'
)

content = content.replace(
    'GeminiRetrofitClient.service.generateContent(apiKey, generateContentRequest)',
    'GeminiRetrofitClient.service.generateContent(model, apiKey, generateContentRequest)'
)

with open('app/src/main/java/com/example/ai/GeminiAIProvider.kt', 'w') as f:
    f.write(content)
