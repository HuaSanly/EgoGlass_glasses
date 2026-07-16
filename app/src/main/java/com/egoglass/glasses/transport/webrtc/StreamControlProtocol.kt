package com.egoglass.glasses.transport.webrtc

import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal const val STREAM_CONTROL_CHANNEL = "stream-control-v1"
internal const val MAX_STREAM_CONTROL_PAYLOAD_BYTES = 1_024
private const val STREAM_CONTROL_SCHEMA_VERSION = "1.0"
private const val STREAM_CONTROL_COMMAND_TYPE = "stream_control_command"
private const val STREAM_CONTROL_STATUS_TYPE = "stream_control_status"
private const val MAX_STREAM_CONTROL_DETAIL_LENGTH = 256
private val COMMAND_ID_PATTERN = Regex("^[a-f0-9]{32}$")
private val COMMAND_FIELDS = setOf("schema_version", "message_type", "command_id", "action")

enum class StreamControlAction(val wireValue: String) {
    START("start"),
    STOP("stop"),
}

data class StreamControlCommand(
    val commandId: String,
    val action: StreamControlAction,
) {
    init {
        require(COMMAND_ID_PATTERN.matches(commandId))
    }
}

enum class StreamControlState(val wireValue: String) {
    READY("ready"),
    STARTING("starting"),
    STREAMING("streaming"),
    STOPPING("stopping"),
    STOPPED("stopped"),
    ERROR("error"),
}

data class StreamControlStatus(
    val commandId: String?,
    val state: StreamControlState,
    val detail: String? = null,
) {
    init {
        require(commandId == null || COMMAND_ID_PATTERN.matches(commandId))
        require(detail == null || detail.length <= MAX_STREAM_CONTROL_DETAIL_LENGTH)
    }
}

internal fun decodeStreamControlCommand(
    payload: ByteArray,
    isBinary: Boolean,
): StreamControlCommand {
    require(!isBinary) { "Binary stream-control messages are not supported" }
    require(payload.size in 1..MAX_STREAM_CONTROL_PAYLOAD_BYTES) {
        "Stream-control payload size is invalid"
    }
    val text = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(payload))
        .toString()
    val tokener = JSONTokener(text)
    val value = tokener.nextValue()
    require(value is JSONObject && tokener.nextClean() == 0.toChar()) {
        "Stream-control payload must contain one JSON object"
    }
    val fields = value.keys().asSequence().toSet()
    require(fields == COMMAND_FIELDS) { "Stream-control command fields are invalid" }
    COMMAND_FIELDS.forEach { field ->
        require(value.get(field) is String) { "Stream-control field $field must be a string" }
    }
    require(value.getString("schema_version") == STREAM_CONTROL_SCHEMA_VERSION) {
        "Unsupported stream-control schema version"
    }
    require(value.getString("message_type") == STREAM_CONTROL_COMMAND_TYPE) {
        "Unsupported stream-control message type"
    }
    val commandId = value.getString("command_id")
    require(COMMAND_ID_PATTERN.matches(commandId)) { "Stream-control command_id is invalid" }
    val action = StreamControlAction.entries.singleOrNull {
        it.wireValue == value.getString("action")
    } ?: throw IllegalArgumentException("Unsupported stream-control action")
    return StreamControlCommand(commandId, action)
}

internal fun encodeStreamControlStatus(status: StreamControlStatus): ByteArray = JSONObject()
    .put("schema_version", STREAM_CONTROL_SCHEMA_VERSION)
    .put("message_type", STREAM_CONTROL_STATUS_TYPE)
    .put("command_id", status.commandId ?: JSONObject.NULL)
    .put("state", status.state.wireValue)
    .put("detail", status.detail ?: JSONObject.NULL)
    .toString()
    .toByteArray(StandardCharsets.UTF_8)
