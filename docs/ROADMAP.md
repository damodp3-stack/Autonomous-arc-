# Roadmap

**Phase 0 — Foundation**
- [x] Completed: Basic app setup, High Density theme, MVVM structure.

**Phase 1 — Projects**
- [x] Completed: Multiple isolated projects, Room DB persistence, Project workspaces.

**Phase 2 — Files & Code Workspace**
- [x] Completed: Code editor, file navigation, file management within projects.

**Phase 3 — AI Coding Agent**
- [x] Completed: AI Provider abstraction created. Mock responses implemented. AI can now generate structured `CodeChangeProposal` items based on user requests and file context.
- [x] Completed: Safe Code Change Apply Engine. AI proposals can be correctly applied to the Room-based project files with full rollback and validation.
- [x] Completed: Diff Viewer & Human Approval Workflow. AI code proposals can be reviewed visually and explicitly approved or rejected by the user.

**Phase 4 — Real Filesystem Integration**
- [x] Completed: True Filesystem Sync. Room DB files securely mirrored to Android private app storage. All operations (Create, Edit, Delete, Rename, Apply, Rollback) execute safely on actual filesystem.

**Phase 4.5 — Media & Ideas**
- [ ] Pending: Ideas vault, AI-generated images/videos/media.

**Phase 5 — Multi-API System**
- [ ] Pending: API key management, token/usage tracking, multiple AI provider integrations.

**Phase 6 — GitHub Integration**
- [x] Completed: GitHub Integration Foundation (Authentication, Repository Discovery, Branch Selection, Project Connection, Isolation).
- [ ] Pending: Repo cloning, committing, pushing, syncing.

**Phase 7 — Build & Preview**
- [ ] Pending: APK/cloud builds, app preview testing.

**Phase 8 — Autonomous Agent**
- [ ] Pending: Full autonomous AI workflow (parse, propose, apply, test).
