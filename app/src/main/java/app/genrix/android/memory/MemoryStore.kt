package app.genrix.android.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notes and routines in Genrix's private storage on this phone. Never backed up or moved to
 * another device (see data_extraction_rules.xml); a note reaches Gemini only when recalled.
 */
class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("memory", Context.MODE_PRIVATE)

    fun notes(): List<Note> = array(NOTES).let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Note(it.optString("t"), it.optLong("at")) } }
    }

    fun addNote(text: String) {
        val kept = (notes() + Note(text, System.currentTimeMillis())).takeLast(MemoryRules.MAX_NOTES)
        saveNotes(kept)
    }

    /** Removes the notes matching [query] (all of them for "all"); returns how many. */
    fun forget(query: String): Int {
        val all = notes()
        val gone = if (query.trim().equals("all", ignoreCase = true)) all else MemoryRules.find(query, all, limit = 1)
        if (gone.isNotEmpty()) saveNotes(all - gone.toSet())
        return gone.size
    }

    fun routines(): List<Routine> {
        val a = array(ROUTINES)
        val saved = (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { o ->
                val steps = o.optJSONArray("steps") ?: JSONArray()
                Routine(o.optString("name"), (0 until steps.length()).map { steps.optString(it) })
            }
        }
        val builtIn = MemoryRules.GOOD_MORNING.takeUnless { gm -> saved.any { MemoryRules.key(it.name) == MemoryRules.key(gm.name) } }
        return listOfNotNull(builtIn) + saved
    }

    /** Saves or replaces the routine with the same name. */
    fun saveRoutine(routine: Routine) {
        val others = savedRoutines().filter { MemoryRules.key(it.name) != MemoryRules.key(routine.name) }
        writeRoutines((others + routine).takeLast(MemoryRules.MAX_ROUTINES))
    }

    fun deleteRoutine(name: String) = writeRoutines(savedRoutines().filter { MemoryRules.key(it.name) != MemoryRules.key(name) })

    fun clearNotes() = saveNotes(emptyList())

    private fun savedRoutines() = routines().filter { it !== MemoryRules.GOOD_MORNING }

    private fun saveNotes(notes: List<Note>) = prefs.edit().putString(
        NOTES,
        JSONArray(notes.map { JSONObject().put("t", it.text).put("at", it.atMillis) }).toString(),
    ).apply()

    private fun writeRoutines(routines: List<Routine>) = prefs.edit().putString(
        ROUTINES,
        JSONArray(routines.map { JSONObject().put("name", it.name).put("steps", JSONArray(it.steps)) }).toString(),
    ).apply()

    private fun array(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())

    private companion object {
        const val NOTES = "notes"
        const val ROUTINES = "routines"
    }
}
