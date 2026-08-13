package com.egoglass.glasses.input

class TempleDoubleTapDetector(
    private val minimumIntervalMs: Long = 100,
    private val maximumIntervalMs: Long = 500,
) {
    private var firstTapAtMs: Long? = null

    init {
        require(minimumIntervalMs >= 0)
        require(maximumIntervalMs > minimumIntervalMs)
    }

    fun onTap(eventTimeMs: Long): Boolean {
        require(eventTimeMs >= 0)
        val first = firstTapAtMs
        if (first == null || eventTimeMs < first || eventTimeMs - first > maximumIntervalMs) {
            firstTapAtMs = eventTimeMs
            return false
        }
        val interval = eventTimeMs - first
        if (interval < minimumIntervalMs) return false
        firstTapAtMs = null
        return true
    }

    fun reset() {
        firstTapAtMs = null
    }
}
