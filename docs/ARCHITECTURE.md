# Architecture

## Current Architecture
What actually exists in the repository now:
- Android/Kotlin/Jetpack Compose client.
- Room Database (Local Persistence) with `ProjectEntity`, `MessageEntity`, and `ProjectFileEntity`.
- Repositories (`LocalProjectRepository`, `MessageRepository`, `ProjectFileRepository`).
- ViewModels managing state (`ProjectListViewModel`, `WorkspaceViewModel`).
- File & Code Workspace UI with side-drawer file explorer and basic editor.
- Interface-driven AI abstraction (`AIProvider` and `ProjectContext`) with concrete implementations:
  - `MockAIProvider` (Offline/Testing)
  - `GeminiAIProvider` (Direct REST API integration using Retrofit and Moshi)
- Navigation managed via `androidx.navigation.compose`.

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
ProjectFileRepository (Room DB)

**Project Structure Architecture:**
Project
├── Chats (Implemented)
├── Metadata (Implemented)
├── Files (Implemented)
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
