package com.egoglass.glasses.transport.webrtc

private const val RTP_CLOCK_HZ = 90_000L
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val RTP_MODULUS = 1L shl 32

internal fun monotonicNsToRtpTimestamp90Khz(monotonicNs: Long): Long {
    require(monotonicNs >= 0)
    val seconds = monotonicNs / NANOS_PER_SECOND
    val remainderNs = monotonicNs % NANOS_PER_SECOND
    val ticks = seconds * RTP_CLOCK_HZ + remainderNs * RTP_CLOCK_HZ / NANOS_PER_SECOND
    return ticks % RTP_MODULUS
}
