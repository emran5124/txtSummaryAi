package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.api.GeminiExecutionEngine
import com.example.data.api.LogManager
import com.example.data.database.AppDatabase
import com.example.data.database.SummaryTask
import com.example.data.repository.SummarizerRepository
import kotlinx.coroutines.*

class SummaryProcessingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var db: AppDatabase
    private lateinit var repository: SummarizerRepository
    private lateinit var executionEngine: GeminiExecutionEngine

    private var activeJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "SummaryProcessingChannel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_OR_RESUME = "START_OR_RESUME"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_USER_DECISION = "USER_DECISION"

        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_DECISION_ACTION = "EXTRA_DECISION_ACTION" // "RETRY_SAME", "NEXT_MODEL", "NEXT_KEY"
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(applicationContext)
        repository = SummarizerRepository(
            db.apiKeyDao(), db.modelDao(), db.promptDao(),
            db.summaryTaskDao(), db.taskChunkDao(), db.appSettingDao()
        )
        executionEngine = GeminiExecutionEngine(
            db.apiKeyDao(), db.modelDao(), db.summaryTaskDao(), db.appSettingDao()
        )

        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L

        if (taskId != -1L) {
            when (action) {
                ACTION_START_OR_RESUME -> {
                    startForeground(NOTIFICATION_ID, buildProgressNotification("در حال آماده‌سازی...", 0, 100))
                    startProcessing(taskId)
                }
                ACTION_PAUSE -> {
                    pauseProcessing(taskId)
                }
                ACTION_USER_DECISION -> {
                    val decision = intent.getStringExtra(EXTRA_DECISION_ACTION)
                    if (decision != null) {
                        applyDecisionAndResume(taskId, decision)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BookSummarizer::ProcessingWakeLock")
            wakeLock?.acquire(10 * 60 * 1000L) // Safe limit 10 minutes max per chunk session
            LogManager.log("قفل بیداری پردازنده فعال گردید (مقاومت در خواب سیستم)")
        } catch (e: Exception) {
            LogManager.log("خطا در کسب قفل بیداری: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                LogManager.log("قفل بیداری پردازنده آزاد شد")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProcessing(taskId: Long) {
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            try {
                val task = repository.getTaskSync(taskId)
                if (task == null) {
                    stopSelf()
                    return@launch
                }

                // Transition status to PROCESSING
                val runningTask = task.copy(status = "PROCESSING", errorMessage = null)
                repository.updateTask(runningTask)
                LogManager.log("آغاز پردازش کتاب الکترونیک: ${task.fileName}")

                val chunks = repository.getChunksForTaskSync(taskId)
                val total = task.totalChunks
                var startIdx = runningTask.currentChunkIndex

                // Fetch details of selected prompt and selected model
                val promptText = db.promptDao().getPromptByIdSync(task.selectedPromptId)?.promptText
                    ?: "لطفاً متن مربوطه را خلاصه نمایید."
                
                // Set engine position according to the actual model inside DB
                val selectedModel = db.modelDao().getModelById(task.selectedModelId)
                if (selectedModel != null) {
                    val allKeys = db.apiKeyDao().getAllApiKeysSync()
                    val keyIdx = maxOf(0, allKeys.indexOfFirst { it.id == selectedModel.apiKeyId })
                    val modelsInKey = db.modelDao().getModelsForKeySync(selectedModel.apiKeyId)
                    val modelIdx = maxOf(0, modelsInKey.indexOfFirst { it.id == selectedModel.id })
                    executionEngine.setState(keyIdx, modelIdx)
                } else {
                    executionEngine.resetState()
                }

                for (i in startIdx until total) {
                    // Check if run was cancelled or paused
                    val checkTask = repository.getTaskSync(taskId) ?: break
                    if (checkTask.status != "PROCESSING") {
                        LogManager.log("پردازش توسط کاربر یا تغییر حالت لغو گردید.")
                        break
                    }

                    updateNotificationProgress(checkTask.fileName, i, total)

                    val currentChunk = chunks[i]
                    val summaryResult = executionEngine.executeWithFallback(
                        task = checkTask,
                        chunkText = currentChunk.chunkText,
                        systemInstructionText = promptText,
                        onUpdateTaskState = { keyIdx, modelIdx, userDecisionRequired, err ->
                            // Dynamically update retry metrics & state status inside DB to withstand reboots
                            val currentTask = repository.getTaskSync(taskId)
                            if (currentTask != null) {
                                var updatedStatus = currentTask.status
                                if (userDecisionRequired != null) {
                                    updatedStatus = userDecisionRequired
                                }
                                val modelList = db.modelDao().getAllModelsSync()
                                val activeModelId = modelList.getOrNull(modelIdx)?.id ?: currentTask.selectedModelId

                                repository.updateTask(
                                    currentTask.copy(
                                        status = updatedStatus,
                                        errorMessage = err,
                                        selectedModelId = activeModelId,
                                        lastUpdateTime = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    )

                    if (summaryResult == null) {
                        // Returned null implies state moved to WAITING_FOR_VPN or WAITING_FOR_SERVER_RETRY
                        LogManager.log("عملیات تعلیق شد. در انتظار برطرف شدن مانع یا اقدام کاربر...")
                        stopSelf()
                        return@launch
                    }

                    // Success checkpoint! regular persistence of individual chunks
                    repository.updateChunk(currentChunk.copy(completedSummary = summaryResult))
                    
                    val nextChunkIndex = i + 1
                    val progressTask = repository.getTaskSync(taskId) ?: break
                    repository.updateTask(
                        progressTask.copy(
                            currentChunkIndex = nextChunkIndex,
                            lastUpdateTime = System.currentTimeMillis()
                        )
                    )
                    LogManager.log("بخش [${nextChunkIndex}/$total] با موفقیت پردازش و ذخیره شد.")
                }

                // Post-processing completion aggregation
                val lastCheck = repository.getTaskSync(taskId)
                if (lastCheck != null && lastCheck.currentChunkIndex >= total && lastCheck.status == "PROCESSING") {
                    LogManager.log("تمامی ابعاد کتاب تلخیص گردید. در حال سرهم بندی قالب فایل ها...")
                    repository.finishTaskAggregation(applicationContext, taskId)
                    LogManager.log("عملیات پایان یافت. خلاصه نهایی آماده است.")
                }

            } catch (e: Exception) {
                LogManager.log("وقفه در پردازش: ${e.localizedMessage}")
                val t = repository.getTaskSync(taskId)
                if (t != null) {
                    repository.updateTask(t.copy(status = "FAILED", errorMessage = e.localizedMessage))
                }
            } finally {
                stopSelf()
            }
        }
    }

    private fun pauseProcessing(taskId: Long) {
        serviceScope.launch {
            val task = repository.getTaskSync(taskId)
            if (task != null && (task.status == "PROCESSING" || task.status.startsWith("WAITING"))) {
                repository.updateTask(task.copy(status = "PAUSED"))
                LogManager.log("عملیات به حالت تعلیق درآمد.")
            }
            stopSelf()
        }
    }

    private fun applyDecisionAndResume(taskId: Long, decision: String) {
        serviceScope.launch {
            val task = repository.getTaskSync(taskId) ?: return@launch
            val allKeys = db.apiKeyDao().getAllApiKeysSync()
            val modelId = task.selectedModelId
            val model = db.modelDao().getModelById(modelId)
            val modelsCount = if (model != null) db.modelDao().getModelsForKeySync(model.apiKeyId).size else 1
            
            executionEngine.setUserSelectedAction(decision, allKeys.size, modelsCount)
            
            // Re-trigger Processing
            startProcessing(taskId)
        }
    }

    private fun updateNotificationProgress(fileName: String, i: Int, total: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = "بخش ${i + 1} از $total"
        notificationManager.notify(
            NOTIFICATION_ID,
            buildProgressNotification(fileName, i, total)
        )
    }

    private fun buildProgressNotification(fileName: String, current: Int, total: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("در حال تلخیص: $fileName")
            .setContentText("بخش ${current + 1} از $total")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(total, current, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "سرور تلخیص هوشمند کتاب",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        activeJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
