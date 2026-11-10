package app.genrix.android.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class AppMatchTest {
    private val apps = listOf(
        InstalledApp("WhatsApp", "com.whatsapp"),
        InstalledApp("WhatsApp Business", "com.whatsapp.w4b"),
        InstalledApp("YouTube", "com.google.android.youtube"),
        InstalledApp("YouTube Music", "com.google.android.apps.youtube.music"),
        InstalledApp("Camera", "com.sec.android.app.camera"),
    )

    private fun names(query: String) = AppMatch.find(query, apps).map { it.label }

    @Test
    fun exactNameWinsOverLongerOnes() {
        assertEquals(listOf("WhatsApp"), names("whatsapp"))
        assertEquals(listOf("YouTube"), names("YouTube app"))
    }

    @Test
    fun partialNamesListEveryMatch() {
        assertEquals(listOf("YouTube", "YouTube Music"), names("you"))
        assertEquals(listOf("YouTube Music"), names("music"))
        assertEquals(emptyList<String>(), names("instagram"))
    }
}
