package app.genrix.android.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRulesTest {
    private fun wa(title: String, text: String, group: Boolean = false) =
        Seen("com.whatsapp", "WhatsApp", title, text, 0L, group)

    @Test
    fun aPersonIsNamedButTheirWordsAreNot() {
        val line = WatchRules().decide(wa("Mom", "Kab aa rahe ho?"), 0L, phoneSilent = false, inConversation = false)
        assertEquals("Mom sent a message on WhatsApp.", line)
        assertFalse(line!!.contains("Kab"))
    }

    @Test
    fun missedCallsAndScamLinksSpeakUp() {
        val call = Seen("com.samsung.android.dialer", "Phone", "Rahul", "Missed call", 0L)
        assertEquals("Missed call from Rahul.", WatchRules().decide(call, 0L, false, false))
        val scam = WatchRules().decide(wa("+91 98765 43210", "Prize! http://bit.ly/x1"), 0L, false, false)
        assertTrue(scam!!.startsWith("Careful"))
        assertFalse(scam.contains("bit.ly"))
    }

    @Test
    fun groupsOffersAndCodesStayQuiet() {
        val rules = WatchRules()
        assertNull(rules.decide(wa("Family", "Ravi: hi", group = true), 0L, false, false))
        assertNull(rules.decide(Seen("in.swiggy.android", "Swiggy", "50% OFF", "deal", 0L), 0L, false, false))
        assertNull(rules.decide(Seen("com.google.android.apps.messaging", "Messages", "HDFC", "OTP is 482913", 0L), 0L, false, false))
    }

    @Test
    fun silentPhoneOrAConversationKeepsItQuiet() {
        assertNull(WatchRules().decide(wa("Mom", "Hi"), 0L, phoneSilent = true, inConversation = false))
        assertNull(WatchRules().decide(wa("Mom", "Hi"), 0L, phoneSilent = false, inConversation = true))
    }

    @Test
    fun repeatsAreHeldBack() {
        val rules = WatchRules()
        assertTrue(rules.decide(wa("Mom", "Hi"), 0L, false, false) != null)
        // Someone else straight after: the global gap holds it back.
        assertNull(rules.decide(wa("Priya", "Hey"), 10_000L, false, false))
        // Mom again after the gap but within ten minutes: still quiet.
        assertNull(rules.decide(wa("Mom", "Hello?"), WatchRules.GAP_MS + 1, false, false))
        // Priya after the gap: speaks.
        assertTrue(rules.decide(wa("Priya", "Hey"), WatchRules.GAP_MS + 2, false, false) != null)
        // Mom after ten minutes: speaks again.
        assertTrue(rules.decide(wa("Mom", "Hello?"), WatchRules.SAME_PERSON_MS + WatchRules.GAP_MS * 2, false, false) != null)
    }
}
