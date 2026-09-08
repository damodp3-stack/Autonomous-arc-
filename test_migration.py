with open('app/src/main/java/com/example/ui/WorkspaceViewModel.kt', 'r') as f:
    lines = f.readlines()
    
found = False
for i, line in enumerate(lines):
    if "init {" in line:
        print("Init found at line", i)
        found = True
        break
