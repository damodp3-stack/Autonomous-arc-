import re

with open('docs/ROADMAP.md', 'r') as f:
    content = f.read()

roadmap = content.replace("- [ ] Pending: Repo cloning, committing, pushing, syncing.", "- [x] Completed: GitHub Repository Clone (clones directly into real Android local filesystem with true file conflict safety).\n- [ ] Pending: Git committing, pushing, and background syncing.")

with open('docs/ROADMAP.md', 'w') as f:
    f.write(roadmap)

with open('docs/PROJECT_STATE.md', 'r') as f:
    content = f.read()

state = content.replace("Pending: GitHub Sync/Commit", "Pending: Git Commit/Push/Pull functionality")
state = state.replace("GitHub Integration Foundation (Phase 6 part 1) is complete.", "GitHub Repository Clone (Phase 6 part 2) is complete.")

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(state)

with open('docs/CHANGELOG.md', 'a') as f:
    f.write("\n## [0.6.1] - GitHub Repository Clone (File Sync)\n- Implemented full remote repository cloning directly to the local Android app-private filesystem.\n- Cloned trees retain original directory structures.\n- Safe base64 blob translation.\n- Configured UI flow for detecting and resolving existing-file conflicts via strict user confirmation.\n")
