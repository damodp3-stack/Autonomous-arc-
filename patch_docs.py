import re

with open('docs/ROADMAP.md', 'r') as f:
    content = f.read()

roadmap = content.replace("- [ ] Pending: Repo cloning, committing, syncing.", "- [x] Completed: GitHub Integration Foundation (Authentication, Repository Discovery, Branch Selection, Project Connection, Isolation).\n- [ ] Pending: Repo cloning, committing, pushing, syncing.")

with open('docs/ROADMAP.md', 'w') as f:
    f.write(roadmap)

with open('docs/PROJECT_STATE.md', 'r') as f:
    content = f.read()

state = content.replace("Phase 4 is complete.", "Phase 4 is complete. GitHub Integration Foundation (Phase 6 part 1) is complete.")
state = state.replace("Pending: GitHub Integration", "Pending: GitHub Sync/Commit")

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(state)

with open('docs/CHANGELOG.md', 'a') as f:
    f.write("\n## [0.6.0] - GitHub Connection Flow\n- Implemented real GitHub authentication and API client using Retrofit/Moshi.\n- Added repository and branch selection to GitHub dialog.\n- Implemented secure local state configuration (persisting connected project config).\n")
