package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.sync.SyncMetadataDao
import com.example.sync.SyncMetadataEntity

@Database(entities = [
    MessageEntity::class, 
    ProjectEntity::class, 
    ProjectFileEntity::class, 
    GitHubConfigEntity::class,
    AIProviderConfigEntity::class,
    UsageRecordEntity::class,
    MediaEntity::class,
    IdeaEntity::class,
    SyncMetadataEntity::class
], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectFileDao(): ProjectFileDao
    abstract fun githubConfigDao(): GitHubConfigDao
    abstract fun aiProviderConfigDao(): AIProviderConfigDao
    abstract fun usageDao(): UsageDao
    abstract fun mediaDao(): MediaDao
    abstract fun ideaDao(): IdeaDao
    abstract fun syncMetadataDao(): SyncMetadataDao

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
