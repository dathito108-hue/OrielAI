package ai.oriel.local

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Real local inference adapter backed by the pinned llama.cpp Android JNI binding.
 * The native engine is single-instance/single-threaded, so all model operations are serialized.
 */
class LlamaCppRuntime(context: Context) : LocalModelRuntime {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var engine: InferenceEngine? = null
    @Volatile private var loadedPath: String? = null

    override val isAvailable: Boolean = true

    private fun getEngine(): InferenceEngine {
        engine?.let { return it }
        return AiChat.getInferenceEngine(appContext).also { engine = it }
    }

    override suspend fun load(modelPath: String, contextTokens: Int): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                require(modelPath.isNotBlank()) { "Chưa chọn tệp mô hình." }
                val modelFile = java.io.File(modelPath)
                require(modelFile.isFile && modelFile.length() > 1024 * 1024) {
                    "Không tìm thấy tệp GGUF hợp lệ tại: $modelPath"
                }
                val inference = getEngine()
                val initial = withTimeout(120_000) {
                    inference.state.first {
                        it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
                    }
                }
                if (initial is InferenceEngine.State.Error) {
                    throw initial.exception
                }
                if (loadedPath != modelFile.absolutePath || inference.state.value !is InferenceEngine.State.ModelReady) {
                    inference.loadModel(modelFile.absolutePath)
                    loadedPath = modelFile.absolutePath
                }
            }
        }
    }

    override suspend fun generate(messages: List<ChatMessage>, profile: BehaviorProfile): Result<String> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val inference = engine ?: error("Hãy nạp mô hình trước khi trò chuyện.")
                    check(inference.state.value is InferenceEngine.State.ModelReady) {
                        "Mô hình chưa sẵn sàng: ${inference.state.value.javaClass.simpleName}"
                    }
                    inference.setSystemPrompt(profile.systemPrompt.take(8000))
                    val prompt = messages.lastOrNull { it.role == "user" }?.content
                        ?.trim()?.take(12000).orEmpty()
                    require(prompt.isNotBlank()) { "Tin nhắn trống." }
                    inference.sendUserPrompt(prompt, profile.maxTokens.coerceIn(32, 2048))
                        .toList().joinToString(separator = "")
                        .trim()
                        .also { if (it.isBlank()) error("Mô hình không sinh ra văn bản. Kiểm tra log native và định dạng GGUF.") }
                }
            }
        }

    override fun cancel() {
        // The pinned upstream API has no public generation-cancel call; cancellation is intentionally not faked.
    }

    override fun unload() {
        val current = engine ?: return
        runCatching {
            if (current.state.value is InferenceEngine.State.ModelReady ||
                current.state.value is InferenceEngine.State.Error) current.cleanUp()
        }
        loadedPath = null
    }

    override fun runtimeInfo(): String =
        "llama.cpp JNI (CPU), context 2048, batch 256; cần kiểm thử nạp/sinh trên thiết bị."
}
