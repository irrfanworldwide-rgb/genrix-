package app.genrix.android.tools

import app.genrix.android.notify.Seen
import app.genrix.android.notify.Triage
import app.genrix.android.notify.WatchRules
import app.genrix.android.tools.MailPick.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailPickTest {
    private fun contact(name: String, vararg emails: String) = Contact(name, emptyList(), emails.toList())

    @Test
    fun anAddressIsUsedAsIs() {
        assertEquals(Pick.Address("priya@example.com", "Priya@Example.com"), MailPick.choose("Priya@Example.com", emptyList()))
        assertTrue(MailPick.isAddress("a.b@c.in"))
        assertTrue(!MailPick.isAddress("priya"))
    }

    @Test
    fun oneContactWithOneAddressIsChosen() {
        assertEquals(Pick.Address("rahul@x.com", "Rahul"), MailPick.choose("Rahul", listOf(contact("Rahul", "rahul@x.com"))))
    }

    @Test
    fun neverGuessesBetweenPeopleOrAddresses() {
        val people = MailPick.choose("Rahul", listOf(contact("Rahul S", "a@x.com"), contact("Rahul K", "b@x.com")))
        assertTrue(people is Pick.Ask && people.choices == listOf("Rahul S", "Rahul K"))
        val addresses = MailPick.choose("Rahul", listOf(contact("Rahul", "work@x.com", "home@x.com")))
        assertTrue(addresses is Pick.Ask && addresses.choices == listOf("work@x.com", "home@x.com"))
        assertTrue(MailPick.choose("Nobody", emptyList()) is Pick.NotFound)
    }

    @Test
    fun emailsAreSortedAndNoReplyStaysQuiet() {
        val boss = Triage.classify(Seen("com.google.android.gm", "Gmail", "Anita Sharma", "Q3 report due Friday", 0L))
        assertEquals(Triage.Kind.Mail, boss.kind)
        assertEquals("Anita Sharma: Q3 report due Friday", boss.line)
        val bot = Triage.classify(Seen("com.google.android.gm", "Gmail", "no-reply@bank.com", "Your statement", 0L))
        assertEquals(Triage.Kind.Quiet, bot.kind)
        val promo = Triage.classify(Seen("com.google.android.gm", "Gmail", "Myntra", "Flat 50% off today", 0L))
        assertEquals(Triage.Kind.Quiet, promo.kind)
        assertTrue(Triage.summary(listOf(boss)).contains("Emails (sender: subject):\n- Anita Sharma"))
    }

    @Test
    fun theWatchDoesNotReadEmailsAloud() {
        val mail = Seen("com.google.android.gm", "Gmail", "Anita Sharma", "Q3 report", 0L)
        assertNull(WatchRules().decide(mail, 0L, phoneSilent = false, inConversation = false))
    }
}
