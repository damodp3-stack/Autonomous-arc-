import re

def update_file(filepath, replacement_map):
    try:
        with open(filepath, 'r') as f:
            content = f.read()
            
        for k, v in replacement_map.items():
            content = content.replace(k, v)
            
        with open(filepath, 'w') as f:
            f.write(content)
    except FileNotFoundError:
        pass

# PROJECT_STATE.md
update_file('docs/PROJECT_STATE.md', {
    '- [ ] Phase 3.1: Harden safe apply engine (security, normalization, UI state)': '- [x] Phase 3.1: Harden safe apply engine (security, normalization, UI state)',
    '- [ ] Phase 3.2: Diff viewer and manual approval workflow': '- [x] Phase 3.2: Diff viewer and manual approval workflow',
    'Current focus: Implement the safe code change apply engine (Phase 3).': 'Current focus: Moving to Phase 4 (Build & Run Engine) after completing Phase 3 (Safe Code Apply & Diff Review).'
})

# ROADMAP.md
update_file('docs/ROADMAP.md', {
    '- [ ] Phase 3.1: Harden safe apply engine (security, normalization, validation)': '- [x] Phase 3.1: Harden safe apply engine (security, normalization, validation)',
    '- [ ] Phase 3.2: Diff viewer and manual approval workflow': '- [x] Phase 3.2: Diff viewer and manual approval workflow'
})

# CHANGELOG.md
changelog_add = """## [Unreleased]
### Added
- Diff Viewer screen for human review of AI code proposals
- ProposalState unified enum for tracking generation, review, and application
- Approve and Reject buttons with safe rollback
- Path normalization and validation in CodeChangeApplier

"""
with open('docs/CHANGELOG.md', 'r') as f:
    content = f.read()
with open('docs/CHANGELOG.md', 'w') as f:
    f.write(content.replace('## [Unreleased]', changelog_add))

