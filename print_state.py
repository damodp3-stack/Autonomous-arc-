with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "assertTrue(vm.pushState.value is GitHubPushState.NoChanges)",
    "val state = vm.pushState.value\n        if (state !is GitHubPushState.NoChanges) {\n            println(\"State was $state\")\n        }\n        assertTrue(state is GitHubPushState.NoChanges)"
)

with open('app/src/test/java/com/example/github/GitHubPushTest.kt', 'w') as f:
    f.write(content)
