with open('docs/PROJECT_STATE.md', 'r') as f:
    content = f.read()

content = content.replace(
    "- Actual implementations for Anthropic and OpenAI REST clients (currently stubbed).",
    ""
)
content = content.replace(
    "- UI for entering and managing custom API keys via SecureAPIKeyManager.",
    ""
)

content = content.replace(
    "**Completed**",
    "**Completed**\n- Actual implementations for Anthropic and OpenAI REST clients.\n- UI for entering and managing custom API keys via SecureAPIKeyManager."
)

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(content)
