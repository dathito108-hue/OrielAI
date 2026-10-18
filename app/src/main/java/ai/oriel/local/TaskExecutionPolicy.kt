package ai.oriel.local

enum class TaskState { PLANNED, RUNNING, WAITING_FOR_CONFIRMATION, RETRYING, COMPLETED, FAILED, CANCELLED }

data class PlannedStep(
    val id: String,
    val title: String,
    val toolName: String? = null,
    val state: TaskState = TaskState.PLANNED,
    val attempts: Int = 0,
    val resultSummary: String? = null
)

data class TaskPlan(
    val id: String,
    val goal: String,
    val steps: List<PlannedStep>,
    val createdAtEpochMs: Long,
    val maxRetriesPerStep: Int = 2
)

enum class ToolRisk { READ_ONLY, REVERSIBLE, CONSEQUENTIAL, DESTRUCTIVE }

data class ToolDeclaration(
    val name: String,
    val description: String,
    val risk: ToolRisk,
    val enabledByUser: Boolean,
    val timeoutMs: Long = 15_000L
)

object TaskExecutionPolicy {
    /** A tool can run only if it is explicitly registered and enabled by the user. */
    fun mayInvoke(tool: ToolDeclaration): Boolean =
        tool.enabledByUser && tool.name.isNotBlank() && tool.timeoutMs in 1L..120_000L

    /** Consequential and destructive actions always require per-action confirmation. */
    fun requiresConfirmation(tool: ToolDeclaration): Boolean =
        tool.risk == ToolRisk.CONSEQUENTIAL || tool.risk == ToolRisk.DESTRUCTIVE

    /** Retry only transient, non-consequential failures and enforce a hard attempt bound. */
    fun mayRetry(step: PlannedStep, plan: TaskPlan, transientFailure: Boolean, actionIsConsequential: Boolean): Boolean =
        transientFailure && !actionIsConsequential && step.attempts < plan.maxRetriesPerStep.coerceIn(0, 3)
}

/*
 * This is a policy/data layer only. A persistent WorkManager runner, tool registry,
 * user-visible approval UI and real runtime callbacks must be added before execution.
 * Never execute a model-generated tool call directly without validating it here.
 */
