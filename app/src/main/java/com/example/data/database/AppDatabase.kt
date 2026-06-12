package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ApiKeyEntity::class,
        ModelEntity::class,
        PromptEntity::class,
        SummaryTask::class,
        TaskChunk::class,
        AppSetting::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun modelDao(): ModelDao
    abstract fun promptDao(): PromptDao
    abstract fun summaryTaskDao(): SummaryTaskDao
    abstract fun taskChunkDao(): TaskChunkDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "book_summarizer_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
