# Architecture

## Current Architecture
What actually exists in the repository now:
- Android/Kotlin/Jetpack Compose client.
- Room Database (Local Persistence) with `ProjectEntity` and `MessageEntity`.
- Repositories (`LocalProjectRepository`, `MessageRepository`).
- ViewModels managing state (`ProjectListViewModel`, `WorkspaceViewModel`).
- `MockAIProvider` implementing the `AIProvider` interface.
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
AI Orchestration (Planned)
↓
AI Provider Abstraction
↓
Multiple AI Providers (Planned)

**Project Structure Architecture:**
Project
├── Chats (Implemented)
├── Metadata (Implemented)
├── Files (Planned)
├── Media (Planned)
├── Ideas (Planned)
├── Builds (Planned)
└── Versions (Planned)

**Future AI Workflow:**
Prompt
→ Planning (Planned)
→ Project Context (Planned)
→ File Analysis (Planned)
→ Proposed Changes (Planned)
→ Diff (Planned)
→ User Approval (Planned)
→ Apply (Planned)
→ Test (Planned)
→ Build (Planned)
→ Version (Planned)
