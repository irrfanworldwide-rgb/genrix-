package app.genrix.android.local

import app.genrix.android.local.LocalCommand.Alarm
import app.genrix.android.local.LocalCommand.Media
import app.genrix.android.local.LocalCommand.Mute
import app.genrix.android.local.LocalCommand.OpenApp
import app.genrix.android.local.LocalCommand.SetVolume
import app.genrix.android.local.LocalCommand.StepVolume
import app.genrix.android.local.LocalCommand.Timer
import app.genrix.android.local.LocalCommand.Torch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalRouterTest {
    private fun check(expected: LocalCommand?, vararg phrases: String) =
        phrases.forEach { assertEquals(it, expected, LocalRouter.match(it)) }

    @Test
    fun opensAppsInEnglishAndHinglish() {
        check(OpenApp("youtube"), "Hey Genrix, YouTube kholo.", "open YouTube", "YouTube open karo", "youtube khol do")
        check(OpenApp("calendar"), "Calendar open karo", "open the calendar app")
        check(OpenApp("google maps"), "Google Maps kholo")
        check(OpenApp("camera"), "camera chalu karo", "launch camera")
    }

    @Test
    fun volume() {
        check(SetVolume(30), "Volume 30 percent kar do.", "volume 30%", "set volume to 30")
        check(SetVolume(100), "volume full kar do", "volume max")
        check(StepVolume(true), "volume badhao", "volume up", "awaaz tez karo")
        check(StepVolume(false), "volume kam karo", "lower the volume", "awaaz dheere karo")
        check(Mute(true), "Phone mute karo.", "mute", "volume mute kar do", "silent mode")
        check(Mute(false), "unmute", "phone unmute karo")
    }

    @Test
    fun mediaAndTorch() {
        check(Media(MediaKey.Pause), "Music pause kar do.", "pause", "gaana band karo", "stop music")
        check(Media(MediaKey.Play), "resume", "gaana chalao", "play music")
        check(Media(MediaKey.Next), "next song", "agla gaana", "skip")
        check(Media(MediaKey.Previous), "previous song", "pichla gaana")
        check(Torch(true), "Flashlight on karo.", "torch jalao", "turn on the flashlight")
        check(Torch(false), "torch band karo", "flashlight off")
    }

    @Test
    fun clockAndStatus() {
        check(LocalCommand.Time, "what's the time", "kitne baje hai", "time kya hua")
        check(LocalCommand.Date, "aaj ki date kya hai", "what day is it today")
        check(LocalCommand.Battery, "kitni battery hai", "battery level", "is my phone charging")
        check(Timer(300), "5 minute ka timer laga do", "set a timer for 5 minutes")
        check(Timer(5_400), "timer for 1 hour 30 minutes")
        check(Alarm(6, 30, Period.Am), "alarm at 6:30 a.m.", "subah 6:30 ka alarm")
        check(Alarm(7, 0, Period.Pm), "shaam 7 baje ka alarm")
        check(Alarm(6, 0, null), "6 baje ka alarm laga do")
        check(Alarm(18, 15, null), "alarm for 18:15")
        check(LocalCommand.Stop, "stop", "bas", "ruk jao", "never mind")
    }

    @Test
    fun leavesTheRestToGemini() {
        assertNull(LocalRouter.match("play despacito"))
        assertNull(LocalRouter.match("open WhatsApp and message Rahul I'm late"))
        assertNull(LocalRouter.match("call mom"))
        assertNull(LocalRouter.match("what's the time in London"))
        assertNull(LocalRouter.match("turn on battery saver"))
        assertNull(LocalRouter.match("cancel my alarm"))
        assertNull(LocalRouter.match("open ChatGPT and start voice"))
        assertNull(LocalRouter.match("explain quantum computing"))
        assertNull(LocalRouter.match("torch"))
        assertNull(LocalRouter.match(""))
    }

    @Test
    fun understandsTheOfflineRecognisersSpelling() {
        // What Vosk's Indian-English model wrote for Hinglish phrases (Mac test, 2026-11-03).
        check(OpenApp("youtube"), "youtube call or", "youtube collar")
        check(OpenApp("calendar"), "calendar open car or")
        check(OpenApp("whatsapp"), "the open what's up")
        check(Torch(true), "flashlight on car or")
        check(Torch(false), "flashlight of cairo") // phone test, Indian English voice
        check(Media(MediaKey.Pause), "music pause car do")
        check(SetVolume(30), "volume thirty percent car do")
        check(SetVolume(45), "set the volume to forty five percent")
        check(Timer(300), "set a timer for five minutes")
        check(Alarm(6, 30, Period.Am), "set an alarm for six thirty am")
        check(LocalCommand.Battery, "kidney battery higher")
        check(LocalCommand.Time, "what time is it")
    }

    @Test
    fun spokenNumbers() {
        assertEquals("volume 35 percent", LocalRouter.numbers("volume thirty five percent"))
        assertEquals("100", LocalRouter.numbers("one hundred"))
        assertEquals("6 30", LocalRouter.numbers("six thirty"))
    }

    @Test
    fun triesEveryAlternative() {
        assertEquals(OpenApp("youtube"), LocalRouter.match(listOf("you tube call", "youtube kholo")))
    }
}
