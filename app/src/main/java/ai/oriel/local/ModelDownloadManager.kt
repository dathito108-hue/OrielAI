package ai.oriel.local

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Downloads the official Q4_0 GGUF into OrielAI's app-specific external files.
 * This downloads weights only; it does not imply the native inference runtime is available.
 */
class ModelDownloadManager(private val context: Context) {
    companion object {
        const val MODEL_FILE = "Qwen3.5-0.8B-Q4_0.gguf"
        const val MODEL_URL =
            "https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_0.gguf"
        const val PREFS = "oriel_model_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_MODEL_PATH = "model_path"
    }

    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun existingModel(): File? {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), MODEL_FILE)
        return file.takeIf { it.isFile && it.length() > 100_000_000L }
    }

    fun start(): Long {
        existingModel()?.let { return -1L }
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("OrielAI · Qwen3.5-0.8B")
            .setDescription("Đang tải mô hình GGUF (~563 MB)")
            .setMimeType("application/octet-stream")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, MODEL_FILE)
        val id = manager.enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DOWNLOAD_ID, id).apply()
        return id
    }

    fun status(id: Long): DownloadStatus {
        if (id <= 0L) {
            val file = existingModel()
            return if (file != null) DownloadStatus.Complete(file.absolutePath, file.length())
            else DownloadStatus.Failed("Không tìm thấy tệp mô hình hợp lệ.")
        }
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) return DownloadStatus.Failed("Không tìm thấy tác vụ tải xuống.")
            val state = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (state) {
                DownloadManager.STATUS_PENDING -> DownloadStatus.Running(downloaded, total, "Đang chờ tải")
                DownloadManager.STATUS_RUNNING -> DownloadStatus.Running(downloaded, total, "Đang tải")
                DownloadManager.STATUS_PAUSED -> DownloadStatus.Running(downloaded, total, "Tạm dừng — kiểm tra kết nối")
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = existingModel()
                    if (file != null) {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString(KEY_MODEL_PATH, file.absolutePath).apply()
                        DownloadStatus.Complete(file.absolutePath, file.length())
                    } else DownloadStatus.Failed("Tải xong nhưng tệp không đạt kiểm tra dung lượng.")
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    DownloadStatus.Failed("Tải thất bại (mã $reason). Hãy thử lại khi mạng ổn định.")
                }
                else -> DownloadStatus.Running(downloaded, total, "Đang xử lý")
            }
        }
    }
}

sealed interface DownloadStatus {
    data class Running(val downloaded: Long, val total: Long, val label: String) : DownloadStatus
    data class Complete(val path: String, val bytes: Long) : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}
