package app.genrix.android.task

import app.genrix.android.task.TurnTrust.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnTrustTest {
    private fun cap(tool: String) = Capabilities.of(tool)!!

    @Test
    fun cleanTurnAllowsEverything() {
        val trust = TurnTrust()
        listOf("whatsapp_send", "call_contact", "book_tickets", "set_alarm").forEach {
            assertEquals(it, Verdict.Allow, trust.verdict(cap(it)))
        }
        assertTrue(trust.mayLearn)
    }

    @Test
    fun afterScreenTextHighRiskNeedsTheUser() {
        val trust = TurnTrust()
        trust.after("ui_read")
        listOf("whatsapp_send", "sms_send", "call_contact", "book_tickets").forEach {
            assertEquals(it, Verdict.AskUser, trust.verdict(cap(it)))
        }
        // Low and medium risk tools carry on, so ordinary screen tasks aren't interrupted.
        listOf("ui_click", "ui_type", "set_alarm", "media_control").forEach {
            assertEquals(it, Verdict.Allow, trust.verdict(cap(it)))
        }
        assertFalse(trust.mayLearn)
    }

    @Test
    fun everyScreenReturningToolTaints() {
        TurnTrust.READS_OUTSIDE_TEXT.forEach { tool ->
            assertTrue("$tool is not a registered tool", Capabilities.of(tool) != null)
            val trust = TurnTrust().apply { after(tool) }
            assertEquals(tool, Verdict.AskUser, trust.verdict(cap("whatsapp_send")))
        }
    }

    @Test
    fun toolsWithoutOutsideTextDontTaint() {
        val trust = TurnTrust()
        listOf("set_alarm", "get_weather", "get_current_datetime", "media_control").forEach(trust::after)
        assertEquals(Verdict.Allow, trust.verdict(cap("whatsapp_send")))
    }

    @Test
    fun theUserSpeakingStartsCleanAgain() {
        val trust = TurnTrust()
        trust.after("open_app")
        trust.userSpoke()
        assertEquals(Verdict.Allow, trust.verdict(cap("call_contact")))
        assertTrue(trust.mayLearn)
    }
}
