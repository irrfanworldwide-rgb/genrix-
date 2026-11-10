package app.genrix.android.memory

/** Something the user asked Genrix to remember. */
data class Note(val text: String, val atMillis: Long)

/** A named list of plain-language steps ("Good morning"), run with the normal tools and approvals. */
data class Routine(val name: String, val steps: List<String>)

/**
 * What may be remembered and how notes are found again. Pure, so it is unit-tested.
 * Secrets are never stored: a note that looks like a password, PIN, OTP or card number is
 * refused, because notes are sent to Gemini when recalled.
 */
object MemoryRules {
    private val SECRET_WORDS = Regex(
        """\b(password|passcode|pass word|pin|otp|cvv|upi pin|atm pin|secret|private key|seed phrase|login code)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CARD = Regex("""\b(?:\d[ -]?){13,19}\b""")
    private val STOP = setOf(
        "the", "a", "an", "my", "i", "is", "was", "where", "what", "did", "do", "to", "of", "in", "on", "at",
        "that", "this", "me", "remember", "about", "kya", "kaha", "kahan", "hai", "tha", "mera", "meri", "ka", "ki", "ke",
    )

    const val MAX_NOTES = 200
    const val MAX_LENGTH = 300
    const val MAX_ROUTINES = 20
    const val MAX_STEPS = 10

    fun looksSecret(text: String) = SECRET_WORDS.containsMatchIn(text) || CARD.containsMatchIn(text)

    /** Notes that share words with [query], best first; all notes (newest first) for an empty query. */
    fun find(query: String, notes: List<Note>, limit: Int = 5): List<Note> {
        val wanted = words(query)
        if (wanted.isEmpty()) return notes.sortedByDescending { it.atMillis }.take(limit)
        return notes
            .map { note -> note to words(note.text).count { w -> wanted.any { q -> w.startsWith(q) || q.startsWith(w) } } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Note, Int>> { it.second }.thenByDescending { it.first.atMillis })
            .take(limit)
            .map { it.first }
    }

    /** "Good Morning!" and "good morning" are the same routine. */
    fun key(name: String) = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** The routine called [name], or the one whose name contains it. */
    fun routine(name: String, all: List<Routine>): Routine? {
        val k = key(name)
        if (k.isEmpty()) return null
        return all.firstOrNull { key(it.name) == k } ?: all.firstOrNull { key(it.name).contains(k) || k.contains(key(it.name)) }
    }

    /** Built in until the user saves their own with the same name. */
    val GOOD_MORNING = Routine(
        "Good morning",
        listOf("Say the date and time", "Today's weather", "What I missed (messages, calls, emails)", "Today's calendar"),
    )

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 2 && it !in STOP }
}
