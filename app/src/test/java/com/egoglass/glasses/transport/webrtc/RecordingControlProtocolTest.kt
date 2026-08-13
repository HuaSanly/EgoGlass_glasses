package com.egoglass.glasses.transport.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class RecordingControlProtocolTest {
    @Test
    fun commandHasExactContractFields() {
        val payload = encodeRecordingControlCommand(
            RecordingControlCommand(
                "0123456789abcdef0123456789abcdef",
                RecordingControlAction.START,
                123,
            )
        ).toString(StandardCharsets.UTF_8)
        assertTrue(payload.contains("\"message_type\":\"recording_control_command\""))
        assertTrue(payload.contains("\"trigger\":\"temple_double_tap\""))
        assertEquals(123, JSONObject(payload).getLong("requested_at_elapsed_realtime_ns"))
        assertEquals(
            setOf(
                "schema_version",
                "message_type",
                "command_id",
                "action",
                "trigger",
                "requested_at_elapsed_realtime_ns",
            ),
            JSONObject(payload).keys().asSequence().toSet(),
        )
    }

    @Test
    fun statusDecodesStrictly() {
        val payload = """{"schema_version":"1.0","message_type":"recording_control_status","command_id":null,"state":"recording","recording_id":"abcdef0123456789abcdef0123456789","countdown_remaining_ms":null,"recording_duration_ms":1200,"frame_count":36,"imu_sample_count":200,"detail":null}""".toByteArray()
        val status = decodeRecordingControlStatus(payload, false)
        assertEquals(RecordingControlState.RECORDING, status.state)
        assertEquals(36, status.frameCount)
    }

    @Test
    fun binaryUnknownFieldsAndInvalidIdsAreRejected() {
        val valid = """{"schema_version":"1.0","message_type":"recording_control_status","command_id":null,"state":"ready","recording_id":null,"countdown_remaining_ms":null,"recording_duration_ms":0,"frame_count":0,"imu_sample_count":0,"detail":null}""".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { decodeRecordingControlStatus(valid, true) }
        val extra = valid.toString(StandardCharsets.UTF_8).replace("}", ",\"extra\":1}").toByteArray()
        assertThrows(IllegalArgumentException::class.java) { decodeRecordingControlStatus(extra, false) }
        assertThrows(IllegalArgumentException::class.java) {
            RecordingControlCommand("BAD", RecordingControlAction.START, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeRecordingControlStatus(ByteArray(MAX_RECORDING_CONTROL_PAYLOAD_BYTES + 1), false)
        }
        val badType = valid.toString(StandardCharsets.UTF_8)
            .replace("\"frame_count\":0", "\"frame_count\":\"0\"")
            .toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            decodeRecordingControlStatus(badType, false)
        }
    }

    @Test
    fun channelIsReliableAndOrdered() {
        val init = createRecordingControlChannelInit()
        assertTrue(init.ordered)
        assertEquals(-1, init.maxRetransmitTimeMs)
        assertEquals(-1, init.maxRetransmits)
        assertFalse(init.negotiated)
    }
}
