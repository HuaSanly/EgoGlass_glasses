package com.egoglass.glasses.capture

import org.junit.Assert.assertThrows
import org.junit.Test

class CapturedVideoFrameTest {
    @Test
    fun `rejects different callback and WebRTC clock samples`() {
        assertThrows(IllegalArgumentException::class.java) {
            CapturedVideoFrame(
                frameId = 1,
                cameraStartGeneration = 1,
                nv21 = ByteArray(6),
                width = 2,
                height = 2,
                capturedAtRokidSdkMs = 10,
                receivedAtElapsedRealtimeNs = 20,
                videoAtMonotonicNs = 21,
                rotationDegrees = 0,
                captureConfigId = "test",
            )
        }
    }

    @Test
    fun `rejects an invalid camera start generation`() {
        assertThrows(IllegalArgumentException::class.java) {
            CapturedVideoFrame(
                frameId = 1,
                cameraStartGeneration = 0,
                nv21 = ByteArray(6),
                width = 2,
                height = 2,
                capturedAtRokidSdkMs = 10,
                receivedAtElapsedRealtimeNs = 20,
                videoAtMonotonicNs = 20,
                rotationDegrees = 0,
                captureConfigId = "test",
            )
        }
    }
}
