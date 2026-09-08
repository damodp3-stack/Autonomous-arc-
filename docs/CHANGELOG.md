# Changelog

The historical development record for Autonomous Arc.

## [Phase 4 - True Filesystem Workspace] - Current
- **Added:** Real Project Filesystem abstraction (`ProjectFileSystem`).
- **Added:** Secure project-private directories.
- **Updated:** Room ↔ Filesystem bi-directional synchronization.
- **Updated:** Apply engine and Rollback natively write to real files.
- **Updated:** Project migration logic on Workspace initialization.

## [Phase 3 - AI Coding Agent]
- **Added:** Diff Viewer and Human Approval Workflow.
- **Added:** Dedicated `ProposalState` enum to manage proposal lifecycle.
- **Added:** `Reject` and `Approve & Apply` safeguards.
- **Updated:** UI integration for reviewing Diff proposals directly in chat.
- **Added:** Safe Code Change Apply Engine (`CodeChangeApplier`).
- **Added:** Secure snapshot and rollback mechanisms for AI file modifications.
- **Added:** `ApplyResult` state models and explicit user Apply actions in the Workspace UI.
- **Added:** In-memory traversal and conflict validation layers preventing destructive AI file overwrites.
- **Fixed:** Editor close button logic which previously improperly deleted the selected file.
- **Added:** AI Code Change Proposal Engine.
- **Added:** `CodeChangeProposal` and `FileChange` models for structured AI output.
- **Added:** `proposeCodeChanges` functionality to `AIProvider` abstractions.
- **Added:** Basic AI Proposal Viewer dialog in the Workspace UI.
- **Added:** Path validation and traversal rejection for AI-generated code proposals.
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
