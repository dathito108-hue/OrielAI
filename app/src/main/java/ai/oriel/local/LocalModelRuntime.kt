package ai.oriel.local

/**
 * Runtime contract for the future llama.cpp JNI integration.
 * This scaffold deliberately does not return fabricated model responses.
 */
interface LocalModelRuntime {
    val isAvailable: Boolean
    suspend fun load(modelPath: String, contextTokens: Int): Result<Unit>
    suspend fun generate(messages: List<ChatMessage>, profile: BehaviorProfile): Result<String>
    fun cancel()
    fun unload()
    fun runtimeInfo(): String
}

data class ChatMessage(val role: String, val content: String)

class RuntimeNotIntegrated : LocalModelRuntime {
    override val isAvailable: Boolean = false
    override suspend fun load(modelPath: String, contextTokens: Int): Result<Unit> =
        Result.failure(IllegalStateException("llama.cpp JNI runtime has not been integrated yet"))
    override suspend fun generate(messages: List<ChatMessage>, profile: BehaviorProfile): Result<String> =
        Result.failure(IllegalStateException("Local inference is unavailable until the native runtime is integrated"))
    override fun cancel() = Unit
    override fun unload() = Unit
    override fun runtimeInfo() = "Chưa tích hợp llama.cpp JNI — chưa thể suy luận thật"
}
