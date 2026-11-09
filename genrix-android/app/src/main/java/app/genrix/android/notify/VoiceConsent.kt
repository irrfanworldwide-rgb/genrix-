package app.genrix.android.notify

/**
 * Whether what the user said after Genrix read a reply back is a clear "yes, send it". The
 * model never decides this: the words come from the user's own speech (or typing) heard
 * after the read-back. Anything doubtful counts as no. Pure, so it is unit-tested.
 */
object VoiceConsent {
    private val NO = Regex(
        """\b(no|nope|not|don't|dont|stop|cancel|wait|wrong|change|nahi|nahin|na|mat|ruko|ruk|galat|badlo)\b|नहीं|मत|रुको|रुक""",
        RegexOption.IGNORE_CASE,
    )
    private val YES = Regex(
        """\b(yes|yeah|yep|yup|ok|okay|sure|send|send it|go ahead|correct|right|haan|han|haa|ha|ji|bhej|bhejo|bhej do|kar do|theek|thik)\b|हाँ|हां|हा|जी|भेजो|भेज दो|ठीक""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * [heard]: the user's words since the read-back. [message]: the reply being confirmed.
     * [readBack]: what Genrix said; repeating its words (the name, the app) means echo, not consent.
     */
    fun confirms(heard: String, message: String, readBack: String = ""): Boolean {
        val said = heard.trim()
        if (said.isEmpty() || said.split(Regex("\\s+")).size > MAX_WORDS) return false
        if (NO.containsMatchIn(said)) return false
        // Genrix's own read-back leaking into the mic would contain the message: not the user.
        val msg = message.lowercase().trim()
        if (msg.length >= 4 && said.lowercase().contains(msg)) return false
        val echoed = words(said).count { it in words(readBack) - setOf("send") }
        if (echoed >= 2) return false
        return YES.containsMatchIn(said)
    }

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()

    private const val MAX_WORDS = 8
}
