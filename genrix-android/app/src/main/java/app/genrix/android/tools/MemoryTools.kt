package app.genrix.android.tools

import app.genrix.android.Diag
import app.genrix.android.memory.MemoryRules
import app.genrix.android.memory.MemoryStore
import app.genrix.android.memory.Routine
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Memory ("remember I parked on level 2") and routines ("Good morning"), kept on this phone.
 * Saving or changing either is held for the user's Allow after other people's text was read
 * in the turn (TurnTrust), so a message can't plant a memory or a routine.
 */
object MemoryTools {
    fun all(): List<PhoneTool> = listOf(Remember, Recall, Forget, RoutineSave, RoutineRun)

    private object Remember : PhoneTool {
        override val name = "remember"

        override fun declaration() = Schema.function(
            name,
            "Saves a short note the user asked to remember (where they parked, a size, a name). Never passwords, PINs, OTPs or card numbers.",
            Schema.string("note", "The note in the user's words, short and self-contained.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val note = args.text("note").take(MemoryRules.MAX_LENGTH).ifEmpty { return Results.failed("empty", "Ask what to remember.") }
            if (MemoryRules.looksSecret(note)) {
                return Results.failed("secret", "That looks like a password, PIN, code or card number. Say it isn't kept, for the user's safety.")
            }
            MemoryStore(host.context).addNote(note)
            Diag.i("memory: note saved")
            return Results.done("Remembered on this phone.")
        }
    }

    private object Recall : PhoneTool {
        override val name = "recall"

        override fun declaration() = Schema.function(
            name,
            "Finds notes the user asked to remember ('where did I park?', 'what's my shoe size?'). Empty query lists the latest.",
            Schema.string("query", "A few key words, e.g. parked."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val found = MemoryRules.find(args.text("query"), MemoryStore(host.context).notes())
            if (found.isEmpty()) return Results.done("Nothing remembered about that.")
            return Results.done(found.joinToString("\n") { "- ${it.text} (saved ${DAY.format(Instant.ofEpochMilli(it.atMillis).atZone(ZoneId.systemDefault()))})" })
        }
    }

    private object Forget : PhoneTool {
        override val name = "forget"

        override fun declaration() = Schema.function(
            name,
            "Deletes the remembered note that best matches, or every note for 'all'.",
            Schema.string("query", "Key words of the note, or all.", required = true),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val gone = MemoryStore(host.context).forget(args.text("query"))
            return if (gone == 0) Results.done("No note matched; nothing deleted.") else Results.done("Deleted $gone note(s).")
        }
    }

    private object RoutineSave : PhoneTool {
        override val name = "routine_save"

        override fun declaration() = Schema.function(
            name,
            "Saves a routine: a name and plain-language steps, run later with routine_run. Same name replaces it.",
            Schema.string("name", "e.g. Good night", required = true),
            Schema.strings("steps", "Steps in order, each a short request, e.g. \"Set an alarm for 6:30\"."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val name = args.text("name").take(40).ifEmpty { return Results.failed("no_name", "Ask what to call the routine.") }
            val list = args.optJSONArray("steps")
            val steps = (0 until (list?.length() ?: 0)).map { list!!.optString(it).trim().take(120) }.filter { it.isNotEmpty() }
            if (steps.isEmpty()) return Results.failed("no_steps", "Ask what the routine should do, step by step.")
            if (steps.size > MemoryRules.MAX_STEPS) return Results.failed("too_long", "At most ${MemoryRules.MAX_STEPS} steps.")
            if (steps.any(MemoryRules::looksSecret)) return Results.failed("secret", "Routines can't hold passwords, PINs, codes or card numbers.")
            MemoryStore(host.context).saveRoutine(Routine(name, steps))
            return Results.done("Saved \"$name\" with ${steps.size} steps. Say \"$name\" to run it.")
        }
    }

    private object RoutineRun : PhoneTool {
        override val name = "routine_run"

        override fun declaration() = Schema.function(
            name,
            "Gets a routine's steps to carry out now, in order, with the normal tools (\"good morning\"). Empty name lists them.",
            Schema.string("name", "The routine's name."),
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val all = MemoryStore(host.context).routines()
            val routine = MemoryRules.routine(args.text("name"), all)
                ?: return Results.done("Routines: " + all.joinToString { it.name } + ". Ask which one, or offer to make a new one.")
            return Results.done(
                "Do these steps of \"${routine.name}\" now, in order, with the tools; risky ones still need the user's OK. " +
                    "Then give one short summary.\n" + routine.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
            )
        }
    }

    private val DAY = DateTimeFormatter.ofPattern("d MMM")
}
