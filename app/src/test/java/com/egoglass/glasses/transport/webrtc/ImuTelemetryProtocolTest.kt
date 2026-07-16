package com.egoglass.glasses.transport.webrtc

import com.egoglass.glasses.sensors.ImuCapabilities
import com.egoglass.glasses.sensors.ImuSample
import com.egoglass.glasses.sensors.ImuSensorDescriptor
import com.egoglass.glasses.sensors.ImuSensorType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ImuTelemetryProtocolTest {
    @Test
    fun encodesCapabilitiesWithExactAndroidDescriptors() {
        val encoded = encodeImuCapabilities(
            ImuCapabilities(
                requestedSamplingPeriodUs = 10_000,
                sensors = listOf(
                    ImuSensorDescriptor(
                        sensorType = ImuSensorType.ACCELEROMETER,
                        name = "BMI acceleration",
                        vendor = "Bosch",
                        version = 1,
                        resolution = 0.001,
                        maxRange = 78.4,
                        minDelayUs = 2500,
                        maxDelayUs = 100000,
                        isWakeUp = false,
                    )
                ),
                missingSensorTypes = listOf(ImuSensorType.GYROSCOPE),
            )
        )

        val json = JSONObject(encoded.toString(StandardCharsets.UTF_8))
        assertEquals("0.1", json.getString("schema_version"))
        assertEquals("imu_capabilities", json.getString("message_type"))
        assertEquals("android_sensor_manager", json.getString("source"))
        assertEquals(10_000, json.getInt("requested_sampling_period_us"))
        val sensor = json.getJSONArray("sensors").getJSONObject(0)
        assertEquals("accelerometer", sensor.getString("sensor_type"))
        assertEquals(1, sensor.getInt("android_sensor_type"))
        assertEquals("m_s2", sensor.getString("unit"))
        assertEquals("gyroscope", json.getJSONArray("missing_sensor_types").getString(0))
    }

    @Test
    fun encodesThreeAxisSampleWithoutChangingTimestamps() {
        val encoded = encodeImuSample(
            ImuSample(
                sensorType = ImuSensorType.GYROSCOPE,
                sequenceNumber = 42,
                sensorEventMonotonicNs = 1_234_567,
                receivedAtElapsedRealtimeNs = 1_234_999,
                accuracy = 3,
                values = listOf(0.1, -0.2, 0.3),
            )
        )

        val json = JSONObject(encoded.toString(StandardCharsets.UTF_8))
        assertEquals("imu_sample", json.getString("message_type"))
        assertEquals("gyroscope", json.getString("sensor_type"))
        assertEquals(4, json.getInt("android_sensor_type"))
        assertEquals(42, json.getLong("sequence_number"))
        assertEquals(1_234_567, json.getLong("sensor_event_monotonic_ns"))
        assertEquals(1_234_999, json.getLong("received_at_elapsed_realtime_ns"))
        assertEquals(3, json.getJSONArray("values").length())
    }

    @Test
    fun configuresUnorderedZeroRetransmitChannelAndBoundedSending() {
        val init = createImuTelemetryChannelInit()

        assertFalse(init.ordered)
        assertEquals(0, init.maxRetransmits)
        assertEquals(-1, init.maxRetransmitTimeMs)
        assertTrue(shouldSendImuTelemetry(true, MAX_IMU_BUFFERED_BYTES - 10, 10))
        assertFalse(shouldSendImuTelemetry(true, MAX_IMU_BUFFERED_BYTES - 10, 11))
        assertFalse(shouldSendImuTelemetry(false, 0, 10))
    }
}
