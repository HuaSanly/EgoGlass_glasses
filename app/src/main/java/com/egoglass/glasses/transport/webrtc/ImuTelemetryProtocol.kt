package com.egoglass.glasses.transport.webrtc

import com.egoglass.glasses.sensors.ImuCapabilities
import com.egoglass.glasses.sensors.ImuSample
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import java.nio.charset.StandardCharsets

const val IMU_TELEMETRY_CHANNEL = "imu-telemetry-experimental-v0"
internal const val MAX_IMU_BUFFERED_BYTES = 65_536L
private const val IMU_SCHEMA_VERSION = "0.1"

internal fun createImuTelemetryChannelInit() = DataChannel.Init().apply {
    ordered = false
    maxRetransmitTimeMs = -1
    maxRetransmits = 0
}

internal fun shouldSendImuTelemetry(
    channelOpen: Boolean,
    bufferedAmount: Long,
    payloadBytes: Int,
): Boolean = channelOpen &&
    bufferedAmount >= 0 &&
    payloadBytes >= 0 &&
    bufferedAmount + payloadBytes <= MAX_IMU_BUFFERED_BYTES

fun encodeImuCapabilities(capabilities: ImuCapabilities): ByteArray {
    val sensors = JSONArray()
    capabilities.sensors.forEach { descriptor ->
        sensors.put(
            JSONObject()
                .put("sensor_type", descriptor.sensorType.wireValue)
                .put("android_sensor_type", descriptor.sensorType.androidSensorType)
                .put("name", descriptor.name)
                .put("vendor", descriptor.vendor)
                .put("version", descriptor.version)
                .put("unit", descriptor.sensorType.unit)
                .put("resolution", descriptor.resolution)
                .put("max_range", descriptor.maxRange)
                .put("min_delay_us", descriptor.minDelayUs)
                .put("max_delay_us", descriptor.maxDelayUs)
                .put("is_wake_up", descriptor.isWakeUp)
        )
    }
    val missing = JSONArray()
    capabilities.missingSensorTypes.forEach { sensorType ->
        missing.put(sensorType.wireValue)
    }
    return JSONObject()
        .put("schema_version", IMU_SCHEMA_VERSION)
        .put("message_type", "imu_capabilities")
        .put("source", "android_sensor_manager")
        .put("requested_sampling_period_us", capabilities.requestedSamplingPeriodUs)
        .put("sensors", sensors)
        .put("missing_sensor_types", missing)
        .toString()
        .toByteArray(StandardCharsets.UTF_8)
}

fun encodeImuSample(sample: ImuSample): ByteArray = JSONObject()
    .put("schema_version", IMU_SCHEMA_VERSION)
    .put("message_type", "imu_sample")
    .put("sensor_type", sample.sensorType.wireValue)
    .put("android_sensor_type", sample.sensorType.androidSensorType)
    .put("sequence_number", sample.sequenceNumber)
    .put("sensor_event_monotonic_ns", sample.sensorEventMonotonicNs)
    .put("received_at_elapsed_realtime_ns", sample.receivedAtElapsedRealtimeNs)
    .put("accuracy", sample.accuracy)
    .put("values", JSONArray(sample.values))
    .toString()
    .toByteArray(StandardCharsets.UTF_8)
