package ai.oriel.local

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OrielApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrielApp() {
    val context = LocalContext.current
    val downloader = remember(context) { ModelDownloadManager(context) }
    var profile by remember { mutableStateOf(BehaviorProfile.load(context)) }
    var modelUri by remember {
        mutableStateOf(context.getSharedPreferences("oriel_model", 0).getString("uri", null))
    }
    var status by remember { mutableStateOf("Chưa tải mô hình.") }
    var downloadId by remember {
        mutableLongStateOf(context.getSharedPreferences(ModelDownloadManager.PREFS, 0)
            .getLong(ModelDownloadManager.KEY_DOWNLOAD_ID, 0L))
    }
    var downloadState by remember { mutableStateOf<DownloadStatus?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    val chatRuntime = remember(context) { LlamaCppRuntime(context) }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var chatInput by remember { mutableStateOf("") }
    var chatReady by remember { mutableStateOf(false) }
    var chatBusy by remember { mutableStateOf(false) }
    var chatStatus by remember { mutableStateOf("Nạp mô hình để bắt đầu suy luận local.") }
    val scope = rememberCoroutineScope()
    var selectedModelPath by remember { mutableStateOf(context.getSharedPreferences("oriel_model", 0).getString("private_path", null)) }
    val taskStore = remember(context) { TaskStore(context) }
    val taskItems = remember { mutableStateListOf<OrielTask>().apply { addAll(taskStore.all()) } }
    var taskInput by remember { mutableStateOf("") }
    var taskBusy by remember { mutableStateOf(false) }
    var taskStatus by remember { mutableStateOf("Tác vụ được lưu trên thiết bị; cần nạp mô hình để chạy.") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            modelUri = uri.toString()
            context.getSharedPreferences("oriel_model", 0).edit().putString("uri", modelUri).apply()
            status = "Đang sao chép GGUF vào bộ nhớ riêng của OrielAI…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val name = (uri.lastPathSegment ?: "imported-model.gguf")
                            .substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                            .let { if (it.endsWith(".gguf", ignoreCase = true)) it else "${it}.gguf" }
                        val dir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
                        val target = File(dir, name)
                        val partial = File(dir, "${name}.part")
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(partial).use { output -> input.copyTo(output) }
                            } ?: error("Không mở được tệp đã chọn")
                            require(partial.length() > 1024 * 1024) { "Tệp quá nhỏ, không giống GGUF hợp lệ" }
                            val magic = ByteArray(4)
                            partial.inputStream().use { input ->
                                require(input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                                    "Sai định dạng: tệp không có chữ ký GGUF"
                                }
                            }
                            if (target.exists()) target.delete()
                            check(partial.renameTo(target)) { "Không thể hoàn tất lưu tệp mô hình" }
                            target.absolutePath
                        } finally {
                            if (partial.exists()) partial.delete()
                        }
                    }
                }
                result.onSuccess { path ->
                    selectedModelPath = path
                    context.getSharedPreferences("oriel_model", 0).edit().putString("private_path", path).apply()
                    status = "Đã nhập mô hình vào bộ nhớ riêng: ${path}"
                }.onFailure { e -> status = "Nhập mô hình thất bại: ${e.message ?: "lỗi không xác định"}" }
            }
        }
    }

    LaunchedEffect(downloadId) {
        if (downloadId > 0L) {
            while (true) {
                val current = downloader.status(downloadId)
                downloadState = current
                when (current) {
                    is DownloadStatus.Running -> {
                        status = current.label
                        delay(1000)
                    }
                    is DownloadStatus.Complete -> {
                        status = "Đã tải mô hình: ${current.path}"
                        break
                    }
                    is DownloadStatus.Failed -> {
                        status = current.message
                        break
                    }
                }
            }
        } else {
            val existing = downloader.existingModel()
            if (existing != null) {
                downloadState = DownloadStatus.Complete(existing.absolutePath, existing.length())
                status = "Đã có mô hình trên thiết bị: ${existing.absolutePath}"
            }
        }
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("OrielAI · Local AI") }) }) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Qwen3.5-0.8B", style = MaterialTheme.typography.headlineSmall)
                Text("Qwen là nền móng ban đầu; OrielAI sẽ phát triển lớp điều phối, bộ nhớ, công cụ và đánh giá riêng.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Mô hình") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Hành vi AI") })
                    FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Tác vụ") })
                    FilterChip(selected = tab == 3, onClick = { tab = 3 }, label = { Text("Trò chuyện") })
                }
                when (tab) {
                    0 -> {
                        Text("Quản lý mô hình", style = MaterialTheme.typography.titleMedium)
                        Text("Tải trực tiếp vào vùng lưu trữ riêng của ứng dụng (không cần Termux).")
                        Button(
                            enabled = downloadState !is DownloadStatus.Running,
                            onClick = {
                                val existing = downloader.existingModel()
                                if (existing != null) {
                                    downloadState = DownloadStatus.Complete(existing.absolutePath, existing.length())
                                    status = "Mô hình đã có sẵn: ${existing.absolutePath}"
                                } else {
                                    downloadState = null
                                    status = "Đang khởi tạo tải xuống…"
                                    downloadId = downloader.start()
                                    if (downloadId == -1L) {
                                        val found = downloader.existingModel()
                                        if (found != null) {
                                            downloadState = DownloadStatus.Complete(found.absolutePath, found.length())
                                            status = "Mô hình đã có sẵn: ${found.absolutePath}"
                                        }
                                    }
                                }
                            }
                        ) { Text(if (downloader.existingModel() != null) "Mô hình đã tải · kiểm tra" else "Tải Qwen3.5-0.8B (~563 MB)") }

                        if (downloadState is DownloadStatus.Running) {
                            val running = downloadState as DownloadStatus.Running
                            val fraction = if (running.total > 0) running.downloaded.toFloat() / running.total else 0f
                            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text(if (running.total > 0) "${running.label}: ${running.downloaded / 1_000_000} / ${running.total / 1_000_000} MB"
                                else running.label)
                        }
                        if (downloadState is DownloadStatus.Complete) {
                            val complete = downloadState as DownloadStatus.Complete
                            Text("Đã tải: ${complete.bytes / 1_000_000} MB")
                            Text("Đường dẫn nội bộ ứng dụng: ${complete.path}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Text("Nguồn: ggml-org/Qwen3.5-0.8B-GGUF · bản Q4_0. Tải xuống cần kết nối Internet và đủ dung lượng trống. Tệp được lưu trong thư mục riêng của OrielAI.")
                        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Chọn GGUF khác") }
                        Text(modelUri?.let { "URI nguồn: " + it } ?: "Chưa chọn tệp khác.")
                        Text(selectedModelPath?.let { "Bản sao trong vùng riêng: ${it}" } ?: "Chưa có bản sao mô hình đã nhập.")
                        Text("Lưu ý: tải xong chưa có nghĩa là AI đã chạy. Hãy mở tab Trò chuyện, nạp mô hình và kiểm tra kết quả thực tế.")
                        Text("Runtime: " + LlamaCppRuntime(context).runtimeInfo())
                    }
                    1 -> {
                        Text("Chỉ dẫn hệ thống", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = profile.systemPrompt,
                            onValueChange = { profile = profile.copy(systemPrompt = it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                            label = { Text("System prompt") }
                        )
                        Text("Mức độ chủ động: " + (profile.initiative * 100).toInt() + "%")
                        Slider(value = profile.initiative, onValueChange = { profile = profile.copy(initiative = it) })
                        Text("Temperature: %.2f".format(profile.temperature))
                        Slider(value = profile.temperature, onValueChange = { profile = profile.copy(temperature = it) }, valueRange = 0f..1.5f)
                        Text("Giới hạn sinh: " + profile.maxTokens + " token")
                        Slider(
                            value = profile.maxTokens.toFloat(),
                            onValueChange = { profile = profile.copy(maxTokens = it.toInt().coerceIn(128, 2048)) },
                            valueRange = 128f..2048f
                        )
                        Row {
                            Checkbox(profile.allowBackgroundTasks, onCheckedChange = { profile = profile.copy(allowBackgroundTasks = it) })
                            Text("Cho phép tác vụ nền (chưa chạy đến khi có bộ điều phối)")
                        }
                        Row {
                            Checkbox(profile.allowTools, onCheckedChange = { profile = profile.copy(allowTools = it) })
                            Text("Cho phép công cụ (chưa có công cụ nào được cấp)")
                        }
                        Button(onClick = { profile.save(context); status = "Đã lưu hồ sơ hành vi trên thiết bị." }) {
                            Text("Lưu cấu hình")
                        }
                        OutlinedButton(onClick = {
                            profile = BehaviorProfile(BehaviorProfile.DEFAULT_PROMPT, 0.7f, 0.7f, 512, true, false)
                            profile.save(context)
                            status = "Đã khôi phục cấu hình mặc định."
                        }) { Text("Khôi phục mặc định") }
                    }
                    2 -> {
                        Text("Bộ điều phối tác vụ local", style = MaterialTheme.typography.titleMedium)
                        Text("Tạo tác vụ, yêu cầu mô hình lập kế hoạch rồi xử lý từng bước theo thứ tự. Tiến độ và kết quả được lưu cục bộ để còn sau khi đóng ứng dụng.")
                        Text("Giới hạn an toàn: phiên bản này chỉ suy luận bằng mô hình local; chưa gọi API, chưa điều khiển ứng dụng khác và không tự thực hiện giao dịch hay thao tác hệ thống.")
                        OutlinedTextField(
                            value = taskInput,
                            onValueChange = { taskInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mục tiêu cần hoàn thành") },
                            minLines = 2
                        )
                        Button(
                            enabled = taskInput.isNotBlank() && !taskBusy,
                            onClick = {
                                val goal = taskInput.trim().take(4000)
                                val task = taskStore.create(goal)
                                taskItems.add(0, task)
                                taskInput = ""
                                taskStatus = "Đã lưu tác vụ. Nạp mô hình trong tab Trò chuyện để thực thi."
                            }
                        ) { Text("Tạo và lưu tác vụ") }
                        Text(taskStatus, style = MaterialTheme.typography.bodySmall)
                        if (!chatReady) {
                            Text("Để chạy tác vụ: mở tab Trò chuyện → nạp mô hình local. Tác vụ đã tạo vẫn được lưu.")
                        }
                        taskItems.toList().forEach { task ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(task.goal, style = MaterialTheme.typography.titleSmall)
                                    Text("Trạng thái: ${task.state} · bước ${task.completedSteps}/${task.steps.size}")
                                    if (task.steps.isNotEmpty()) Text(task.steps.mapIndexed { i, s -> "${i + 1}. ${s}" }.joinToString("\n"))
                                    if (task.error.isNotBlank()) Text("Lỗi: ${task.error}", color = MaterialTheme.colorScheme.error)
                                    if (task.result.isNotBlank()) Text("Kết quả gần nhất:\n${task.result.take(5000)}")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = chatReady && !chatBusy && !taskBusy &&
                                                task.state != OrielTaskState.RUNNING &&
                                                task.state != OrielTaskState.COMPLETED,
                                            onClick = {
                                                taskBusy = true
                                                taskStatus = "Đang lập kế hoạch và thực hiện từng bước bằng mô hình local…"
                                                scope.launch {
                                                    var current = task.copy(state = OrielTaskState.RUNNING, error = "")
                                                    taskStore.save(current)
                                                    val planResult = chatRuntime.generate(
                                                        listOf(
                                                            ChatMessage("system", "Bạn là bộ lập kế hoạch tác vụ. Chỉ tạo kế hoạch ngắn, tối đa 4 bước. Mỗi bước phải là một dòng đánh số dạng 1. ... Không tuyên bố đã thực hiện hành động bên ngoài."),
                                                            ChatMessage("user", "Lập kế hoạch để đạt mục tiêu sau:\n${current.goal}\nChỉ trả về các bước đánh số.")
                                                        ),
                                                        profile.copy(maxTokens = minOf(profile.maxTokens, 512))
                                                    )
                                                    if (planResult.isFailure) {
                                                        current = current.copy(state = OrielTaskState.FAILED, error = planResult.exceptionOrNull()?.message ?: "Không lập được kế hoạch")
                                                        taskStore.save(current)
                                                        taskStatus = "Lập kế hoạch thất bại."
                                                    } else {
                                                        val rawPlan = planResult.getOrThrow()
                                                        val parsed = rawPlan.lineSequence()
                                                            .map { it.trim() }
                                                            .filter { Regex("^\\d+[.)、-]\\s*.+").matches(it) }
                                                            .map { it.replaceFirst(Regex("^\\d+[.)、-]\\s*"), "").trim() }
                                                            .filter { it.isNotBlank() }
                                                            .take(4).toList()
                                                        val steps = if (parsed.isEmpty()) listOf("Phân tích mục tiêu và xây dựng câu trả lời hoàn chỉnh") else parsed
                                                        current = current.copy(steps = steps, completedSteps = 0, result = "", state = OrielTaskState.RUNNING)
                                                        taskStore.save(current)
                                                        var accumulated = ""
                                                        var failure: String? = null
                                                        for ((index, step) in steps.withIndex()) {
                                                            val result = chatRuntime.generate(
                                                                listOf(
                                                                    ChatMessage("system", profile.systemPrompt + "\nBạn đang thực hiện một bước trong tác vụ. Nêu rõ suy luận và kết quả; không giả vờ gọi công cụ hoặc làm việc ngoài ứng dụng."),
                                                                    ChatMessage("user", "Mục tiêu tổng thể: ${current.goal}\nKế hoạch: ${steps.mapIndexed { i, s -> "${i + 1}. ${s}" }.joinToString("\n")}\nBước hiện tại (${index + 1}/${steps.size}): ${step}\nKết quả các bước trước:\n${accumulated.take(5000)}\nHãy hoàn thành bước hiện tại một cách cụ thể.")
                                                                ),
                                                                profile.copy(maxTokens = minOf(profile.maxTokens, 768))
                                                            )
                                                            if (result.isFailure) {
                                                                failure = result.exceptionOrNull()?.message ?: "Suy luận bước ${index + 1} thất bại"
                                                                break
                                                            }
                                                            accumulated += "\n\nBƯỚC ${index + 1}: ${step}\n${result.getOrThrow()}"
                                                            current = current.copy(completedSteps = index + 1, result = accumulated, error = "")
                                                            taskStore.save(current)
                                                            val pos = taskItems.indexOfFirst { it.id == current.id }
                                                            if (pos >= 0) taskItems[pos] = current
                                                        }
                                                        current = if (failure == null) current.copy(state = OrielTaskState.COMPLETED)
                                                                  else current.copy(state = OrielTaskState.FAILED, error = failure)
                                                        taskStore.save(current)
                                                        val pos = taskItems.indexOfFirst { it.id == current.id }
                                                        if (pos >= 0) taskItems[pos] = current else taskItems.add(0, current)
                                                        taskStatus = if (failure == null) "Đã hoàn tất các bước suy luận local; không có hành động bên ngoài nào được thực hiện."
                                                                     else "Tác vụ dừng ở bước ${current.completedSteps}: ${failure}"
                                                    }
                                                    taskBusy = false
                                                }
                                            }
                                        ) { Text(if (taskBusy && task.state == OrielTaskState.RUNNING) "Đang chạy…" else "Chạy tác vụ") }
                                        OutlinedButton(
                                            enabled = !taskBusy,
                                            onClick = {
                                                taskStore.delete(task.id)
                                                taskItems.removeAll { it.id == task.id }
                                                taskStatus = "Đã xóa tác vụ khỏi bộ nhớ cục bộ."
                                            }
                                        ) { Text("Xóa") }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text("Trò chuyện bằng mô hình local", style = MaterialTheme.typography.titleMedium)
                        Text("Runtime: ${chatRuntime.runtimeInfo()}")
                        Button(
                            enabled = !chatBusy && !chatReady,
                            onClick = {
                                val path = selectedModelPath ?: downloader.existingModel()?.absolutePath
                                if (path == null) {
                                    chatStatus = "Chưa có mô hình. Tải Qwen hoặc chọn tệp GGUF ở tab Mô hình."
                                } else {
                                    chatBusy = true
                                    chatStatus = "Đang nạp mô hình; lần đầu có thể mất một lúc…"
                                    scope.launch {
                                        val result = chatRuntime.load(path, 2048)
                                        result.onSuccess {
                                            chatReady = true
                                            chatStatus = "Mô hình đã được nạp. Bạn có thể trò chuyện."
                                        }.onFailure { e ->
                                            chatStatus = "Không nạp được mô hình: ${e.message ?: "lỗi không xác định"}"
                                        }
                                        chatBusy = false
                                    }
                                }
                            }
                        ) { Text(if (chatBusy) "Đang nạp…" else if (chatReady) "Mô hình đã nạp" else "Nạp mô hình local") }
                        Text(chatStatus, style = MaterialTheme.typography.bodySmall)
                        if (chatMessages.isEmpty()) {
                            Text("Cuộc trò chuyện sẽ xuất hiện ở đây. Câu trả lời được sinh bởi mô hình trên thiết bị khi runtime hoạt động.")
                        }
                        chatMessages.forEach { message ->
                            Surface(
                                color = if (message.role == "user") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(if (message.role == "user") "Bạn" else "OrielAI", style = MaterialTheme.typography.labelMedium)
                                    Text(message.content)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Tin nhắn") },
                            enabled = chatReady && !chatBusy
                        )
                        Button(
                            enabled = chatReady && !chatBusy && chatInput.isNotBlank(),
                            onClick = {
                                val prompt = chatInput.trim()
                                chatInput = ""
                                chatMessages.add(ChatMessage("user", prompt))
                                chatBusy = true
                                chatStatus = "Đang sinh câu trả lời trên thiết bị…"
                                scope.launch {
                                    val result = chatRuntime.generate(chatMessages.toList(), profile)
                                    result.onSuccess { answer ->
                                        chatMessages.add(ChatMessage("assistant", answer))
                                        chatStatus = "Đã sinh câu trả lời local."
                                    }.onFailure { e ->
                                        chatStatus = "Lỗi suy luận: ${e.message ?: "lỗi không xác định"}"
                                    }
                                    chatBusy = false
                                }
                            }
                        ) { Text(if (chatBusy) "Đang suy luận…" else "Gửi") }
                    }
                }
            }
        }
    }
}
