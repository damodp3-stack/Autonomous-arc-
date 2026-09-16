with open('docs/PROJECT_STATE.md', 'r') as f:
    content = f.read()

content = content.replace("- True Autonomous Pipeline: autonomous plan/execute loops without human intervention for every file.", "")
content = content.replace("- API Keys must currently be configured via hardcoded `.env` rather than in-app settings menu.", "")
content = content.replace("- OpenAI and Anthropic are only stubbed and do not yet execute real network calls.", "")
content = content.replace("- AI currently prompts for human review for each change proposal, lacking true autonomous continuation loops.", "")

content = content.replace("**Completed**", "**Completed**\n- True Autonomous Pipeline for iterative development.")

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(content)

