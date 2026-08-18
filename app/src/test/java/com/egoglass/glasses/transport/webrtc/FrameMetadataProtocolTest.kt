package com.egoglass.glasses.transport.webrtc

import com.egoglass.glasses.capture.CapturedVideoFrame
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameMetadataProtocolTest {
    @Test
    fun `publishes camera generation and unchanged source clocks`() {
        val payload = JSONObject(
            encodeVideoFrameMetadata(
                CapturedVideoFrame(
                    frameId = 7,
                    cameraStartGeneration = 3,
                    nv21 = ByteArray(6),
                    width = 2,
                    height = 2,
                    capturedAtRokidSdkMs = 123,
                    receivedAtElapsedRealtimeNs = 2_000_000_000,
                    videoAtMonotonicNs = 2_000_000_000,
                    rotationDegrees = 0,
                    captureConfigId = "test",
                ),
            ),
        )

        assertEquals(3L, payload.getLong("camera_start_generation"))
        assertEquals(123L, payload.getLong("captured_at_rokid_sdk_ms"))
        assertEquals(2_000_000_000L, payload.getLong("received_at_elapsed_realtime_ns"))
        assertEquals(2_000_000_000L, payload.getLong("video_at_monotonic_ns"))
    }

    @Test
    fun `publishes video only after its metadata is queued`() {
        val rejectedEvents = mutableListOf<String>()
        val rejected = publishFrameAfterMetadataQueued(
            queueMetadata = {
                rejectedEvents += "metadata_rejected"
                false
            },
            publishVideo = { rejectedEvents += "video" },
        )
        assertFalse(rejected)
        assertEquals(listOf("metadata_rejected"), rejectedEvents)

        val acceptedEvents = mutableListOf<String>()
        val accepted = publishFrameAfterMetadataQueued(
            queueMetadata = {
                acceptedEvents += "metadata_queued"
                true
            },
            publishVideo = { acceptedEvents += "video" },
        )
        assertTrue(accepted)
        assertEquals(listOf("metadata_queued", "video"), acceptedEvents)
    }
}
