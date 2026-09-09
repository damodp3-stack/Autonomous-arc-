with open('docs/PROJECT_STATE.md', 'r') as f:
    state = f.read()

state = state.replace("GitHub Repository Clone (Phase 6 part 2) is complete.", "GitHub Repository Clone (Phase 6 part 2) is hardened and production-ready.")

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(state)

with open('docs/CHANGELOG.md', 'a') as f:
    f.write("\n## [0.6.2] - GitHub Clone Hardening\n- Hardened clone flow with atomic staging workspace replacement.\n- Handled GitHub `truncated=true` recursive tree limitations via DFS fallback.\n- Introduced binary vs text file classification saving images/apks correctly.\n- Implemented safety guardrails preventing massive clone exhaustion (size/count caps).\n- Validated remote path inputs, filtering duplication and preventing directory traversal attacks.\n")
