# Architecture

## Current Architecture
What actually exists in the repository now:
- Android/Kotlin/Jetpack Compose client.
- Room Database (Local Persistence) with `ProjectEntity`, `MessageEntity`, and `ProjectFileEntity`.
- Android App-Private Filesystem for real, on-disk file storage (`ProjectFileSystem`).
- Repositories (`LocalProjectRepository`, `MessageRepository`, `ProjectFileRepository`).
- ViewModels managing state (`ProjectListViewModel`, `WorkspaceViewModel`).
- File & Code Workspace UI with side-drawer file explorer and basic editor.
- Interface-driven AI abstraction (`AIProvider` and `ProjectContext`) with concrete implementations:
  - `MockAIProvider` (Offline/Testing)
  - `GeminiAIProvider` (Direct REST API integration using Retrofit and Moshi)
- Navigation managed via `androidx.navigation.compose`.
- GitHub Integration Foundation:
  - `GitHubConfigEntity` stored in Room, linked 1:1 with Projects.
  - `GitHubService` and `GitHubAuthService` implementations using Retrofit/Moshi.
  - User Flow: Project -> GitHubConfig -> GitHubService -> GitHub API.

## Target Architecture
What the system is intended to become.

**Conceptual Architecture:**
Mobile App
↓
UI / Workspace
↓
Application State
↓
ProjectContext
↓
AIProvider
↓
CodeChangeProposal
↓
Proposal Viewer
↓
CodeChangeApplier
↓
ProjectFileRepository (Room DB + ProjectFileSystem)

**Project Structure Architecture:**
Project
├── Chats (Implemented)
├── Metadata (Implemented)
├── Files (Implemented in Room + Real Filesystem)
├── Media (Planned)
├── Ideas (Planned)
├── Builds (Planned)
└── Versions (Planned)

**Future AI Workflow:**
Prompt
→ Planning (Planned)
→ Project Context (Planned)
→ File Analysis (Planned)
→ Proposed Changes (Implemented)
→ Diff (Implemented)
→ User Approval (Implemented)
→ Apply (Implemented)
→ Test (Planned)
→ Build (Planned)
→ Version (Planned)
