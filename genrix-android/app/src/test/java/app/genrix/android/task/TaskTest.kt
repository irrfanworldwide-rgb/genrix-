package app.genrix.android.task

import app.genrix.android.control.ScreenElement
import app.genrix.android.tools.AppTools
import app.genrix.android.tools.BookingRequest
import app.genrix.android.tools.BookingTools
import app.genrix.android.tools.CalendarTools
import app.genrix.android.tools.ClockTools
import app.genrix.android.tools.CommsTools
import app.genrix.android.tools.DeviceTools
import app.genrix.android.tools.InfoTools
import app.genrix.android.tools.MailTools
import app.genrix.android.tools.MediaTools
import app.genrix.android.tools.MemoryTools
import app.genrix.android.tools.NotifyTools
import app.genrix.android.tools.PcTools
import app.genrix.android.tools.ReminderTools
import app.genrix.android.tools.ScreenTools
import app.genrix.android.tools.SendCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskTest {
    @Before
    fun reset() = TaskTracker.clear()

    @Test
    fun everyToolIsInTheRegistry() {
        val tools = (
            CommsTools.all() + ClockTools.all() + ReminderTools.all() + MediaTools.all() + InfoTools.all() +
                ScreenTools.all() + AppTools.all() + BookingTools.all() + DeviceTools.all() + NotifyTools.all() + CalendarTools.all() + MailTools.all() + MemoryTools.all() + PcTools.all()
            ).map { it.name }
        assertEquals(tools.toSet(), Capabilities.tools)
        assertNull(Capabilities.of("pay_now"))
    }

    @Test
    fun sendingAndPayingAreLeftToTheUser() {
        listOf("whatsapp_send", "sms_send", "call_contact", "book_tickets").forEach {
            val cap = Capabilities.of(it)!!
            assertTrue(it, cap.risk >= Risk.High)
            assertEquals(it, Finish.User, cap.finish)
        }
        assertEquals(Risk.Financial, Capabilities.of("book_tickets")!!.risk)
    }

    @Test
    fun whatsappEndsWaitingForTheUserNotDone() {
        TaskTracker.heard()
        TaskTracker.step("whatsapp_send", "WhatsApp message")
        assertEquals(TaskState.Executing, TaskTracker.current.value!!.state)
        TaskTracker.result("whatsapp_send", "done", "Tap Send")
        assertEquals(TaskState.WaitingForYou, TaskTracker.current.value!!.state)
        TaskTracker.verified(true, "Sent")
        assertEquals(TaskState.Completed, TaskTracker.current.value!!.state)
    }

    @Test
    fun stepsOfOneRequestShareATask() {
        TaskTracker.heard()
        TaskTracker.step("open_app", "Open an app")
        TaskTracker.result("open_app", "done")
        TaskTracker.step("ui_click", "Tap on screen")
        TaskTracker.confirming("Tap Send")
        assertEquals(TaskState.AwaitingConfirmation, TaskTracker.current.value!!.state)
        TaskTracker.confirmed(false)
        val task = TaskTracker.current.value!!
        assertEquals(TaskState.Failed, task.state)
        assertEquals(2, task.steps.size)
        TaskTracker.heard()
        TaskTracker.step("get_weather", "Check the weather")
        assertEquals(1, TaskTracker.current.value!!.steps.size)
    }

    @Test
    fun questionsAndFailures() {
        TaskTracker.heard()
        TaskTracker.step("whatsapp_send", "WhatsApp message")
        TaskTracker.result("whatsapp_send", "needs_clarification")
        assertEquals(TaskState.Planning, TaskTracker.current.value!!.state)
        TaskTracker.step("whatsapp_send", "WhatsApp message")
        TaskTracker.result("whatsapp_send", "failed")
        assertEquals(TaskState.Failed, TaskTracker.current.value!!.state)
        assertEquals(false, TaskTracker.current.value!!.steps.last().done)
    }

    @Test
    fun geminiUsesTheSamePhoneControls() {
        assertEquals(app.genrix.android.local.LocalCommand.Torch(false), DeviceTools.command("torch_off", -1))
        assertEquals(app.genrix.android.local.LocalCommand.SetVolume(30), DeviceTools.command("volume_set", 30))
        assertNull(DeviceTools.command("volume_set", -1))
        assertNull(DeviceTools.command("airplane_mode", -1))
    }

    @Test
    fun duplicateGuard() {
        var now = 0L
        val guard = DuplicateGuard(windowMs = 1_000) { now }
        assertTrue(guard.allow("whatsapp_send", "a"))
        assertFalse(guard.allow("whatsapp_send", "a"))
        assertTrue(guard.allow("whatsapp_send", "b"))
        guard.forget("whatsapp_send", "a")
        assertTrue(guard.allow("whatsapp_send", "a"))
        now = 2_000
        assertTrue(guard.allow("whatsapp_send", "b"))
    }

    @Test
    fun sentOnlyWhenInTheChatAndNotStillTyped() {
        val msg = "I will arrive at 7 PM"
        val typed = listOf(ScreenElement(1, "field", "Message", msg), ScreenElement(2, "button", "Send"))
        assertFalse(SendCheck.sent(typed, msg))
        val sent = listOf(ScreenElement(1, "field", "Message", ""), ScreenElement(2, "button", "I will arrive at 7 PM 19:02"))
        assertTrue(SendCheck.sent(sent, msg))
        assertFalse(SendCheck.sent(listOf(ScreenElement(1, "field", "Message", "")), msg))
    }

    @Test
    fun bookingTextUsesOnlyWhatWasSaid() {
        val r = BookingRequest("Pushpa 3", "Ahmedabad", "tomorrow evening", 4)
        assertEquals("Pushpa 3 movie tickets Ahmedabad tomorrow evening", r.query())
        assertEquals("4 tickets, tomorrow evening, in Ahmedabad", r.summary())
        assertEquals("the show and seats", BookingRequest("Pushpa 3").summary())
    }

    @Test
    fun bookingPlanAsksForWhatIsMissingAndStopsAtPayment() {
        val full = BookingRequest("Pushpa 3", "Ahmedabad", "tomorrow evening", 4).plan()
        assertTrue(full.contains("city is Ahmedabad") && full.contains("pick tomorrow evening") && full.contains("Choose 4 tickets"))
        val bare = BookingRequest("Pushpa 3").plan()
        assertTrue(bare.contains("Ask how many tickets") && bare.contains("keep today"))
        listOf(full, bare).forEach { assertTrue(it.contains("Stop at the payment step") && it.contains("Never say booked")) }
    }
}
