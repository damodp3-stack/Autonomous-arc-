package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [
    MessageEntity::class, 
    ProjectEntity::class, 
    ProjectFileEntity::class, 
    GitHubConfigEntity::class,
    AIProviderConfigEntity::class
], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectFileDao(): ProjectFileDao
    abstract fun githubConfigDao(): GitHubConfigDao
    abstract fun aiProviderConfigDao(): AIProviderConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aicoder_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
