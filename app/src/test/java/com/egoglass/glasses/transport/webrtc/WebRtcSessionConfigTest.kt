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
        assertEquals(6_000_000, config.targetBitrateBps)
        assertEquals(-1, config.toString().indexOf("runtime-pairing-token-123456"))
    }

    @Test
    fun createsAdaptiveBitratePolicyWithinConfiguredMaximum() {
        assertEquals(
            VideoBitratePolicy(
                minimumBps = 800_000,
                startBps = 3_000_000,
                maximumBps = 6_000_000,
            ),
            videoBitratePolicy(6_000_000),
        )
        assertEquals(
            VideoBitratePolicy(
                minimumBps = 500_000,
                startBps = 500_000,
                maximumBps = 500_000,
            ),
            videoBitratePolicy(500_000),
        )
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
        assertThrows(IllegalArgumentException::class.java) {
            WebRtcSessionConfig(
                "http://192.168.1.20:8770/api/v1/webrtc/sessions",
                "runtime-pairing-token-123456",
                targetBitrateBps = 8_000_001,
            )
        }
    }
}
