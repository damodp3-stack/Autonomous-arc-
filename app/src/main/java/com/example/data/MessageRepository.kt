package com.example.data

import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {
    fun getMessagesForProject(projectId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForProject(projectId)

    suspend fun insert(message: MessageEntity) = messageDao.insertMessage(message)

    suspend fun clearMessagesForProject(projectId: String) = messageDao.clearMessages(projectId)
}
