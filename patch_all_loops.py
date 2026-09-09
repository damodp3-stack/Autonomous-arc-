with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

import re

# Replace `vm.detectChanges()\n        ShadowLooper.idleMainLooper()\n` and any following delay
content = re.sub(
    r'vm\.detectChanges\(\)\n\s+ShadowLooper\.idleMainLooper\(\)\n(\s+kotlinx\.coroutines\.delay\(10\)\n)?',
    'vm.detectChanges()\n        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }\n',
    content
)

content = re.sub(
    r'vm\.commitAndPush\(.*?\)\n\s+ShadowLooper\.idleMainLooper\(\)\n(\s+kotlinx\.coroutines\.delay\(10\)\n)?',
    lambda m: m.group(0).split('\n')[0] + '\n        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }\n',
    content
)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)

