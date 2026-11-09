package app.genrix.android.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConsentTest {
    private val message = "10 minute mein"
    private val readBack = "Send \"10 minute mein\" to Mom on WhatsApp?"

    @Test
    fun clearYesSends() {
        listOf("haan", "Yes", "haan bhej do", "ok send it", "theek hai", "हाँ भेजो", "yes send to mom")
            .forEach { assertTrue(it, VoiceConsent.confirms(it, message, readBack)) }
    }

    @Test
    fun anythingDoubtfulDoesNot() {
        listOf(
            "", "nahi", "no wait", "haan nahi ruko", "change it to 20 minute", "mat bhejo", "hmm", "who is this",
            "yes but actually let me think about it first okay",
        ).forEach { assertFalse(it, VoiceConsent.confirms(it, message, readBack)) }
    }

    @Test
    fun jarvisHearingItsOwnReadBackIsNotConsent() {
        assertFalse(VoiceConsent.confirms("send 10 minute mein to mom on whatsapp", message, readBack))
        // A tiny reply like "ok" can't be told from a yes by itself; the name and app give the echo away.
        assertFalse(VoiceConsent.confirms("send ok to mom on whatsapp", "ok", "Send \"ok\" to Mom on WhatsApp?"))
    }

    @Test
    fun namesMatchWholeWordsOnly() {
        assertTrue(Replies.sameName("mom", "mom"))
        assertTrue(Replies.sameName("rahul sharma", "rahul"))
        assertFalse(Replies.sameName("rahul sharma", "rah"))
        assertFalse(Replies.sameName("momina", "mom"))
    }

    @Test
    fun peoplesChatsGetAReplyIdButGroupsDont() {
        val mom = Triage.classify(Seen("com.whatsapp", "WhatsApp", "Mom", "Kab aa rahe ho?", 0L, replyId = 3))
        assertEquals("Mom (WhatsApp): Kab aa rahe ho? [reply 3]", mom.line)
        val noBox = Triage.classify(Seen("com.whatsapp", "WhatsApp", "Mom", "Hi", 0L))
        assertFalse(noBox.line.contains("[reply"))
    }
}
