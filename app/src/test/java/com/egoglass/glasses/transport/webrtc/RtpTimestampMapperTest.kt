package com.egoglass.glasses.transport.webrtc

import org.junit.Assert.assertEquals
import org.junit.Test

class RtpTimestampMapperTest {
    @Test
    fun mapsMonotonicNanosecondsToUnsigned90KhzClock() {
        assertEquals(0, monotonicNsToRtpTimestamp90Khz(0))
        assertEquals(90_000, monotonicNsToRtpTimestamp90Khz(1_000_000_000))
        assertEquals(45_000, monotonicNsToRtpTimestamp90Khz(500_000_000))
        assertEquals(
            12_345_678L * 90_000L % (1L shl 32),
            monotonicNsToRtpTimestamp90Khz(12_345_678_000_000_000L),
        )
    }
}
