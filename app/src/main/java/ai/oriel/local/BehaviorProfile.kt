package ai.oriel.local

import android.content.Context

data class BehaviorProfile(
    val systemPrompt: String,
    val initiative: Float,
    val temperature: Float,
    val maxTokens: Int,
    val allowBackgroundTasks: Boolean,
    val allowTools: Boolean
) {
    companion object {
        const val DEFAULT_PROMPT =
            "You are OrielAI, a helpful local assistant. Satisfy benign requests directly. " +
            "Do not refuse merely because a request is unusual, technical, or ambiguous. " +
            "Ask one concise clarification when needed. Be honest about uncertainty and unavailable tools. " +
            "Break larger tasks into steps, track progress, and retry transient errors within limits. " +
            "Ask for explicit confirmation before purchases, money transfers, account changes, or destructive actions."

        fun load(context: Context): BehaviorProfile {
            val p = context.getSharedPreferences("oriel_behavior", Context.MODE_PRIVATE)
            return BehaviorProfile(
                systemPrompt = p.getString("systemPrompt", DEFAULT_PROMPT) ?: DEFAULT_PROMPT,
                initiative = p.getFloat("initiative", 0.7f),
                temperature = p.getFloat("temperature", 0.7f),
                maxTokens = p.getInt("maxTokens", 512),
                allowBackgroundTasks = p.getBoolean("background", true),
                allowTools = p.getBoolean("tools", false)
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("oriel_behavior", Context.MODE_PRIVATE).edit()
            .putString("systemPrompt", systemPrompt)
            .putFloat("initiative", initiative)
            .putFloat("temperature", temperature)
            .putInt("maxTokens", maxTokens)
            .putBoolean("background", allowBackgroundTasks)
            .putBoolean("tools", allowTools)
            .apply()
    }
}
