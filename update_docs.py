with open('docs/PROJECT_STATE.md', 'r') as f:
    content = f.read()

import re

# Update COMPLETED section
completed_addition = """- Real structured autonomous planning with strict JSON parsing.
- Dependency-aware execution engine.
- Bounded retry limits per task (max 2 failures).
- Execution history with structured rollback support per task.
- Tests for task creation, iteration limits, loop boundaries.
"""

content = content.replace("- Bounded execution with configurable max iterations.", "- Bounded execution with configurable max iterations.\n" + completed_addition)

with open('docs/PROJECT_STATE.md', 'w') as f:
    f.write(content)
