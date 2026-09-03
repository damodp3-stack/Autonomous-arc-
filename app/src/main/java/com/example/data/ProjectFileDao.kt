package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectFileDao {
    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY path ASC")
    fun getFilesForProject(projectId: String): Flow<List<ProjectFileEntity>>

    @Query("SELECT * FROM project_files WHERE id = :fileId")
    suspend fun getFile(fileId: String): ProjectFileEntity?

    @Query("SELECT * FROM project_files WHERE projectId = :projectId AND path = :path")
    suspend fun getFileByPath(projectId: String, path: String): ProjectFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ProjectFileEntity)

    @Update
    suspend fun updateFile(file: ProjectFileEntity)

    @Query("DELETE FROM project_files WHERE id = :fileId")
    suspend fun deleteFile(fileId: String)

    @Query("DELETE FROM project_files WHERE projectId = :projectId")
    suspend fun clearFilesForProject(projectId: String)
}
