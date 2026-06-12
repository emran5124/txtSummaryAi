package com.example.data.repository

import android.content.Context
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.database.*
import com.example.util.BookTextPipeline
import com.example.util.FileHelper
import com.example.util.HtmlExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SummarizerRepository(
    private val apiKeyDao: ApiKeyDao,
    private val modelDao: ModelDao,
    private val promptDao: PromptDao,
    private val summaryTaskDao: SummaryTaskDao,
    private val taskChunkDao: TaskChunkDao,
    private val appSettingDao: AppSettingDao
) {
    val allApiKeys: Flow<List<ApiKeyEntity>> = apiKeyDao.getAllApiKeys()
    val allPrompts: Flow<List<PromptEntity>> = promptDao.getAllPrompts()
    val allModels: Flow<List<ModelEntity>> = modelDao.getAllModels()
    val allTasks: Flow<List<SummaryTask>> = summaryTaskDao.getAllTasksFlow()

    fun getTaskFlow(id: Long): Flow<SummaryTask?> = summaryTaskDao.getTaskFlow(id)

    fun getModelsForKey(apiKeyId: Long): Flow<List<ModelEntity>> {
        return modelDao.getModelsForKey(apiKeyId)
    }

    suspend fun getModelsForKeySync(apiKeyId: Long): List<ModelEntity> {
        return modelDao.getModelsForKeySync(apiKeyId)
    }

    suspend fun getSetting(key: String): AppSetting? = appSettingDao.getSetting(key)
    suspend fun insertSetting(key: String, value: String) {
        appSettingDao.insertSetting(AppSetting(key, value))
    }

    suspend fun ensureSeedsInstalled(fallbackKey: String) = withContext(Dispatchers.IO) {
        val existingKeys = apiKeyDao.getAllApiKeysSync()
        if (existingKeys.isEmpty()) {
            val defaultKeyId = apiKeyDao.insertApiKey(
                ApiKeyEntity(
                    name = "کلید اصلی برنامه‌نویس",
                    key = fallbackKey,
                    displayOrder = 0
                )
            )

            // Install default Models
            modelDao.insertModel(
                ModelEntity(
                    apiKeyId = defaultKeyId,
                    modelName = "gemini-3.5-flash",
                    displayName = "جمینای ۳.۵ فلش",
                    displayOrder = 0
                )
            )
            modelDao.insertModel(
                ModelEntity(
                    apiKeyId = defaultKeyId,
                    modelName = "gemini-3.1-pro-preview",
                    displayName = "جمینای ۳.۱ پرو",
                    displayOrder = 1
                )
            )
            modelDao.insertModel(
                ModelEntity(
                    apiKeyId = defaultKeyId,
                    modelName = "gemini-flash-latest",
                    displayName = "جمینای فلش آخرین نسخه",
                    displayOrder = 2
                )
            )
        }

        // Check and Seed exactly two default prompts
        val existingPromptsFlow = promptDao.getPromptByIdSync(1)
        if (existingPromptsFlow == null) {
            promptDao.insertPrompt(
                PromptEntity(
                    id = 1,
                    title = "خلاصه عمومی متنی (طرح پیش‌فرض)",
                    promptText = """
1. Language & Style
   - فقط به فارسی جواب بده. کوتاه، ساده، ساختارمند، و ADHD-friendly.
   - خیلی مهمه که چیزی از قلم نیوفته


3. Tone
   - محکم، واضح، Gen Z-friendly، صادق، بدون حرف اضافه.
   - دلایل و استدلالات و مثال حتما ذکر بشن

4. ساختار خروجی — برای هر:
🚩[موضوع]🚩
محتوا

- only 🚩[موضوع]🚩 not 🚩[*موضوع*]🚩 and not 🚩*[موضوع]*🚩 or any style else.

5. روش خلاصهنویسی — مهمترین بخش
   - هدف: همون محتوا، بیان فشردهتر — نه حذف اطلاعات، فقط کوتاهتر گفتن.
   - حجم خروجی باید ۲۰ تا ۳۰ درصد متن اصلی باشه.
   - جملات رو بازنویسی کن.
   - از ساختار bullet point یا شمارهگذاری برای وضوح استفاده کن.
   - هیچوقت ننویس "در این بخش" یا "نویسنده میگوید".
   - هیچ نکتهای از شرح نباید از قلم بیوفته.

فقط خروجی ساختاریافته رو برگردون، بدون توضیح اضافه.
Text Color Styles:

[text] green
¥¥text¥¥ red
«text» yellow
<text> blue

Highlight Styles:

[[text]] green
((text)) red
««text»» yellow
<<text>> blue

Nested syntax must work correctly.

Examples:

**<<text>>**
<<**text**>>
<<[[text]]>>
[hello ((world)) text]

but useing emojis is in priority.
                    """.trimIndent(),
                    isDefault = true
                )
            )

            promptDao.insertPrompt(
                PromptEntity(
                    id = 2,
                    title = "ترجمه وفادار به زبان فارسی",
                    promptText = """
Purpose:
Produce a faithful, natural, conceptually accurate Persian translation.

Requirements:

* Translate into clear Persian.
* Preserve meaning, intent, reasoning, and nuance.
* Avoid literal word-for-word translation when it harms clarity.
* Preserve technical terminology accurately.
* Preserve structure where appropriate.
* Use natural Persian wording.
* Do not add information.
* Do not remove information.
* Do not summarize.
* Do not explain.
* Do not interpret beyond the source text.
* Output only the final Persian translation.

Emoji usage is allowed when the source structure or readability benefits from it.
                    """.trimIndent(),
                    isDefault = true
                )
            )
        }

        // Initialize settings if empty
        if (appSettingDao.getSetting("retry_limit") == null) {
            appSettingDao.insertSetting(AppSetting("retry_limit", "5"))
        }
        if (appSettingDao.getSetting("retry_delay_sec") == null) {
            appSettingDao.insertSetting(AppSetting("retry_delay_sec", "30"))
        }
    }

    suspend fun createNewSession(
        fileName: String,
        fileContent: String,
        selectedPromptId: Long,
        selectedModelId: Long
    ): Long = withContext(Dispatchers.IO) {
        // Run deterministic chunking pipeline
        val chunks = BookTextPipeline.buildDeterministicChunks(fileContent)

        val newTask = SummaryTask(
            fileName = fileName,
            fileSize = fileContent.length.toLong(),
            totalChunks = chunks.size,
            currentChunkIndex = 0,
            status = "IDLE",
            selectedPromptId = selectedPromptId,
            selectedModelId = selectedModelId,
            errorMessage = null,
            finalSavedPathTxt = null,
            finalSavedPathHtml = null,
            completeSummaryText = ""
        )

        // Insert task to return newly auto-generated database Row ID
        val generatedTaskId = summaryTaskDao.insertOrUpdateTask(newTask)

        // Insert database chunks corresponding to the session taskId
        val chunkEntities = chunks.mapIndexed { index, chunkText ->
            TaskChunk(
                taskId = generatedTaskId,
                chunkIndex = index,
                chunkText = chunkText,
                completedSummary = null
            )
        }
        taskChunkDao.insertChunks(chunkEntities)

        return@withContext generatedTaskId
    }

    suspend fun insertApiKey(key: ApiKeyEntity) = apiKeyDao.insertApiKey(key)
    suspend fun updateApiKey(key: ApiKeyEntity) = apiKeyDao.updateApiKey(key)
    suspend fun deleteApiKeyById(id: Long) = apiKeyDao.deleteApiKeyById(id)

    suspend fun insertModel(model: ModelEntity) = modelDao.insertModel(model)
    suspend fun updateModel(model: ModelEntity) = modelDao.updateModel(model)
    suspend fun deleteModelById(id: Long) = modelDao.deleteModelById(id)

    suspend fun insertPrompt(prompt: PromptEntity) = promptDao.insertPrompt(prompt)
    suspend fun updatePrompt(prompt: PromptEntity) = promptDao.updatePrompt(prompt)
    suspend fun deletePromptById(id: Long) = withContext(Dispatchers.IO) {
        val prompt = promptDao.getPromptByIdSync(id)
        if (prompt != null && !prompt.isDefault) {
            promptDao.deletePrompt(prompt)
        }
    }

    suspend fun updateKeysOrdering(orderedKeys: List<ApiKeyEntity>) = withContext(Dispatchers.IO) {
        orderedKeys.forEachIndexed { index, apiKey ->
            apiKeyDao.updateApiKey(apiKey.copy(displayOrder = index))
        }
    }

    suspend fun updateModelsOrdering(orderedModels: List<ModelEntity>) = withContext(Dispatchers.IO) {
        orderedModels.forEachIndexed { index, model ->
            modelDao.updateModel(model.copy(displayOrder = index))
        }
    }

    suspend fun getTaskSync(id: Long): SummaryTask? = withContext(Dispatchers.IO) {
        summaryTaskDao.getTaskSync(id)
    }

    suspend fun getChunksForTaskSync(id: Long): List<TaskChunk> = withContext(Dispatchers.IO) {
        taskChunkDao.getChunksForTaskSync(id)
    }

    suspend fun updateTask(task: SummaryTask) = withContext(Dispatchers.IO) {
        summaryTaskDao.insertOrUpdateTask(task)
    }

    suspend fun updateChunk(chunk: TaskChunk) = withContext(Dispatchers.IO) {
        taskChunkDao.updateChunk(chunk)
    }

    suspend fun deleteTaskSession(id: Long) = withContext(Dispatchers.IO) {
        summaryTaskDao.deleteTask(id)
        taskChunkDao.clearChunksForTask(id)
    }

    suspend fun finishTaskAggregation(context: Context, taskId: Long) = withContext(Dispatchers.IO) {
        val task = summaryTaskDao.getTaskSync(taskId) ?: return@withContext
        val chunks = taskChunkDao.getChunksForTaskSync(taskId)
        val combinedSummary = chunks.map { it.completedSummary ?: "" }.filter { it.isNotEmpty() }.joinToString("\n\n")

        // Save formats securely
        val baseName = task.fileName.substringBeforeLast(".")
        val (txtPath, _) = FileHelper.saveSummaryToDownloads(context, baseName, combinedSummary)
        
        // Generate high-fidelity SPA Html Reader
        val htmlContent = HtmlExporter.generateReaderHtml(baseName, combinedSummary)
        val htmlPath = saveHtmlViaMediaStore(context, "${baseName}_Summary.html", htmlContent)

        val completedTask = task.copy(
            status = "COMPLETED",
            completeSummaryText = combinedSummary,
            finalSavedPathTxt = txtPath,
            finalSavedPathHtml = htmlPath,
            lastUpdateTime = System.currentTimeMillis()
        )
        summaryTaskDao.insertOrUpdateTask(completedTask)
    }

    private fun saveHtmlViaMediaStore(context: Context, displayName: String, content: String): String? {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/html")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/Summaries")
            }
        }
        val tableUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val summariesDir = java.io.File(downloadsDir, "Summaries")
            if (!summariesDir.exists()) summariesDir.mkdirs()
            val file = java.io.File(summariesDir, displayName)
            java.io.FileOutputStream(file).use { os ->
                os.write(content.toByteArray())
            }
            return android.net.Uri.fromFile(file).toString()
        }
        try {
            val fileUri = resolver.insert(tableUri, contentValues) ?: return null
            resolver.openOutputStream(fileUri)?.use { os ->
                os.write(content.toByteArray())
            }
            return fileUri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
