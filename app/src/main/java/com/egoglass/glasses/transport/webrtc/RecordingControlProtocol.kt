package com.egoglass.glasses.transport.webrtc

import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal const val RECORDING_CONTROL_CHANNEL = "recording-control-v1"
internal const val MAX_RECORDING_CONTROL_PAYLOAD_BYTES = 2_048
private const val SCHEMA_VERSION = "1.0"
private const val COMMAND_TYPE = "recording_control_command"
private const val STATUS_TYPE = "recording_control_status"
private const val MAX_DETAIL_LENGTH = 256
private val ID_PATTERN = Regex("^[a-f0-9]{32}$")
private val STATUS_FIELDS = setOf(
    "schema_version",
    "message_type",
    "command_id",
    "state",
    "recording_id",
    "countdown_remaining_ms",
    "recording_duration_ms",
    "frame_count",
    "imu_sample_count",
    "detail",
)

enum class RecordingControlAction(val wireValue: String) {
    START("start"),
    STOP("stop"),
}

data class RecordingControlCommand(
    val commandId: String,
    val action: RecordingControlAction,
    val requestedAtElapsedRealtimeNs: Long,
) {
    init {
        require(ID_PATTERN.matches(commandId))
        require(requestedAtElapsedRealtimeNs >= 0)
    }
}

enum class RecordingControlState(val wireValue: String) {
    UNAVAILABLE("unavailable"),
    READY("ready"),
    STARTING_STREAM("starting_stream"),
    COUNTDOWN("countdown"),
    RECORDING("recording"),
    FINALIZING("finalizing"),
    ERROR("error"),
}

data class RecordingControlStatus(
    val commandId: String?,
    val state: RecordingControlState,
    val recordingId: String?,
    val countdownRemainingMs: Long?,
    val recordingDurationMs: Long,
    val frameCount: Long,
    val imuSampleCount: Long,
    val detail: String?,
) {
    init {
        require(commandId == null || ID_PATTERN.matches(commandId))
        require(recordingId == null || ID_PATTERN.matches(recordingId))
        require(countdownRemainingMs == null || countdownRemainingMs >= 0)
        require(recordingDurationMs >= 0)
        require(frameCount >= 0)
        require(imuSampleCount >= 0)
        require(detail == null || detail.length <= MAX_DETAIL_LENGTH)
    }

    companion object {
        val UNAVAILABLE = RecordingControlStatus(
            commandId = null,
            state = RecordingControlState.UNAVAILABLE,
            recordingId = null,
            countdownRemainingMs = null,
            recordingDurationMs = 0,
            frameCount = 0,
            imuSampleCount = 0,
            detail = null,
        )
    }
}

internal fun encodeRecordingControlCommand(command: RecordingControlCommand): ByteArray =
    JSONObject()
        .put("schema_version", SCHEMA_VERSION)
        .put("message_type", COMMAND_TYPE)
        .put("command_id", command.commandId)
        .put("action", command.action.wireValue)
        .put("trigger", "temple_double_tap")
        .put("requested_at_elapsed_realtime_ns", command.requestedAtElapsedRealtimeNs)
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

internal fun decodeRecordingControlStatus(
    payload: ByteArray,
    isBinary: Boolean,
): RecordingControlStatus {
    require(!isBinary) { "Binary recording-control messages are not supported" }
    require(payload.size in 1..MAX_RECORDING_CONTROL_PAYLOAD_BYTES) {
        "Recording-control payload size is invalid"
    }
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val text = decoder.decode(ByteBuffer.wrap(payload)).toString()
    val tokener = JSONTokener(text)
    val value = tokener.nextValue()
    require(value is JSONObject && tokener.nextClean() == 0.toChar()) {
        "Recording-control payload must contain one JSON object"
    }
    require(value.keys().asSequence().toSet() == STATUS_FIELDS) {
        "Recording-control status fields are invalid"
    }
    require(value.get("schema_version") is String && value.getString("schema_version") == SCHEMA_VERSION) {
        "Unsupported recording-control schema version"
    }
    require(value.get("message_type") is String && value.getString("message_type") == STATUS_TYPE) {
        "Unsupported recording-control message type"
    }
    val commandId = value.nullableString("command_id")
    val recordingId = value.nullableString("recording_id")
    val detail = value.nullableString("detail")
    val countdown = value.nullableNonNegativeLong("countdown_remaining_ms")
    val stateValue = value.requireString("state")
    val state = RecordingControlState.entries.singleOrNull { it.wireValue == stateValue }
        ?: throw IllegalArgumentException("Unsupported recording-control state")
    return RecordingControlStatus(
        commandId = commandId,
        state = state,
        recordingId = recordingId,
        countdownRemainingMs = countdown,
        recordingDurationMs = value.requireNonNegativeLong("recording_duration_ms"),
        frameCount = value.requireNonNegativeLong("frame_count"),
        imuSampleCount = value.requireNonNegativeLong("imu_sample_count"),
        detail = detail,
    )
}

internal fun createRecordingControlChannelInit() = org.webrtc.DataChannel.Init().apply {
    ordered = true
    maxRetransmitTimeMs = -1
    maxRetransmits = -1
}

private fun JSONObject.requireString(name: String): String {
    require(get(name) is String) { "Recording-control field $name must be a string" }
    return getString(name)
}

private fun JSONObject.nullableString(name: String): String? {
    val value = get(name)
    require(value == JSONObject.NULL || value is String) {
        "Recording-control field $name must be a string or null"
    }
    return if (value == JSONObject.NULL) null else value as String
}

private fun JSONObject.requireNonNegativeLong(name: String): Long {
    val value = get(name)
    require(value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0) {
        "Recording-control field $name must be an integer"
    }
    return value.toLong().also { require(it >= 0) { "$name must be non-negative" } }
}

private fun JSONObject.nullableNonNegativeLong(name: String): Long? =
    if (get(name) == JSONObject.NULL) null else requireNonNegativeLong(name)
