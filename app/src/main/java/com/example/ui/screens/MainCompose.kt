package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ApiKeyEntity
import com.example.data.database.ModelEntity
import com.example.data.database.PromptEntity
import com.example.data.database.SummaryTask
import com.example.ui.viewmodel.SummarizerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCompose(viewModel: SummarizerViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("home") }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "خلاصه‌ساز هوشمند کتاب الکترونیکی",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 18.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    NavigationBarItem(
                        selected = currentTab == "home",
                        onClick = { currentTab = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "خانه") },
                        label = { Text("پانل اصلی", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("nav_home_tab")
                    )
                    NavigationBarItem(
                        selected = currentTab == "history",
                        onClick = { currentTab = "history" },
                        icon = { Icon(Icons.Default.List, contentDescription = "کتابخانه") },
                        label = { Text("کتابخانه من", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("nav_history_tab")
                    )
                    NavigationBarItem(
                        selected = currentTab == "settings",
                        onClick = { currentTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "تنظیمات") },
                        label = { Text("گزینه‌ها", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (currentTab) {
                    "home" -> HomeScreen(viewModel = viewModel)
                    "history" -> HistoryScreen(viewModel = viewModel)
                    "settings" -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: SummarizerViewModel) {
    val context = LocalContext.current
    val selectedName by viewModel.selectedFileName.collectAsStateWithLifecycle()
    val selectedContent by viewModel.selectedFileContent.collectAsStateWithLifecycle()
    val apiKeysList by viewModel.apiKeys.collectAsStateWithLifecycle()
    val modelsList by viewModel.modelsForActiveKey.collectAsStateWithLifecycle()
    val promptsList by viewModel.prompts.collectAsStateWithLifecycle()

    val activeKey by viewModel.currentSelectedKey.collectAsStateWithLifecycle()
    val activeModel by viewModel.currentSelectedModel.collectAsStateWithLifecycle()
    val activePrompt by viewModel.currentSelectedPrompt.collectAsStateWithLifecycle()

    val activeTaskState by viewModel.currentTask.collectAsStateWithLifecycle()
    val logs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val content = readTextFromUri(context, uri)
            val name = getFileName(context, uri) ?: "book_content.txt"
            viewModel.selectFile(name, content)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Document selection block
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_selection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "سند",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))

                    if (selectedName != null) {
                        Text(
                            text = "سند آماده تلخیص: $selectedName",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "حجم: ${(selectedContent?.length ?: 0) / 1024} کیلوبایت",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.clearFile() },
                                modifier = Modifier.testTag("clear_file_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Clear, contentDescription = "تغییر فایل", modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("تغییر سند فایل")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "برای شروع، یک فایل متنی با فرمت TXT انتخاب نمایید",
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Button(
                            onClick = { filePicker.launch("text/plain") },
                            modifier = Modifier.testTag("pick_file_button")
                        ) {
                            Text("انتخاب سند متنی (TXT)")
                        }
                    }
                }
            }
        }

        // Active Quick Options
        if (selectedName != null && activeTaskState == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "تنظیمات سریع مدل جمینای",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(12.dp))

                        // Key Selector
                        Text("کلید امنیتی جمینای فعال:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        var keyExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { keyExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("key_selector_dropdown")
                            ) {
                                Text(activeKey?.name ?: "انتخاب کلید امنیتی")
                            }
                            DropdownMenu(
                                expanded = keyExpanded,
                                onDismissRequest = { keyExpanded = false }
                            ) {
                                apiKeysList.forEach { key ->
                                    DropdownMenuItem(
                                        text = { Text(key.name) },
                                        onClick = {
                                            viewModel.selectKey(key)
                                            keyExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        // Model Selector
                        Text("مدل هوش مصنوعی هوشمند:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        var modelExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { modelExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("model_selector_dropdown"),
                                enabled = modelsList.isNotEmpty()
                            ) {
                                Text(activeModel?.displayName ?: "انتخاب مدل")
                            }
                            DropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                modelsList.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.displayName) },
                                        onClick = {
                                            viewModel.selectModel(model)
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        // Prompt Selector
                        Text("دستورالعمل خلاصه نویسی:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        var promptExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { promptExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("prompt_selector_dropdown")
                            ) {
                                Text(activePrompt?.title ?: "انتخاب دستور خلاصه")
                            }
                            DropdownMenu(
                                expanded = promptExpanded,
                                onDismissRequest = { promptExpanded = false }
                            ) {
                                promptsList.forEach { prompt ->
                                    DropdownMenuItem(
                                        text = { Text(prompt.title) },
                                        onClick = {
                                            viewModel.selectPrompt(prompt)
                                            promptExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.size(16.dp))

                        Button(
                            onClick = { viewModel.startSummarizationService(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_summarizing_button"),
                            enabled = activeKey != null && activeModel != null && activePrompt != null
                        ) {
                            Text("شروع تلخیص مستمر بخش‌ها")
                        }
                    }
                }
            }
        }

        // Active engine progress
        if (activeTaskState != null) {
            val task = activeTaskState!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (task.status == "PROCESSING") MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "وضعیت فرآیند: ${resolveStatusPersian(task.status)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.size(8.dp))

                        val totalChunks = task.totalChunks
                        val currentChunk = task.currentChunkIndex
                        val progress = if (totalChunks > 0) currentChunk.toFloat() / totalChunks.toFloat() else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .testTag("task_progress_bar")
                        )
                        Spacer(modifier = Modifier.size(8.dp))

                        Text(
                            text = "پیشرفت: ${(progress * 100).toInt()}% (بخش $currentChunk از $totalChunks)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        if (task.errorMessage != null) {
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = "توضیح خطا: ${task.errorMessage}",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.size(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (task.status == "PROCESSING") {
                                Button(
                                    onClick = { viewModel.pauseSummarizationService(context, task.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.testTag("pause_task_button")
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = "توقف")
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("توقف موقت")
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.resumeSummarizationService(context, task.id) },
                                    modifier = Modifier.testTag("resume_task_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "ادامه")
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("ادامه عملیات")
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.setFocusedTask(null) }
                            ) {
                                Text("بستن ناظر")
                            }
                        }
                    }
                }
            }
        }

        // Live Log Output
        if (activeTaskState != null && activeTaskState?.status == "PROCESSING") {
            item {
                Text(
                    text = "گزارش زنده پیشرفت وب‌سرویس:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(logs) { logLine ->
                                Text(
                                    text = logLine,
                                    color = Color.Green,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Reactive State Dialog Boxes
    if (activeTaskState != null) {
        val task = activeTaskState!!
        
        // VPN Error (403 or network issue)
        if (task.status == "WAITING_FOR_VPN") {
            Dialog(onDismissRequest = { /* forced action */ }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "هشدار فیلترینگ",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = "دسترسی تحریم یا قطع ارتباط",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = task.errorMessage ?: "خطای ۴۰۳ یا عدم دسترسی منطقه‌ای رخ داد. لطفاً ابزار تغییر آی‌پی (VPN) خود را روشن کرده و مجدداً روی دکمه «ادامه» ضربه بزنید.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.resumeSummarizationService(context, task.id) }
                            ) {
                                Text("ادامه")
                            }
                            OutlinedButton(
                                onClick = { viewModel.pauseSummarizationService(context, task.id) }
                            ) {
                                Text("توقف موقت")
                            }
                        }
                    }
                }
            }
        }

        // Server Failure Options (503 and transient server error threshold exceeded)
        if (task.status == "WAITING_FOR_SERVER_RETRY") {
            Dialog(onDismissRequest = { /* forced action */ }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "پاسخی از سرور دریافت نشد",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = "وقفه سرور ابری در تکرارها",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "سرور جمینای با خطای موقت مواجه شد. در تمایل خود مشخص فرمایید مایلید چه اقدامی صورت بپذیرد:",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.makeTaskDecision(context, task.id, "RETRY_SAME") }
                            ) {
                                Text("تلاش مجدد روی همین مدل")
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.makeTaskDecision(context, task.id, "NEXT_MODEL") }
                            ) {
                                Text("سویچ به مدل بعدی")
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.makeTaskDecision(context, task.id, "NEXT_KEY") }
                            ) {
                                Text("تغییر به کلید API بعدی")
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.pauseSummarizationService(context, task.id) }
                            ) {
                                Text("توقف موقت فرآیند")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: SummarizerViewModel) {
    val context = LocalContext.current
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()

    val completedTasks = allTasks.filter { it.status == "COMPLETED" }
    val unfinishedTasks = allTasks.filter { it.status != "COMPLETED" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Unfinished sessions
        item {
            Text(
                text = "جلسات ناتمام و آماده جهت بازیابی:",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (unfinishedTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "هیچ جلسه معلقی یافت نگردید.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(unfinishedTasks) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(task.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "پیشرفت: ${task.currentChunkIndex} از ${task.totalChunks} بخش (${resolveStatusPersian(task.status)})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.setFocusedTask(task.id) }
                            ) {
                                Text("مشاهده و کنترل")
                            }
                            OutlinedButton(
                                onClick = { viewModel.deleteTaskSession(task.id) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                            ) {
                                Text("حذف")
                            }
                        }
                    }
                }
            }
        }

        // Section: Historial Completesummaries
        item {
            Text(
                text = "کتاب‌های تلخیص شده من (تاریخچه):",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (completedTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "هنوز خلاصه‌ای تولید نگردیده است.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(completedTasks) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(task.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "تعداد کل بخش‌ها: ${task.totalChunks} خلاصه شده",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.size(12.dp))

                        // Opens operations
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Open HTML Reader (VIRTUALIZED)
                            Button(
                                onClick = {
                                    task.finalSavedPathHtml?.let {
                                        openSavedFile(context, it, "text/html")
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("open_html_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "کتابخوان تعاملی HTML", modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("کتابخوان HTML")
                            }

                            // Share txt
                            OutlinedButton(
                                onClick = {
                                    task.finalSavedPathTxt?.let {
                                        shareSavedFile(context, it, "text/plain")
                                    }
                                },
                                modifier = Modifier.testTag("share_txt_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "اشتراک گذاری")
                            }

                            // Delete
                            IconButton(
                                onClick = { viewModel.deleteTaskSession(task.id) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف رکورد", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SummarizerViewModel) {
    val keysList by viewModel.apiKeys.collectAsStateWithLifecycle()
    val promptsList by viewModel.prompts.collectAsStateWithLifecycle()
    val allModelsList by viewModel.allModels.collectAsStateWithLifecycle(emptyList())

    val retryLimitSetting by viewModel.retryLimit.collectAsStateWithLifecycle()
    val retryDelaySecSetting by viewModel.retryDelaySec.collectAsStateWithLifecycle()

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showAddPromptDialog by remember { mutableStateOf(false) }

    // local settings state bound to fields
    var localRetryLimit by remember(retryLimitSetting) { mutableStateOf(retryLimitSetting.toString()) }
    var localRetryDelaySec by remember(retryDelaySecSetting) { mutableStateOf(retryDelaySecSetting.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Configurable retry limit and retry delay
        item {
            Text(
                text = "تنظیمات آستانه تلاش مجدد و تاخیرها",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = localRetryLimit,
                        onValueChange = { localRetryLimit = it },
                        label = { Text("آستانه تلاش مجدد (تعداد صدمه سرور ۵۰۳)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = localRetryDelaySec,
                        onValueChange = { localRetryDelaySec = it },
                        label = { Text("زمان تاخیر در تلاش مجدد (به ثانیه)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val retVal = localRetryLimit.toIntOrNull() ?: 5
                            val dlyVal = localRetryDelaySec.toIntOrNull() ?: 30
                            viewModel.saveSettings(retVal, dlyVal)
                        }
                    ) {
                        Text("ذخیره تنظیمات عددی")
                    }
                }
            }
        }

        // Section: Key & associated Models configurations
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مدیریت کلیدها و مدل‌ها",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = { showAddKeyDialog = true },
                    modifier = Modifier.testTag("add_key_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "کلید جدید")
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("کلید جدید")
                }
            }
        }

        itemsIndexed(keysList) { idx, apiKey ->
            var isEditingKey by remember { mutableStateOf(false) }
            var editName by remember { mutableStateOf(apiKey.name) }
            var editValue by remember { mutableStateOf(apiKey.key) }

            var showAddModelForm by remember { mutableStateOf(false) }
            var newModelName by remember { mutableStateOf("") }
            var newModelDisplayName by remember { mutableStateOf("") }

            val modelsByThisKey = allModelsList.filter { it.apiKeyId == apiKey.id }.sortedBy { it.displayOrder }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_card_${apiKey.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (isEditingKey) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("نام نمایشی کلید") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                OutlinedTextField(
                                    value = editValue,
                                    onValueChange = { editValue = it },
                                    label = { Text("مقدار فنی یا رشته کلید") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Row {
                                    Button(
                                        onClick = {
                                            viewModel.updateApiKey(apiKey.copy(name = editName, key = editValue))
                                            isEditingKey = false
                                        },
                                        modifier = Modifier.testTag("save_key_edit_button")
                                    ) {
                                        Text("ذخیره کلید")
                                    }
                                    Spacer(modifier = Modifier.size(4.dp))
                                    OutlinedButton(onClick = { isEditingKey = false }) {
                                        Text("انصراف")
                                    }
                                }
                            } else {
                                Text(apiKey.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = if (apiKey.key.length > 8) apiKey.key.take(4) + "••••••••" + apiKey.key.takeLast(4)
                                    else "فاقد مقدار کلید معتبر",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { viewModel.moveApiKeyUp(idx) },
                                enabled = idx > 0,
                                modifier = Modifier.testTag("move_key_up_$idx")
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "بالا")
                            }
                            IconButton(
                                onClick = { viewModel.moveApiKeyDown(idx) },
                                enabled = idx < keysList.count() - 1,
                                modifier = Modifier.testTag("move_key_down_$idx")
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "پایین")
                            }
                            IconButton(onClick = { isEditingKey = !isEditingKey }) {
                                Icon(Icons.Default.Edit, contentDescription = "ویرایش")
                            }
                            IconButton(
                                onClick = { viewModel.deleteApiKey(apiKey) },
                                modifier = Modifier.testTag("delete_key_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف کلید")
                            }
                        }
                    }

                    // Associated Models Grouping
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("مدلهای جمینای این کلید (به ترتیب کوشش):", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    modelsByThisKey.forEachIndexed { mIdx, model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(model.displayName, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                Text(model.modelName, fontSize = 10.sp, color = Color.Gray)
                            }
                            Row {
                                IconButton(
                                    onClick = { viewModel.moveModelUp(apiKey.id, mIdx) },
                                    enabled = mIdx > 0
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "بالا")
                                }
                                IconButton(
                                    onClick = { viewModel.moveModelDown(apiKey.id, mIdx) },
                                    enabled = mIdx < modelsByThisKey.count() - 1
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "پایین")
                                }
                                IconButton(onClick = { viewModel.deleteModel(model) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف مدل")
                                }
                            }
                        }
                    }

                    if (showAddModelForm) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                OutlinedTextField(
                                    value = newModelName,
                                    onValueChange = { newModelName = it },
                                    label = { Text("شناسه فنی جمینای (مانند: gemini-3.5-flash)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                OutlinedTextField(
                                    value = newModelDisplayName,
                                    onValueChange = { newModelDisplayName = it },
                                    label = { Text("نام نمایشی مدل فارسی") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Row {
                                    Button(
                                        onClick = {
                                            if (newModelName.isNotBlank() && newModelDisplayName.isNotBlank()) {
                                                viewModel.addModel(apiKey.id, newModelName, newModelDisplayName)
                                                newModelName = ""
                                                newModelDisplayName = ""
                                                showAddModelForm = false
                                            }
                                        }
                                    ) {
                                        Text("افزودن مدل")
                                    }
                                    Spacer(modifier = Modifier.size(4.dp))
                                    OutlinedButton(onClick = { showAddModelForm = false }) {
                                        Text("لغو")
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(4.dp))
                        OutlinedButton(
                            onClick = { showAddModelForm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_model_to_key_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "افزودن مدل")
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("افزودن مدل جدید به این کلید")
                        }
                    }
                }
            }
        }

        // Section: Prompts Manage
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "دستورالعمل‌ها و شیوه‌های نگارش (Prompt)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = { showAddPromptDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "دستور جدید")
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("دستور جدید")
                }
            }
        }

        itemsIndexed(promptsList) { idx, prompt ->
            var isEditingPrompt by remember { mutableStateOf(false) }
            var editTitle by remember { mutableStateOf(prompt.title) }
            var editText by remember { mutableStateOf(prompt.promptText) }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isEditingPrompt) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("عنوان سبک") },
                            singleLine = true,
                            enabled = !prompt.isDefault,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            label = { Text("دستور سیستم خلاصه نویس") },
                            enabled = !prompt.isDefault,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Row {
                            Button(
                                onClick = {
                                    viewModel.updatePrompt(prompt.copy(title = editTitle, promptText = editText))
                                    isEditingPrompt = false
                                }
                            ) {
                                Text("ثبت ویرایش")
                            }
                            Spacer(modifier = Modifier.size(4.dp))
                            OutlinedButton(onClick = { isEditingPrompt = false }) {
                                Text("انصراف")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prompt.title + if (prompt.isDefault) " [پیش‌فرض سیستم]" else "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Row {
                                IconButton(onClick = { isEditingPrompt = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "مشاهده یا ویرایش دستورالعمل")
                                }
                                if (!prompt.isDefault) {
                                    IconButton(onClick = { viewModel.deletePrompt(prompt) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف سبک")
                                    }
                                }
                            }
                        }
                        Text(
                            text = prompt.promptText,
                            fontSize = 11.sp,
                            maxLines = 4,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    // New Key Dialog Form
    if (showAddKeyDialog) {
        var keyName by remember { mutableStateOf("") }
        var keyVal by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddKeyDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("تعریف کلید امنیتی جدید جمینای", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.size(12.dp))
                    OutlinedTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        label = { Text("نام نمایشی کلید معرفی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = keyVal,
                        onValueChange = { keyVal = it },
                        label = { Text("مقدار رشته کلید (AIza...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (keyName.isNotBlank() && keyVal.isNotBlank()) {
                                    viewModel.addApiKey(keyName, keyVal)
                                    showAddKeyDialog = false
                                }
                            }
                        ) {
                            Text("ثبت کلید")
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedButton(onClick = { showAddKeyDialog = false }) {
                            Text("انصراف")
                        }
                    }
                }
            }
        }
    }

    // New Prompt Dialog Form
    if (showAddPromptDialog) {
        var pTitle by remember { mutableStateOf("") }
        var pText by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddPromptDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("تعریف سبک جدید خلاصه نویس", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.size(12.dp))
                    OutlinedTextField(
                        value = pTitle,
                        onValueChange = { pTitle = it },
                        label = { Text("عنوان سبک") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = pText,
                        onValueChange = { pText = it },
                        label = { Text("متن کامل دستورالعمل خلاصه نویسی") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (pTitle.isNotBlank() && pText.isNotBlank()) {
                                    viewModel.addPrompt(pTitle, pText)
                                    showAddPromptDialog = false
                                }
                            }
                        ) {
                            Text("ثبت دستور")
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedButton(onClick = { showAddPromptDialog = false }) {
                            Text("انصراف")
                        }
                    }
                }
            }
        }
    }
}

private fun resolveStatusPersian(status: String): String {
    return when (status) {
        "IDLE" -> "در صف شروع"
        "PROCESSING" -> "در حال پردازش هوشمند..."
        "PAUSED" -> "متوقف شده"
        "FAILED" -> "ناموفق به دلیل خطا"
        "WAITING_FOR_VPN" -> "در انتظار تغییر آی‌پی (VPN)"
        "WAITING_FOR_SERVER_RETRY" -> "در انتظار تصمیم چگونگی تلاش مجدد"
        "COMPLETED" -> "لذت ببرید! به پایان رسید"
        else -> "نامشخص ($status)"
    }
}

// Android Intent triggers
fun openSavedFile(context: Context, pathOrUriString: String, mimeType: String) {
    try {
        val uri = getShareableUriFromPathString(context, pathOrUriString)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(viewIntent, "باز کردن خلاصه"))
    } catch (e: Exception) {
        Toast.makeText(context, "برنامه‌ای مناسب جهت باز کردن این فایل پیدا نشد: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun shareSavedFile(context: Context, pathOrUriString: String, mimeType: String) {
    try {
        val uri = getShareableUriFromPathString(context, pathOrUriString)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری خلاصه کتاب با دیگران..."))
    } catch (e: Exception) {
        Toast.makeText(context, "خطا در اشتراک‌گذاری: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun getShareableUriFromPathString(context: Context, pathOrUriString: String): Uri {
    val uri = Uri.parse(pathOrUriString)
    if (uri.scheme == "content") {
        return uri
    }
    val path = if (uri.scheme == "file") uri.path else pathOrUriString
    val file = File(path ?: "")
    return FileProvider.getUriForFile(context, "com.example.fileprovider", file)
}

private fun readTextFromUri(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        inputStream.bufferedReader().use { it.readText() }
    } ?: ""
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}
