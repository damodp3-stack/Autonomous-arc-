# Decisions

Maintain important architectural and product decisions.

## Verified Decisions

- **Product name is Autonomous Arc.**
- **Mobile-first approach:** The UI is designed specifically for mobile screens utilizing the High Density theme.
- **GitHub is the source of truth for code and documentation.**
- **AI providers should be modular:** Demonstrated by the `AIProvider` interface to prevent vendor lock-in.
- **API keys must never be committed:** Credentials should be handled via the `.env` approach securely.
- **AI code changes should eventually use review/approval:** Emphasized in the roadmap for transparent automation.
- **Important changes should be reversible/versioned:** Reflected in the planned architectural phases.
- **Gemini API Integration:** Selected Direct REST (Option B) with Moshi for the prototyping phase to avoid Firebase backend setup overhead, isolating the logic in `GeminiAIProvider` and exposing a UI toggle to safely fall back to `MockAIProvider`.
- **Files & Code Editor:** Implemented as a drawer for mobile friendliness rather than cramming side-by-side files and editors. Project isolation is maintained firmly at the Room Database level via strict `projectId` foreign key/queries.

## Future Decisions

**Decision:** True Filesystem Engine for Project Storage
**Date:** 2026-09-08
**Reason:** Room DB is great for text, but actual code compilation (and future GitHub integration) requires physical files. We built `ProjectFileSystem` to mirror Room, synchronizing at startup and operating atomically during apply/rollback.
**Alternatives considered:** Relying purely on Room (impossible for standard toolchains). Using Scoped Storage (SAF) - too much overhead and user permission friction compared to app-private storage.
**Impact:** Required writing atomic `writeFile`, hardening path traversal protections, updating the Apply/Rollback Engine, and modifying `WorkspaceViewModel` to sync on open.

**Decision:** AI-generated code changes are represented as proposals and require a later explicit approval/apply stage rather than directly modifying project files.
**Date:** 2026-09-03
**Reason:** Ensures user control over modifications and provides a safe abstraction before any destructive actions are performed.
**Alternatives considered:** Direct automatic file modification (too risky, harder to revert).
**Impact:** Required introducing the `CodeChangeProposal` model and a distinct UI flow before applying changes.

*(Template for future decisions)*
**Decision:** [What was decided]
**Date:** [YYYY-MM-DD]
**Reason:** [Why this decision was made]
**Alternatives considered:** [What else was evaluated]
**Impact:** [How this affects the project]


## Authentication Security
Tokens are stored locally using standard `SharedPreferences` (wrapped by `TokenManager`) rather than being persisted into Room or the main project database. No client secrets are hardcoded in the app for GitHub integration. We use Personal Access Tokens for initial implementation flexibility.
