package com.example.data.api

import android.content.Context
import com.example.data.database.*
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

class GeminiExecutionEngine(
    private val apiKeyDao: ApiKeyDao,
    private val modelDao: ModelDao,
    private val summaryTaskDao: SummaryTaskDao,
    private val appSettingDao: AppSettingDao
) {

    // Cache or state variables to track current indices
    private var currentKeyIndex = 0
    private var currentModelIndex = 0

    // Configuration values (fallback defaults if database has no records)
    private var maxRetries = 5
    private var retryDelayMs = 30000L // 30 seconds

    suspend fun loadSettings() {
        val maxRetrySetting = appSettingDao.getSetting("retry_limit")
        val delaySetting = appSettingDao.getSetting("retry_delay_sec")
        
        maxRetries = maxRetrySetting?.value?.toIntOrNull() ?: 5
        retryDelayMs = (delaySetting?.value?.toIntOrNull() ?: 30).toLong() * 1000L
        
        LogManager.log("تنظیمات موتور بارگیری شد: تکرار=$maxRetries، تاخیر=${retryDelayMs/1000} ثانیه")
    }

    /**
     * Executes content generation for a specific chunk, observing the fallback tree.
     * If recovery states are hit, suspends/mutates database and returns null to indicate paused/waiting state.
     */
    suspend fun executeWithFallback(
        task: SummaryTask,
        chunkText: String,
        systemInstructionText: String,
        onUpdateTaskState: suspend (keyIndex: Int, modelIndex: Int, userDecisionRequired: String?, errorMessage: String?) -> Unit
    ): String? {
        loadSettings()

        // Fetch keys and models
        val allKeys = apiKeyDao.getAllApiKeysSync()
        if (allKeys.isEmpty()) {
            val error = "هیچ کلید API تعریف نشده است. لطفاً ابتدا در بخش تنظیمات کلید اضافه کنید."
            LogManager.log(error)
            onUpdateTaskState(0, 0, null, error)
            return null
        }

        // Keep current keys and models in order
        var keyChanged = false

        while (currentKeyIndex < allKeys.size) {
            val activeKey = allKeys[currentKeyIndex]
            val modelsForActiveKey = modelDao.getModelsForKeySync(activeKey.id)

            if (modelsForActiveKey.isEmpty()) {
                LogManager.log("کلید '${activeKey.name}' مدل فعالی ندارد. حرکت به کلید بعدی...")
                currentKeyIndex++
                currentModelIndex = 0
                keyChanged = true
                continue
            }

            while (currentModelIndex < modelsForActiveKey.size) {
                val activeModel = modelsForActiveKey[currentModelIndex]
                
                LogManager.log("تلاش جهت پردازش با کلید [${currentKeyIndex + 1}/${allKeys.size}]: ${activeKey.name} | مدل: ${activeModel.modelName}")
                
                var attempt = 1
                while (attempt <= maxRetries) {
                    try {
                        // Assemble request matching limit targets: Max Out of 60000, Temperature 0.3
                        val request = GenerateContentRequest(
                            contents = listOf(Content(parts = listOf(Part(text = chunkText)))),
                            generationConfig = GenerationConfig(
                                temperature = 0.3f,
                                maxOutputTokens = 60000
                            ),
                            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
                        )

                        // Update current state markers so we resume from the EXACT same point
                        onUpdateTaskState(currentKeyIndex, currentModelIndex, null, null)

                        // REST direct call
                        val response = RetrofitClient.service.generateContent(
                            activeModel.modelName,
                            activeKey.key,
                            request
                        )

                        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (responseText != null) {
                            LogManager.log("پاسخ کلاود با موفقیت دریافت گردید.")
                            return responseText
                        } else {
                            throw Exception("پاسخ دریافتی خالی بود")
                        }

                    } catch (e: Exception) {
                        val errorCode = getErrorCode(e)
                        LogManager.log("خطای مدل رخ داد! کد خطا: $errorCode | جزئیات: ${e.message}")

                        when (errorCode) {
                            429 -> {
                                // Rate limit reached. Invalidate and fallback to NEXT enabled model or key.
                                LogManager.log("محدودیت نرخ درخواست (429) رخ داد. سویچ به گزینه بعدی...")
                                currentModelIndex++
                                if (currentModelIndex >= modelsForActiveKey.size) {
                                    currentKeyIndex++
                                    currentModelIndex = 0
                                }
                                break // breaks inner retry attempt, moves to outer key/model loops
                            }

                            403, -403 -> {
                                // Auth/Regional Failure. Requires VPN check.
                                val message = "خطای ۴۰۳ یا عدم دسترسی منطقه‌ای رخ داد. لطفاً ابزار تغییر آی‌پی (VPN) خود را روشن کرده و مجدداً روی دکمه «ادامه» ضربه بزنید."
                                LogManager.log(message)
                                onUpdateTaskState(currentKeyIndex, currentModelIndex, "WAITING_FOR_VPN", message)
                                return null
                            }

                            503, 500, 502, 504 -> {
                                // Transient Server failure
                                if (attempt < maxRetries) {
                                    LogManager.log("خطای موقت سرور ($errorCode). تلاش مجدد [$attempt/$maxRetries] در ${retryDelayMs / 1000} ثانیه دیگر...")
                                    delay(retryDelayMs)
                                    attempt++
                                } else {
                                    // Exhausted retries. Ask the user.
                                    val message = "خطای تکرارشونده سرور جمینای رخ داد. لطفاً تصمیم بگیرید که آیا مایل به تلاش مجدد روی همین مدل هستید یا مایلید به مدل/کلید بعدی تغییر مسیر دهید؟"
                                    LogManager.log(message)
                                    onUpdateTaskState(currentKeyIndex, currentModelIndex, "WAITING_FOR_SERVER_RETRY", message)
                                    return null
                                }
                            }

                            else -> {
                                // Socket timeouts/Internet Connection errors represent regional/VPN issues
                                if (e is IOException) {
                                    val message = "برقراری ارتباط با وب‌سرویس صورت نپذیرفت. از صحت اتصال اینترنت یا روشن بودن VPN خود اطمینان حاصل کرده و دکمه «ادامه» را بفشارید."
                                    LogManager.log(message)
                                    onUpdateTaskState(currentKeyIndex, currentModelIndex, "WAITING_FOR_VPN", message)
                                    return null
                                } else {
                                    // General terminal failure
                                    val message = "خطای غیرمنتظره: ${e.localizedMessage ?: "خطای سیستمی"}"
                                    LogManager.log(message)
                                    onUpdateTaskState(currentKeyIndex, currentModelIndex, null, message)
                                    throw e
                                }
                            }
                        }
                    }
                } // retry attempt loop
                
                // If indices changed inside loop due to rate limits (429), they will execute on next loops automatically
            } // model loop
        } // key loop

        // If we ran out of keys and did not return a value
        val error = "تمامی کلیدهای امنیتی و مدل‌های تعریف شده با خطا یا محدودیت مواجه شدند."
        onUpdateTaskState(0, 0, null, error)
        LogManager.log(error)
        return null
    }

    private fun getErrorCode(e: Exception): Int {
        if (e is HttpException) {
            return e.code()
        }
        val message = e.message ?: ""
        if (message.contains("403")) return 403
        if (message.contains("429")) return 429
        if (message.contains("503")) return 503
        if (message.contains("500")) return 500
        return -1
    }

    // Command patterns to let the user instruct the engine upon failure
    fun setUserSelectedAction(action: String, allKeysSize: Int, modelsCount: Int) {
        LogManager.log("دستور اقدام کاربر وارد شد: $action")
        when (action) {
            "RETRY_SAME" -> {
                // Keep currentKeyIndex and currentModelIndex as is
            }
            "NEXT_MODEL" -> {
                currentModelIndex++
                if (currentModelIndex >= modelsCount) {
                    currentKeyIndex++
                    currentModelIndex = 0
                }
            }
            "NEXT_KEY" -> {
                currentKeyIndex++
                currentModelIndex = 0
            }
        }
        
        if (currentKeyIndex >= allKeysSize) {
            currentKeyIndex = 0
            currentModelIndex = 0
        }
    }

    fun resetState() {
        currentKeyIndex = 0
        currentModelIndex = 0
    }

    fun setState(keyIndex: Int, modelIndex: Int) {
        currentKeyIndex = keyIndex
        currentModelIndex = modelIndex
    }
}
