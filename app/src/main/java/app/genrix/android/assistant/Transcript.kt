package app.genrix.android.assistant

data class Line(val id: Long, val fromUser: Boolean, val text: String)

/** Builds conversation lines from streamed transcription pieces. */
class Transcript(private val limit: Int = 60) {
    private val lines = ArrayList<Line>()
    private var nextId = 0L
    private var open = false

    @Synchronized
    fun add(fromUser: Boolean, piece: String): List<Line> {
        val last = lines.lastOrNull()
        if (open && last != null && last.fromUser == fromUser) {
            lines[lines.lastIndex] = last.copy(text = (last.text + piece).replace(SPACES, " ").trim())
        } else {
            lines += Line(nextId++, fromUser, piece.trim())
            if (lines.size > limit) lines.removeAt(0)
            open = true
        }
        return lines.toList()
    }

    /** The next piece starts a new line, even from the same side. */
    @Synchronized
    fun endTurn() {
        open = false
    }

    @Synchronized
    fun snapshot(): List<Line> = lines.toList()

    private companion object {
        val SPACES = Regex("""\s+""")
    }
}
