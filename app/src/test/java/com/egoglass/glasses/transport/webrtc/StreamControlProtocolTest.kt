package com.egoglass.glasses.transport.webrtc

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets

class StreamControlProtocolTest {
    private val commandId = "0123456789abcdef0123456789abcdef"

    @Test
    fun decodesStrictTextStartAndStopCommands() {
        assertEquals(
            StreamControlCommand(commandId, StreamControlAction.START),
            decodeStreamControlCommand(commandPayload("start"), isBinary = false),
        )
        assertEquals(
            StreamControlCommand(commandId, StreamControlAction.STOP),
            decodeStreamControlCommand(commandPayload("stop"), isBinary = false),
        )
    }

    @Test
    fun rejectsBinaryMalformedOversizedUnknownAndWronglyTypedCommands() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeStreamControlCommand(commandPayload("start"), isBinary = true)
        }
        assertThrows(Exception::class.java) {
            decodeStreamControlCommand("{".toByteArray(), isBinary = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeStreamControlCommand(
                ByteArray(MAX_STREAM_CONTROL_PAYLOAD_BYTES + 1),
                isBinary = false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeStreamControlCommand(
                commandJson("start").put("unknown", true).toString().toByteArray(),
                isBinary = false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeStreamControlCommand(
                commandJson("start").put("action", 1).toString().toByteArray(),
                isBinary = false,
            )
        }
    }

    @Test
    fun rejectsWrongSchemaTypeCommandIdActionAndTrailingContent() {
        listOf(
            commandJson("start").put("schema_version", "2.0"),
            commandJson("start").put("message_type", "other"),
            commandJson("start").put("command_id", "not-a-command-id"),
            commandJson("pause"),
        ).forEach { payload ->
            assertThrows(IllegalArgumentException::class.java) {
                decodeStreamControlCommand(payload.toString().toByteArray(), isBinary = false)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeStreamControlCommand(
                "${commandJson("start")} true".toByteArray(),
                isBinary = false,
            )
        }
    }

    @Test
    fun encodesStatusWithMatchingCommandIdAndExplicitNulls() {
        val payload = encodeStreamControlStatus(
            StreamControlStatus(commandId, StreamControlState.STOPPED)
        )
        val status = JSONObject(payload.toString(StandardCharsets.UTF_8))

        assertEquals(5, status.length())
        assertEquals("1.0", status.getString("schema_version"))
        assertEquals("stream_control_status", status.getString("message_type"))
        assertEquals(commandId, status.getString("command_id"))
        assertEquals("stopped", status.getString("state"))
        assertNull(status.optString("detail", null))
    }

    private fun commandPayload(action: String): ByteArray =
        commandJson(action).toString().toByteArray(StandardCharsets.UTF_8)

    private fun commandJson(action: String) = JSONObject()
        .put("schema_version", "1.0")
        .put("message_type", "stream_control_command")
        .put("command_id", commandId)
        .put("action", action)
}
