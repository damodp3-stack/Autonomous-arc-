package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IdeaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(idea: IdeaEntity)

    @Update
    suspend fun update(idea: IdeaEntity)

    @Delete
    suspend fun delete(idea: IdeaEntity)

    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM ideas ORDER BY updatedTimestamp DESC")
    fun getAllIdeas(): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE status = :status ORDER BY updatedTimestamp DESC")
    fun getIdeasByStatus(status: String): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun getIdeaById(id: String): IdeaEntity?

    @Query("SELECT * FROM ideas WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY updatedTimestamp DESC")
    fun searchIdeas(query: String): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE projectAssociation = :projectId ORDER BY updatedTimestamp DESC")
    fun getIdeasForProject(projectId: String): Flow<List<IdeaEntity>>

    @Query("SELECT COUNT(*) FROM ideas")
    suspend fun getIdeaCount(): Int
}
