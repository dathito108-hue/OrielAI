package ai.oriel.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class OrielTaskState { QUEUED, RUNNING, WAITING_FOR_MODEL, COMPLETED, FAILED, CANCELLED }

data class OrielTask(
    val id: String,
    val goal: String,
    val state: OrielTaskState,
    val steps: List<String>,
    val completedSteps: Int,
    val result: String,
    val error: String,
    val updatedAt: Long
)

class TaskStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("oriel_tasks_v1", Context.MODE_PRIVATE)

    @Synchronized fun all(): List<OrielTask> {
        val raw = prefs.getString("tasks", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val stepArray = o.optJSONArray("steps") ?: JSONArray()
                OrielTask(
                    id = o.getString("id"),
                    goal = o.getString("goal"),
                    state = runCatching { OrielTaskState.valueOf(o.optString("state", "QUEUED")) }.getOrDefault(OrielTaskState.QUEUED),
                    steps = (0 until stepArray.length()).map { stepArray.getString(it) },
                    completedSteps = o.optInt("completedSteps", 0).coerceAtLeast(0),
                    result = o.optString("result", ""),
                    error = o.optString("error", ""),
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    @Synchronized fun create(goal: String): OrielTask {
        val task = OrielTask(UUID.randomUUID().toString(), goal.trim(), OrielTaskState.QUEUED, emptyList(), 0, "", "", System.currentTimeMillis())
        save(task)
        return task
    }

    @Synchronized fun save(task: OrielTask) {
        val current = all().filterNot { it.id == task.id }.toMutableList()
        current.add(task.copy(updatedAt = System.currentTimeMillis()))
        val array = JSONArray()
        current.sortedByDescending { it.updatedAt }.take(50).forEach { t ->
            val steps = JSONArray()
            t.steps.forEach { steps.put(it) }
            array.put(JSONObject().apply {
                put("id", t.id); put("goal", t.goal); put("state", t.state.name)
                put("steps", steps); put("completedSteps", t.completedSteps)
                put("result", t.result); put("error", t.error); put("updatedAt", t.updatedAt)
            })
        }
        prefs.edit().putString("tasks", array.toString()).apply()
    }

    @Synchronized fun delete(id: String) {
        val array = JSONArray()
        all().filterNot { it.id == id }.forEach { t ->
            val steps = JSONArray(); t.steps.forEach { steps.put(it) }
            array.put(JSONObject().apply {
                put("id", t.id); put("goal", t.goal); put("state", t.state.name)
                put("steps", steps); put("completedSteps", t.completedSteps)
                put("result", t.result); put("error", t.error); put("updatedAt", t.updatedAt)
            })
        }
        prefs.edit().putString("tasks", array.toString()).apply()
    }
}
