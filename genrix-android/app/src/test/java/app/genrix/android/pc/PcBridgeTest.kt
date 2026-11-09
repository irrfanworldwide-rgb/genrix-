package app.genrix.android.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcBridgeTest {
    @Test
    fun homeNetworkAndTailscaleAddressesAreAllowed() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.0.1", "172.31.255.1", "100.64.1.1", "127.0.0.1", "localhost", "mypc.local")
            .forEach { assertTrue(it, PcBridge.isPrivateHost(it)) }
    }

    @Test
    fun theOpenInternetIsRefused() {
        listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "192.169.1.1", "example.com", "1.2.3", "300.1.1.1")
            .forEach { assertFalse(it, PcBridge.isPrivateHost(it)) }
    }

    @Test
    fun onlyPlainHttpOnAHomeNetworkIsAccepted() {
        assertEquals("http://192.168.1.20:8765", PcBridge.cleanUrl(" http://192.168.1.20:8765/ "))
        assertNull(PcBridge.cleanUrl("https://192.168.1.20:8765"))
        assertNull(PcBridge.cleanUrl("http://8.8.8.8:8765"))
        assertNull(PcBridge.cleanUrl("not a url"))
    }

    /** The same value the Python helper computes with hmac.new(token, message, sha256). */
    @Test
    fun signatureMatchesTheHelper() {
        val sig = PcBridge.sign("test-token-123", "/v1/ping", "1700000000", "00112233445566778899aabbccddeeff", "{}")
        assertEquals("cf01c3c8b793da2631139e4d8aa06597257b484df44c906cddf5b069ea89602b", sig)
    }
}
