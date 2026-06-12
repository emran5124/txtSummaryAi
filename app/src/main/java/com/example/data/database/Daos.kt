package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY displayOrder ASC")
    fun getAllApiKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY displayOrder ASC")
    suspend fun getAllApiKeysSync(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE id = :id")
    suspend fun getApiKeyById(id: Long): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(key: ApiKeyEntity): Long

    @Update
    suspend fun updateApiKey(key: ApiKeyEntity)

    @Delete
    suspend fun deleteApiKey(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteApiKeyById(id: Long)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY displayOrder ASC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE apiKeyId = :apiKeyId ORDER BY displayOrder ASC")
    fun getModelsForKey(apiKeyId: Long): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE apiKeyId = :apiKeyId ORDER BY displayOrder ASC")
    suspend fun getModelsForKeySync(apiKeyId: Long): List<ModelEntity>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModelById(id: Long): ModelEntity?

    @Query("SELECT * FROM models")
    suspend fun getAllModelsSync(): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity): Long

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Delete
    suspend fun deleteModel(model: ModelEntity)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteModelById(id: Long)
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY id ASC")
    fun getAllPrompts(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getPromptByIdSync(id: Long): PromptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptEntity): Long

    @Update
    suspend fun updatePrompt(prompt: PromptEntity)

    @Delete
    suspend fun deletePrompt(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun deletePromptById(id: Long)
}

@Dao
interface SummaryTaskDao {
    @Query("SELECT * FROM summary_tasks WHERE id = :id")
    fun getTaskFlow(id: Long): Flow<SummaryTask?>

    @Query("SELECT * FROM summary_tasks WHERE id = :id")
    suspend fun getTaskSync(id: Long): SummaryTask?

    @Query("SELECT * FROM summary_tasks ORDER BY lastUpdateTime DESC")
    fun getAllTasksFlow(): Flow<List<SummaryTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTask(task: SummaryTask): Long

    @Query("DELETE FROM summary_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)
}

@Dao
interface TaskChunkDao {
    @Query("SELECT * FROM task_chunks WHERE taskId = :taskId ORDER BY chunkIndex ASC")
    fun getChunksForTask(taskId: Long): Flow<List<TaskChunk>>

    @Query("SELECT * FROM task_chunks WHERE taskId = :taskId ORDER BY chunkIndex ASC")
    suspend fun getChunksForTaskSync(taskId: Long): List<TaskChunk>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<TaskChunk>)

    @Update
    suspend fun updateChunk(chunk: TaskChunk)

    @Query("DELETE FROM task_chunks WHERE taskId = :taskId")
    suspend fun clearChunksForTask(taskId: Long)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE key = :key")
    suspend fun getSetting(key: String): AppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettings(): List<AppSetting>
}
