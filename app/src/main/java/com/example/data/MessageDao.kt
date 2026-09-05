package com.example.data




import androidx.room.Dao



import androidx.room.Insert



import androidx.room.OnConflictStrategy



import androidx.room.Query


import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getMessagesForProject(projectId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE projectId = :projectId")
    suspend fun clearMessages(projectId: String)
}
