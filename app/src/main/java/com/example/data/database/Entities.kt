package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val key: String,
    val displayOrder: Int
)

@Entity(
    tableName = "models",
    foreignKeys = [
        ForeignKey(
            entity = ApiKeyEntity::class,
            parentColumns = ["id"],
            childColumns = ["apiKeyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["apiKeyId"])]
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiKeyId: Long,
    val modelName: String,
    val displayName: String,
    val displayOrder: Int
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val promptText: String,
    val isDefault: Boolean = false
)

@Entity(tableName = "summary_tasks")
data class SummaryTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileSize: Long,
    val totalChunks: Int,
    val currentChunkIndex: Int,
    val status: String, // "IDLE", "PROCESSING", "PAUSED", "COMPLETED", "FAILED"
    val selectedPromptId: Long,
    val selectedModelId: Long,
    val errorMessage: String? = null,
    val finalSavedPathTxt: String? = null,
    val finalSavedPathHtml: String? = null,
    val completeSummaryText: String = "",
    val lastUpdateTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_chunks")
data class TaskChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val chunkIndex: Int,
    val chunkText: String,
    val completedSummary: String? = null
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
