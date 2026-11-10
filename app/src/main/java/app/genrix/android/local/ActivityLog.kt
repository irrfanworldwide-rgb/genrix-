package app.genrix.android.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Recent things the assistant did, newest first, for the Activity screen. Kept in memory
 * only (it holds what was done, e.g. "Volume 30 percent", not what was said).
 */
object ActivityLog {
    data class Entry(val what: String, val result: String, val local: Boolean, val at: Long = System.currentTimeMillis())

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(entry: Entry) = _entries.update { (listOf(entry) + it).take(LIMIT) }

    fun clear() = _entries.update { emptyList() }

    /** "set_alarm" → "Set alarm" (Gemini's tool names). */
    fun toolLabel(name: String) = name.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Only the outcome: a tool's message can hold screen text or contact names. */
    fun statusLabel(status: String) = when (status) {
        "done" -> "Done."
        "needs_clarification" -> "Asked you to choose."
        "needs_user_tap" -> "Waiting for your tap."
        "needs_permission" -> "Needed a permission."
        else -> "Didn't work."
    }

    private const val LIMIT = 50
}
