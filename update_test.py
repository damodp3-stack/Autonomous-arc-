with open('app/src/test/java/com/example/ai/CodeChangeApplierTest.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "import com.example.data.ProjectFileRepository",
    "import com.example.data.ProjectFileRepository\nimport com.example.data.ProjectFileSystem"
)
    
setup_old = """        dao = db.projectFileDao()
        repository = ProjectFileRepository(dao)
        applier = CodeChangeApplier(repository)"""
        
setup_new = """        dao = db.projectFileDao()
        val fileSystem = ProjectFileSystem(context)
        repository = ProjectFileRepository(dao, fileSystem)
        applier = CodeChangeApplier(repository)"""

content = content.replace(setup_old, setup_new)

with open('app/src/test/java/com/example/ai/CodeChangeApplierTest.kt', 'w') as f:
    f.write(content)
