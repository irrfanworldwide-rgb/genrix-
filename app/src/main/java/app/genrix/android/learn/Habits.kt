package app.genrix.android.learn

import android.content.Context
import app.genrix.android.Diag
import app.genrix.android.local.LocalCommand
import app.genrix.android.local.LocalRouter
import app.genrix.android.local.MediaKey
import org.json.JSONObject
import java.io.File

/**
 * What Genrix learns about how this user uses it, kept only on the phone (app storage, no
 * backup, never sent except as the short summary Gemini gets when a conversation starts):
 *
 * - how often each phone command is used, and at what hour, for Home's quick actions;
 * - phrases the local router missed but Gemini then turned into a phone command, so the
 *   same words run locally next time.
 *
 * Contact choices are never learned: a recipient is always asked, never guessed.
 */
class Habits(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val counts = mutableMapOf<String, Int>()
    private val hours = mutableMapOf<String, IntArray>()
    private val phrases = mutableMapOf<String, String>()
    private var pending: Pair<String, Long>? = null

    init {
        load()
    }

    @Synchronized
    fun record(command: LocalCommand, hour: Int) {
        val key = HabitKeys.key(command) ?: return
        counts[key] = (counts[key] ?: 0) + 1
        hours.getOrPut(key) { IntArray(24) }[hour.coerceIn(0, 23)]++
        save()
    }

    /** The phone heard [text] but didn't know it; Gemini may now show what it meant. */
    @Synchronized
    fun expect(text: String) {
        pending = LocalRouter.normalize(text).takeIf { it.isNotEmpty() && it.split(" ").size <= MAX_WORDS }?.let { it to clock() }
    }

    /** Gemini's first phone command after [expect]: those words mean this command now. */
    @Synchronized
    fun learn(command: LocalCommand) {
        val (phrase, at) = pending ?: return
        pending = null
        val key = HabitKeys.key(command) ?: return
        if (clock() - at > LEARN_WINDOW_MS) return
        phrases[phrase] = key
        if (phrases.size > MAX_PHRASES) phrases.remove(phrases.keys.first())
        Diag.i("learn: new phrase for ${key.substringBefore(':')}")
        save()
    }

    @Synchronized
    fun lookup(alternatives: List<String>): LocalCommand? =
        alternatives.firstNotNullOfOrNull { phrases[LocalRouter.normalize(it)]?.let(HabitKeys::command) }

    /** Most used first; commands used fewer than [min] times are left out. */
    @Synchronized
    fun top(n: Int, min: Int = 3): List<LocalCommand> =
        counts.filter { it.value >= min }.entries.sortedByDescending { it.value }.mapNotNull { HabitKeys.command(it.key) }.take(n)

    /** A few lines for Gemini: what this user tends to do, and at this hour. */
    @Synchronized
    fun summary(hour: Int): String {
        if (counts.isEmpty()) return ""
        val often = counts.entries.sortedByDescending { it.value }.take(5).joinToString(", ") { "${HabitKeys.describe(it.key)} (${it.value}×)" }
        val now = hours.entries.filter { it.value[hour.coerceIn(0, 23)] >= 2 }.joinToString(", ") { HabitKeys.describe(it.key) }
        return "Often asks for: $often." + if (now.isNotEmpty()) " Around this hour usually: $now." else ""
    }

    @Synchronized
    fun learnedPhrases(): Int = phrases.size

    @Synchronized
    fun forget() {
        counts.clear()
        hours.clear()
        phrases.clear()
        pending = null
        file.delete()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            root.optJSONObject("counts")?.let { o -> o.keys().forEach { counts[it] = o.getInt(it) } }
            root.optJSONObject("hours")?.let { o ->
                o.keys().forEach { k -> val a = o.getJSONArray(k); hours[k] = IntArray(24) { a.optInt(it) } }
            }
            root.optJSONObject("phrases")?.let { o -> o.keys().forEach { phrases[it] = o.getString(it) } }
        }.onFailure { Diag.w("learn: couldn't read what was learned (${it.javaClass.simpleName})") }
    }

    private fun save() {
        val root = JSONObject()
            .put("counts", JSONObject(counts as Map<*, *>))
            .put("hours", JSONObject().apply { hours.forEach { (k, v) -> put(k, org.json.JSONArray(v.toList())) } })
            .put("phrases", JSONObject(phrases as Map<*, *>))
        runCatching { file.writeText(root.toString()) }
    }

    companion object {
        private const val LEARN_WINDOW_MS = 60_000L
        private const val MAX_PHRASES = 200
        private const val MAX_WORDS = 8

        @Volatile private var instance: Habits? = null

        fun get(context: Context): Habits = instance ?: synchronized(this) {
            instance ?: Habits(File(context.filesDir, "habits.json")).also { instance = it }
        }
    }
}

/** Phone commands as short keys ("torch:on", "app:youtube") and back. Pure, unit-tested. */
object HabitKeys {
    fun key(command: LocalCommand): String? = when (command) {
        is LocalCommand.SetVolume -> "volume:${command.percent}"
        is LocalCommand.StepVolume -> if (command.up) "volume:up" else "volume:down"
        is LocalCommand.Mute -> if (command.on) "mute:on" else "mute:off"
        is LocalCommand.Media -> "media:${command.key.name.lowercase()}"
        is LocalCommand.Torch -> if (command.on) "torch:on" else "torch:off"
        LocalCommand.Battery -> "battery"
        LocalCommand.Time -> "time"
        LocalCommand.Date -> "date"
        is LocalCommand.Timer -> "timer:${command.seconds}"
        is LocalCommand.OpenApp -> "app:${command.name.lowercase()}"
        is LocalCommand.Alarm, LocalCommand.Stop -> null // one-off times; nothing to learn
    }

    fun command(key: String): LocalCommand? {
        val (kind, value) = key.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when (kind) {
            "volume" -> when (value) {
                "up" -> LocalCommand.StepVolume(true)
                "down" -> LocalCommand.StepVolume(false)
                else -> value.toIntOrNull()?.takeIf { it in 0..100 }?.let { LocalCommand.SetVolume(it) }
            }
            "mute" -> LocalCommand.Mute(value == "on")
            "media" -> MediaKey.entries.firstOrNull { it.name.lowercase() == value }?.let { LocalCommand.Media(it) }
            "torch" -> LocalCommand.Torch(value == "on")
            "battery" -> LocalCommand.Battery
            "time" -> LocalCommand.Time
            "date" -> LocalCommand.Date
            "timer" -> value.toIntOrNull()?.takeIf { it in 1..86_400 }?.let { LocalCommand.Timer(it) }
            "app" -> value.takeIf { it.isNotEmpty() }?.let { LocalCommand.OpenApp(it) }
            else -> null
        }
    }

    /** Short words for a key, for Gemini's summary and Home's buttons. */
    fun describe(key: String): String {
        val (kind, value) = key.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when (kind) {
            "volume" -> if (value.toIntOrNull() != null) "volume $value%" else "volume $value"
            "mute" -> if (value == "on") "mute" else "unmute"
            "media" -> "music $value"
            "torch" -> "torch $value"
            "timer" -> "${(value.toIntOrNull() ?: 0) / 60} min timer"
            "app" -> "open ${value.replaceFirstChar { it.uppercase() }}"
            else -> kind
        }
    }
}
