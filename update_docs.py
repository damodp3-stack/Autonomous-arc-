import re

with open('docs/PROJECT_STATE.md', 'r') as f:
    state = f.read()

# Update Pending
state = state.replace(
    '- GitHub Integration (Clone, Commit, Push, Conflict Detection).\n',
    ''
)

# Update Completed
state = state.replace(
    '**Completed**\n',
    '**Completed**\n- GitHub Integration (Clone, Commit, Push, Conflict Detection).\n- Multi-Provider AI Architecture (Gemini, OpenAI, Anthropic support via AIFactory and AIProviderConfigEntity).\n- Secure API Key Storage Foundation (APIKeyManager).\n- Structured File Operations expanded to include RENAME.\n'
)

# Update Verified
state = state.replace(
    '**Verified**\n',
    '**Verified**\n- Multi-Provider AI Architecture compilation and routing (Verified).\n- File operations expanded to support RENAME with safe Apply/Rollback (Verified).\n'
)

# Update Current Architecture
state = state.replace(
    '- Interface-driven AI abstraction (`AIProvider` with `GeminiAIProvider` and `MockAIProvider`).\n',
    '- Dynamic Multi-Provider AI abstraction (`AIFactory`, `AIProviderConfigEntity`, `OpenAIProvider`, `AnthropicProvider`, `GeminiAIProvider`, `MockAIProvider`).\n'
)

# Update Immediate Next Step
state = re.sub(
    r'\*\*Immediate Next Step\*\*\n- .*\n',
    '**Immediate Next Step**\n- Core AI app-generation pipeline refinement and UI/UX integration for provider/model configuration.\n',
    state
)

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(state)

# Now update CHANGELOG.md
from datetime import datetime
date_str = datetime.now().strftime("%Y-%m-%d")

with open('docs/CHANGELOG.md', 'r') as f:
    changelog = f.read()

new_log = f"""## [Unreleased] - {date_str}
### Added
- **AI Provider Abstraction**: Introduced `AIProviderConfigEntity` and `AIProviderConfigDao` in Room for multi-provider configurations.
- **Provider Routing**: Created `AIFactory` to dynamically resolve the active AI provider based on configuration.
- **Provider Implementations**: Stubbed `OpenAIProvider` and `AnthropicProvider` implementations conforming to the `AIProvider` interface.
- **Secure Key Management**: Created `APIKeyManager` and `SecureAPIKeyManager` interface for secure API key storage.
- **RENAME Support**: Added `RENAME` to `FileOperation` in `CodeChangeProposal.kt`. Updated `CodeChangeApplier.kt` to handle renames securely including rollback logic constraints. Updated `WorkspaceScreen.kt` to reflect RENAME operations dynamically in the UI.

### Changed
- Replaced the static map of `providers` in `WorkspaceViewModel` and `AppNavigation` with dependency injection of `AIFactory`.
- Extended `GeminiAIProvider` to accept dynamically injected API keys and model names, dropping the strict dependency on `BuildConfig.GEMINI_API_KEY`.
- Corrected Retrofit routing for Gemini API to inject model dynamically into the `@POST` path.
- Updated `AppDatabase.kt` to version 5 to include `AIProviderConfigEntity`.

"""

changelog = changelog.replace('## [Unreleased]', new_log)

with open('docs/CHANGELOG.md', 'w') as f:
    f.write(changelog)
