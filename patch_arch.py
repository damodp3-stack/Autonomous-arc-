with open('docs/ARCHITECTURE.md', 'r') as f:
    content = f.read()

arch = """
- Navigation managed via `androidx.navigation.compose`.
- GitHub Integration Foundation:
  - `GitHubConfigEntity` stored in Room, linked 1:1 with Projects.
  - `GitHubService` and `GitHubAuthService` implementations using Retrofit/Moshi.
  - User Flow: Project -> GitHubConfig -> GitHubService -> GitHub API.
"""

content = content.replace("- Navigation managed via `androidx.navigation.compose`.", arch.strip())

with open('docs/ARCHITECTURE.md', 'w') as f:
    f.write(content)

with open('docs/DECISIONS.md', 'a') as f:
    f.write("\n\n## Authentication Security\nTokens are stored locally using standard `SharedPreferences` (wrapped by `TokenManager`) rather than being persisted into Room or the main project database. No client secrets are hardcoded in the app for GitHub integration. We use Personal Access Tokens for initial implementation flexibility.\n")
