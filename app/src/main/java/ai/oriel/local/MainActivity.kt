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
    var profile by remember { mutableStateOf(BehaviorProfile.load(context)) }
    var modelUri by remember {
        mutableStateOf(context.getSharedPreferences("oriel_model", 0).getString("uri", null))
    }
    var status by remember { mutableStateOf("Runtime chưa được tích hợp; chọn model chỉ lưu tham chiếu tệp.") }
    var tab by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            modelUri = uri.toString()
            context.getSharedPreferences("oriel_model", 0).edit().putString("uri", modelUri).apply()
            status = "Đã chọn tệp GGUF. Chưa nạp được cho đến khi tích hợp llama.cpp JNI."
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
                Text("Ứng dụng Android local · không cần Termux")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Mô hình") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Hành vi AI") })
                    FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Tác vụ") })
                }
                when (tab) {
                    0 -> {
                        Text("Quản lý mô hình", style = MaterialTheme.typography.titleMedium)
                        Text(modelUri?.let { "Tệp đã chọn: " + it } ?: "Chưa chọn mô hình GGUF.")
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Chọn tệp GGUF") }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Text("Mô hình chưa được đóng gói trong APK. Bản phát hành cần kiểm tra loại tệp, dung lượng, license và checksum.")
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
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Text("Bộ điều phối tác vụ", style = MaterialTheme.typography.titleMedium)
                        Text("Mục tiêu thiết kế: lập kế hoạch → chia bước → gọi công cụ có quyền → kiểm tra kết quả → thử lại có giới hạn.")
                        Text("Tác vụ nền và gọi công cụ chưa được bật ở runtime này. Chúng sẽ không được giả lập.")
                        Text("Các thao tác nhạy cảm như chuyển tiền, xóa dữ liệu hoặc thay đổi tài khoản sẽ yêu cầu xác nhận rõ ràng.")
                    }
                }
            }
        }
    }
}
