import re

with open('docs/PROJECT_STATE.md', 'r') as f:
    content = f.read()

content = content.replace(
    '- Implement True Filesystem Sync (Phase 4). Currently files exist in Room DB; they need to be written to a real file system or in-memory file system for compilation.',
    '- **True Filesystem Workspace completed.** (Phase 4) Project files are now securely synchronized and managed across both the Room database and the Android app-private filesystem. The AI Apply Engine and rollback mechanism operate atomically on real files.'
)

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(content)

with open('docs/ROADMAP.md', 'r') as f:
    content = f.read()

content = content.replace(
    '- [ ] Pending: Diff Viewer for more granular approval before applying.',
    '- [x] Completed: Diff Viewer & Human Approval Workflow.'
)

content = content.replace(
    '**Phase 4 — Media & Ideas**',
    '**Phase 4 — Real Filesystem Integration**\n- [x] Completed: True Filesystem Sync. Room DB files securely mirrored to Android private app storage. All operations (Create, Edit, Delete, Rename, Apply, Rollback) execute safely on actual filesystem.\n\n**Phase 4.5 — Media & Ideas**'
)

with open('docs/ROADMAP.md', 'w') as f:
    f.write(content)

with open('docs/CHANGELOG.md', 'r') as f:
    content = f.read()

content = content.replace(
    '## [Phase 3 - AI Coding Agent] - Current',
    '## [Phase 4 - True Filesystem Workspace] - Current\n- **Added:** Real Project Filesystem abstraction (`ProjectFileSystem`).\n- **Added:** Secure project-private directories.\n- **Updated:** Room ↔ Filesystem bi-directional synchronization.\n- **Updated:** Apply engine and Rollback natively write to real files.\n- **Updated:** Project migration logic on Workspace initialization.\n\n## [Phase 3 - AI Coding Agent]'
)

with open('docs/CHANGELOG.md', 'w') as f:
    f.write(content)

