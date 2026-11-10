package app.genrix.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagTest {
    @Test
    fun blanksKeyShapedText() {
        val cleaned = Diag.clean("API key AIzaSyA1b2C3d4E5f6G7h8 not valid")
        assertFalse("AIza" in cleaned)
        assertEquals("API key [key] not valid", cleaned)
    }

    @Test
    fun shortensLongTextAndHandlesNull() {
        assertEquals(160, Diag.clean("x".repeat(500)).length)
        assertEquals("", Diag.clean(null))
    }
}
