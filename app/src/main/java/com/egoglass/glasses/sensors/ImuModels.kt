package com.egoglass.glasses.sensors

enum class ImuSensorType(
    val androidSensorType: Int,
    val wireValue: String,
    val unit: String,
) {
    ACCELEROMETER(1, "accelerometer", "m_s2"),
    GYROSCOPE(4, "gyroscope", "rad_s"),
}

data class ImuSensorDescriptor(
    val sensorType: ImuSensorType,
    val name: String,
    val vendor: String,
    val version: Int,
    val resolution: Double,
    val maxRange: Double,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val isWakeUp: Boolean,
)

data class ImuCapabilities(
    val requestedSamplingPeriodUs: Int,
    val sensors: List<ImuSensorDescriptor>,
    val missingSensorTypes: List<ImuSensorType>,
)

data class ImuSample(
    val sensorType: ImuSensorType,
    val sequenceNumber: Long,
    val sensorEventMonotonicNs: Long,
    val receivedAtElapsedRealtimeNs: Long,
    val accuracy: Int,
    val values: List<Double>,
) {
    init {
        require(sequenceNumber >= 0)
        require(sensorEventMonotonicNs >= 0)
        require(receivedAtElapsedRealtimeNs >= 0)
        require(accuracy in -1..3)
        require(values.size == 3)
        require(values.all(Double::isFinite))
    }
}
