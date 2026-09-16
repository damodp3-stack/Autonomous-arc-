package com.example.data



import kotlinx.coroutines.flow.Flow

open class MessageRepository(private val messageDao: MessageDao) {
    open fun getMessagesForProject(projectId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForProject(projectId)

    open suspend fun insert(message: MessageEntity) = messageDao.insertMessage(message)

    suspend fun clearMessagesForProject(projectId: String) = messageDao.clearMessages(projectId)
}
