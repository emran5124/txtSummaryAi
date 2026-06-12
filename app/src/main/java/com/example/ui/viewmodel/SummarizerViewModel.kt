package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.api.LogManager
import com.example.data.database.*
import com.example.data.repository.SummarizerRepository
import com.example.service.SummaryProcessingService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SummarizerViewModel(private val repository: SummarizerRepository) : ViewModel() {

    val apiKeys = repository.allApiKeys.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val prompts = repository.allPrompts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allModels = repository.allModels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTasks = repository.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current focused task state (can be an active one, or selected historic one)
    private val _currentTaskId = MutableStateFlow<Long?>(null)
    val currentTaskId: StateFlow<Long?> = _currentTaskId

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTask: StateFlow<SummaryTask?> = _currentTaskId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getTaskFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val diagnosticLogs = LogManager.logs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName: StateFlow<String?> = _selectedFileName

    private val _selectedFileContent = MutableStateFlow<String?>(null)
    val selectedFileContent: StateFlow<String?> = _selectedFileContent

    private val _currentSelectedKey = MutableStateFlow<ApiKeyEntity?>(null)
    val currentSelectedKey: StateFlow<ApiKeyEntity?> = _currentSelectedKey

    private val _currentSelectedModel = MutableStateFlow<ModelEntity?>(null)
    val currentSelectedModel: StateFlow<ModelEntity?> = _currentSelectedModel

    private val _currentSelectedPrompt = MutableStateFlow<PromptEntity?>(null)
    val currentSelectedPrompt: StateFlow<PromptEntity?> = _currentSelectedPrompt

    // Settings config mapping values
    private val _retryLimit = MutableStateFlow(5)
    val retryLimit: StateFlow<Int> = _retryLimit

    private val _retryDelaySec = MutableStateFlow(30)
    val retryDelaySec: StateFlow<Int> = _retryDelaySec

    @OptIn(ExperimentalCoroutinesApi::class)
    val modelsForActiveKey: StateFlow<List<ModelEntity>> = _currentSelectedKey
        .flatMapLatest { key ->
            if (key == null) flowOf(emptyList())
            else repository.getModelsForKey(key.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            // Seeding primary development configuration
            val keyFromConfig = MainActivity.getDevApiKey()
            repository.ensureSeedsInstalled(keyFromConfig)

            // Auto-select starting entities when lists populate
            apiKeys.firstOrNull { it.isNotEmpty() }?.let { keys ->
                selectKey(keys.first())
            }
            prompts.firstOrNull { it.isNotEmpty() }?.let { prs ->
                selectPrompt(prs.first())
            }

            // Load settings
            _retryLimit.value = repository.getSetting("retry_limit")?.value?.toIntOrNull() ?: 5
            _retryDelaySec.value = repository.getSetting("retry_delay_sec")?.value?.toIntOrNull() ?: 30
        }
    }

    fun selectKey(key: ApiKeyEntity) {
        _currentSelectedKey.value = key
        viewModelScope.launch {
            val models = repository.getModelsForKeySync(key.id)
            if (models.isNotEmpty()) {
                _currentSelectedModel.value = models.first()
            } else {
                _currentSelectedModel.value = null
            }
        }
    }

    fun selectModel(model: ModelEntity) {
        _currentSelectedModel.value = model
    }

    fun selectPrompt(prompt: PromptEntity) {
        _currentSelectedPrompt.value = prompt
    }

    fun selectFile(rawName: String, content: String) {
        _selectedFileName.value = rawName
        _selectedFileContent.value = content
    }

    fun clearFile() {
        _selectedFileName.value = null
        _selectedFileContent.value = null
    }

    fun setFocusedTask(id: Long?) {
        _currentTaskId.value = id
    }

    fun saveSettings(retryVal: Int, delayVal: Int) {
        _retryLimit.value = retryVal
        _retryDelaySec.value = delayVal
        viewModelScope.launch {
            repository.insertSetting("retry_limit", retryVal.toString())
            repository.insertSetting("retry_delay_sec", delayVal.toString())
            LogManager.log("تنظیمات با موفقیت در پایگاه داده ذخیره گردید.")
        }
    }

    fun addApiKey(name: String, key: String) {
        viewModelScope.launch {
            val order = (apiKeys.value.maxByOrNull { it.displayOrder }?.displayOrder ?: -1) + 1
            repository.insertApiKey(ApiKeyEntity(name = name, key = key, displayOrder = order))
        }
    }

    fun updateApiKey(key: ApiKeyEntity) {
        viewModelScope.launch {
            repository.updateApiKey(key)
        }
    }

    fun deleteApiKey(key: ApiKeyEntity) {
        viewModelScope.launch {
            repository.deleteApiKeyById(key.id)
        }
    }

    fun addModel(apiKeyId: Long, name: String, displayName: String) {
        viewModelScope.launch {
            val currentModels = repository.getModelsForKeySync(apiKeyId)
            val order = (currentModels.maxByOrNull { it.displayOrder }?.displayOrder ?: -1) + 1
            repository.insertModel(
                ModelEntity(
                    apiKeyId = apiKeyId,
                    modelName = name,
                    displayName = displayName,
                    displayOrder = order
                )
            )
        }
    }

    fun updateModel(model: ModelEntity) {
        viewModelScope.launch {
            repository.updateModel(model)
        }
    }

    fun deleteModel(model: ModelEntity) {
        viewModelScope.launch {
            repository.deleteModelById(model.id)
        }
    }

    fun addPrompt(title: String, text: String) {
        viewModelScope.launch {
            repository.insertPrompt(PromptEntity(title = title, promptText = text))
        }
    }

    fun updatePrompt(prompt: PromptEntity) {
        viewModelScope.launch {
            repository.updatePrompt(prompt)
        }
    }

    fun deletePrompt(prompt: PromptEntity) {
        viewModelScope.launch {
            if (!prompt.isDefault) {
                repository.deletePromptById(prompt.id)
            }
        }
    }

    fun moveApiKeyUp(index: Int) {
        val list = apiKeys.value.toMutableList()
        if (index > 0) {
            val element = list.removeAt(index)
            list.add(index - 1, element)
            viewModelScope.launch {
                repository.updateKeysOrdering(list)
            }
        }
    }

    fun moveApiKeyDown(index: Int) {
        val list = apiKeys.value.toMutableList()
        if (index < list.size - 1) {
            val element = list.removeAt(index)
            list.add(index + 1, element)
            viewModelScope.launch {
                repository.updateKeysOrdering(list)
            }
        }
    }

    fun moveModelUp(apiKeyId: Long, index: Int) {
        viewModelScope.launch {
            val models = repository.getModelsForKeySync(apiKeyId).toMutableList()
            if (index > 0) {
                val element = models.removeAt(index)
                models.add(index - 1, element)
                repository.updateModelsOrdering(models)
            }
        }
    }

    fun moveModelDown(apiKeyId: Long, index: Int) {
        viewModelScope.launch {
            val models = repository.getModelsForKeySync(apiKeyId).toMutableList()
            if (index < models.size - 1) {
                val element = models.removeAt(index)
                models.add(index + 1, element)
                repository.updateModelsOrdering(models)
            }
        }
    }

    /**
     * Start high-reliability foreground service processing loop
     */
    fun startSummarizationService(context: Context) {
        val activeModel = currentSelectedModel.value ?: return
        val activePrompt = currentSelectedPrompt.value ?: return
        val name = selectedFileName.value ?: "کتاب_نامشخص.txt"
        val content = selectedFileContent.value ?: return

        LogManager.clear()
        LogManager.log("ایجاد فرآیند تلخیص فایل صوتی/متنی جدید...")

        viewModelScope.launch {
            val generatedTaskId = repository.createNewSession(
                fileName = name,
                fileContent = content,
                selectedPromptId = activePrompt.id,
                selectedModelId = activeModel.id
            )

            _currentTaskId.value = generatedTaskId

            // Launch active foreground background task Sync Service
            val intent = Intent(context, SummaryProcessingService::class.java).apply {
                action = SummaryProcessingService.ACTION_START_OR_RESUME
                putExtra(SummaryProcessingService.EXTRA_TASK_ID, generatedTaskId)
            }
            context.startService(intent)
        }
    }

    fun resumeSummarizationService(context: Context, taskId: Long) {
        _currentTaskId.value = taskId
        LogManager.log("از سرگیری پردازش شناسه: $taskId")
        viewModelScope.launch {
            val task = repository.getTaskSync(taskId)
            if (task != null) {
                val intent = Intent(context, SummaryProcessingService::class.java).apply {
                    action = SummaryProcessingService.ACTION_START_OR_RESUME
                    putExtra(SummaryProcessingService.EXTRA_TASK_ID, taskId)
                }
                context.startService(intent)
            }
        }
    }

    fun pauseSummarizationService(context: Context, taskId: Long) {
        val intent = Intent(context, SummaryProcessingService::class.java).apply {
            action = SummaryProcessingService.ACTION_PAUSE
            putExtra(SummaryProcessingService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    fun makeTaskDecision(context: Context, taskId: Long, decisionAction: String) {
        val intent = Intent(context, SummaryProcessingService::class.java).apply {
            action = SummaryProcessingService.ACTION_USER_DECISION
            putExtra(SummaryProcessingService.EXTRA_TASK_ID, taskId)
            putExtra(SummaryProcessingService.EXTRA_DECISION_ACTION, decisionAction)
        }
        context.startService(intent)
    }

    fun deleteTaskSession(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTaskSession(taskId)
            if (_currentTaskId.value == taskId) {
                _currentTaskId.value = null
            }
        }
    }
}

class SummarizerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val repo = SummarizerRepository(
            db.apiKeyDao(),
            db.modelDao(),
            db.promptDao(),
            db.summaryTaskDao(),
            db.taskChunkDao(),
            db.appSettingDao()
        )
        return SummarizerViewModel(repo) as T
    }
}
