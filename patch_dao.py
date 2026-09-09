with open('app/src/main/java/com/example/data/ProjectFileDao.kt', 'r') as f:
    content = f.read()

replacement = """    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ProjectFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<ProjectFileEntity>)"""

content = content.replace("    @Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun insertFile(file: ProjectFileEntity)", replacement)

with open('app/src/main/java/com/example/data/ProjectFileDao.kt', 'w') as f:
    f.write(content)
