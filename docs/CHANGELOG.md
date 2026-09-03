# Changelog

The historical development record for Autonomous Arc.

## [Phase 1 - Projects] - Current
- **Added:** Files & Code Workspace Foundation (Room-based project files, code editor, side drawer file explorer).
- **Added:** `ProjectFileEntity` and `ProjectFileDao` for isolated project file persistence.
- **Added:** UI for basic file operations: Create, Read, Update, Delete.
- **Added:** `ProjectContext` to prepare Gemini AI integration for file awareness.
- **Added:** Real Gemini AI Provider integration (`GeminiAIProvider`) using Retrofit and Moshi.
- **Added:** AI Provider selection dropdown in the Workspace TopAppBar (Mock vs. Gemini).
- **Added:** Secure API Key management via `BuildConfig` and `secrets-gradle-plugin`.
- **Added:** `docs/` directory for permanent project documentation.
- **Added:** Local persistence via Room Database (`AppDatabase`, `ProjectEntity`, `MessageEntity`).
- **Added:** Project List view allowing users to create, view, and delete isolated projects.
- **Added:** Workspace view for individual projects with isolated chat history.
- **Added:** Navigation graph (`AppNavigation`) to handle routing.
- **Added:** `AIProvider` interface and `MockAIProvider` abstraction.
- **Changed:** Restructured previous V1 codebase to support multiple isolated project workspaces.
- **Changed:** Migrated UI to "High Density" theme to improve layout efficiency on mobile.

## [Phase 0 - Foundation] - Initial
- **Added:** Basic Jetpack Compose app setup.
- **Added:** Single-screen prompt input and chat mock.
- **Added:** `metadata.json` and strings configuration.
