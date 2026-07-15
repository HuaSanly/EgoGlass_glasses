package com.egoglass.glasses.transport.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebRtcSessionConfigTest {
    @Test
    fun acceptsVersionedLanSignalingEndpointWithoutExposingToken() {
        val config = WebRtcSessionConfig(
            "http://192.168.1.20:8770/api/v1/webrtc/sessions",
            "runtime-pairing-token-123456",
        )

        assertEquals("192.168.1.20:8770", config.displayEndpoint)
        assertEquals(5_000_000, config.targetBitrateBps)
        assertEquals(-1, config.toString().indexOf("runtime-pairing-token-123456"))
    }

    @Test
    fun rejectsWrongPathOrShortToken() {
        assertThrows(IllegalArgumentException::class.java) {
            WebRtcSessionConfig("http://192.168.1.20:8770/other", "valid-token-123456")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebRtcSessionConfig(
                "http://192.168.1.20:8770/api/v1/webrtc/sessions",
                "short",
            )
        }
    }
}
