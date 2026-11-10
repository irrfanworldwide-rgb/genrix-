package app.genrix.android.notify

/**
 * When "watch my messages" speaks up, biased to silence: only a person's message or missed
 * call, or a link from an unknown number; never the same person twice within [SAME_PERSON_MS],
 * never twice within [GAP_MS], never on a silent phone or while the user is already talking to
 * Genrix. It says who and where, never what: the words stay on screen for the user alone.
 * Pure, so it is unit-tested.
 */
class WatchRules {
    private val lastByPerson = mutableMapOf<String, Long>()
    private var lastSpoke = NEVER

    /** What to say about [n] at [now], or null to stay quiet. */
    fun decide(n: Seen, now: Long, phoneSilent: Boolean, inConversation: Boolean): String? {
        val item = Triage.classify(n)
        if (item.kind != Triage.Kind.Needs && item.kind != Triage.Kind.Suspicious) return null
        if (phoneSilent || inConversation) return null
        val who = n.title.ifBlank { n.app }.trim()
        val key = "${n.packageName}|$who"
        if (now - (lastByPerson[key] ?: NEVER) < SAME_PERSON_MS) return null
        if (now - lastSpoke < GAP_MS) return null
        lastByPerson[key] = now
        lastSpoke = now
        return when (item.kind) {
            Triage.Kind.Suspicious -> "Careful: a link from an unknown number on ${n.app}. Don't open it."
            else -> if (n.category == "missed_call" || n.text.contains("missed call", ignoreCase = true)) {
                "Missed call from $who."
            } else {
                "$who sent a message on ${n.app}."
            }
        }
    }

    companion object {
        const val SAME_PERSON_MS = 10 * 60_000L
        const val GAP_MS = 45_000L
        private const val NEVER = Long.MIN_VALUE / 2 // far enough back that any gap has passed, without overflow
    }
}
