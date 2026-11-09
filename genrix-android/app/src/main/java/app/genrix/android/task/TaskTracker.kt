package app.genrix.android.task

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class TaskState { Understanding, Planning, AwaitingConfirmation, Executing, WaitingForYou, Completed, Failed }

/** [done]: null while it runs, then true or false. */
data class Step(val label: String, val done: Boolean? = null)

data class Task(
    val id: String,
    val title: String,
    val risk: Risk,
    val state: TaskState,
    val steps: List<Step>,
    /** What is true now, in plain words ("Opened in WhatsApp; you tap Send"). Never a guess. */
    val detail: String = "",
    val at: Long = System.currentTimeMillis(),
)

/**
 * The task the assistant is working on, for the Home screen. Built from what actually
 * happened (tool calls, their results, the Allow card), never from what the model says.
 */
object TaskTracker {
    private val _current = MutableStateFlow<Task?>(null)
    val current: StateFlow<Task?> = _current.asStateFlow()
    @Volatile private var turn = 0
    @Volatile private var taskTurn = -1

    /** The user spoke: a new request may begin. */
    fun heard() {
        turn++
    }

    fun step(tool: String, label: String) = _current.update { task ->
        val cap = Capabilities.of(tool)
        // One task per request: every tool call after the user spoke adds a step to it.
        if (task == null || taskTurn != turn) {
            taskTurn = turn
            Task(UUID.randomUUID().toString(), cap?.title ?: label, cap?.risk ?: Risk.Medium, TaskState.Executing, listOf(Step(label)))
        } else {
            task.copy(state = TaskState.Executing, steps = task.steps + Step(label), risk = maxOf(task.risk, cap?.risk ?: Risk.Low))
        }
    }

    /** The last step's outcome, from the tool's status. */
    fun result(tool: String, status: String, detail: String = "") = _current.update { task ->
        task ?: return@update null
        val ok = status in setOf("done", "needs_user_tap")
        val steps = task.steps.dropLast(1) + task.steps.last().copy(done = ok || status == "needs_clarification")
        val userFinishes = Capabilities.of(tool)?.finish == Finish.User
        val state = when {
            status == "needs_clarification" || status == "needs_permission" -> TaskState.Planning
            !ok -> TaskState.Failed
            status == "needs_user_tap" || userFinishes -> TaskState.WaitingForYou
            else -> TaskState.Completed
        }
        task.copy(state = state, steps = steps, detail = detail)
    }

    fun confirming(what: String) = _current.update { it?.copy(state = TaskState.AwaitingConfirmation, detail = what) }

    fun confirmed(allowed: Boolean) = _current.update {
        it?.copy(state = if (allowed) TaskState.Executing else TaskState.Failed, detail = if (allowed) "" else "You cancelled it.")
    }

    /** Something the phone checked afterwards, e.g. the message really appears in the chat. */
    fun verified(done: Boolean, detail: String) = _current.update {
        it?.copy(state = if (done) TaskState.Completed else TaskState.WaitingForYou, detail = detail)
    }

    fun clear() {
        _current.value = null
    }
}

/**
 * Stops the same high-risk action running twice by mistake (a reconnect replaying a call,
 * the model repeating itself): an identical one within [windowMs] is refused.
 */
class DuplicateGuard(private val windowMs: Long = 90_000L, private val clock: () -> Long = System::currentTimeMillis) {
    private val recent = mutableMapOf<String, Long>()

    @Synchronized
    fun allow(tool: String, args: String): Boolean {
        val now = clock()
        recent.entries.removeAll { now - it.value > windowMs }
        val key = "$tool|$args"
        if (key in recent) return false
        recent[key] = now
        return true
    }

    /** The action failed or was cancelled, so trying again is fine. */
    @Synchronized
    fun forget(tool: String, args: String) {
        recent.remove("$tool|$args")
    }
}
