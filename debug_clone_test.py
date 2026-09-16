import re

with open('app/src/test/java/com/example/github/GitHubCloneTest.kt', 'r') as f:
    content = f.read()

bad = """        assertTrue("Files should be created", files.isNotEmpty())"""
good = """        if (files.isEmpty()) {
            println("DISCOVERY STATE WAS " + vm.discoveryState.value)
        }
        assertTrue("Files should be created", files.isNotEmpty())"""

content = content.replace(bad, good)

with open('app/src/test/java/com/example/github/GitHubCloneTest.kt', 'w') as f:
    f.write(content)

