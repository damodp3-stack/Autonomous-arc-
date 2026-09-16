with open('docs/PROJECT_STATE.md', 'r') as f:
    content = f.read()

import re

# Insert into COMPLETED
completed_items = """- Autonomous execution engine.
- Bounded execution with configurable max iterations.
- State machine (IDLE, GENERATING, APPLYING, VERIFYING, CONTINUING, STOPPED, BLOCKED, COMPLETED).
- Coroutine-based structured cancellation.
- Rollback-aware execution loop.
- Execution history tracking per step.
"""

# replace `- True Autonomous Pipeline for iterative development.` with the items
content = content.replace("- True Autonomous Pipeline for iterative development.", completed_items)

# Add pending items
pending_items = """- Token/usage dashboard.
- Media Vault.
- Ideas Vault.
- Cloud synchronization.
- Advanced provider fallback.
- Production hardening.
"""

content = re.sub(r'\*\*Pending\*\*\n(.*?)\n\n', f"**Pending**\n{pending_items}\n", content, flags=re.DOTALL)

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(content)

