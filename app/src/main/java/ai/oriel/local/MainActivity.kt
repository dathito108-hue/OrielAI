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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            modelUri = uri.toString()
            context.getSharedPreferences("oriel_model", 0).edit().putString("uri", modelUri).apply()
            status = "Đã chọn tệp GGUF. Runtime suy luận vẫn cần llama.cpp JNI."
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
                        Text(modelUri?.let { "Tệp được chọn thủ công: " + it } ?: "Chưa chọn tệp khác.")
                        Text("Lưu ý: tải xong chưa có nghĩa là AI đã chạy. Runtime llama.cpp JNI chưa được tích hợp nên chưa thể nạp và sinh câu trả lời local.")
                        Text("Runtime: " + RuntimeNotIntegrated().runtimeInfo())
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
                    else -> {
                        Text("Bộ điều phối tác vụ", style = MaterialTheme.typography.titleMedium)
                        Text("Mục tiêu: lập kế hoạch → chia bước → gọi công cụ có quyền → kiểm tra kết quả → thử lại có giới hạn.")
                        Text("Tác vụ nền và gọi công cụ chưa được bật ở runtime này. Chúng sẽ không được giả lập.")
                        Text("Các thao tác nhạy cảm như chuyển tiền, xóa dữ liệu hoặc thay đổi tài khoản sẽ yêu cầu xác nhận rõ ràng.")
                    }
                }
            }
        }
    }
}
