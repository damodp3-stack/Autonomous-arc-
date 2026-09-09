import re

with open('docs/PROJECT_STATE.md', 'r') as f:
    content = f.read()

content = content.replace(
    "- GitHub Integration (Completed Clone Flow).",
    "- GitHub Integration (Clone, Commit, Push, Conflict Detection)."
)
content = content.replace(
    "- **GitHub Clone Engine with True Failure Safety (Verified with extensive edge-case tests).**",
    "- **GitHub Clone Engine with True Failure Safety (Verified with extensive edge-case tests).**\n- **GitHub Commit & Push Engine (Verified with test coverage for binary support and ref update safety).**"
)

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(content)


with open('docs/CHANGELOG.md', 'r') as f:
    content2 = f.read()

changelog = """## [0.6.3] - GitHub Commit & Push Engine
- Implemented robust `detectChanges` logic computing true local vs. remote SHAs (blob size + content matching).
- Added binary file support via base64 for Blob APIs and `calculateGitSha`.
- Added branch conflict validation protecting against remote-changed overwrites (`lastRemoteSha`).
- Introduced explicit `CommitSummary` and `FileChange` model displaying modifications, additions, and deletions for human review.
- Added GitHub Tree and Commit creation endpoints.
- Added strict empty commit message and empty change handling.
- Verified via 11-test suite covering simultaneous changes, binary edits, error fallbacks, and branch integrity.

"""

content2 = content2.replace("## [0.6.2]", changelog + "## [0.6.2]")

with open('docs/CHANGELOG.md', 'w') as f:
    f.write(content2)

