package com.egoglass.glasses.capture

import java.util.concurrent.atomic.AtomicLong

internal class CameraStartGenerationCounter(initialGeneration: Long = 1) {
    private val nextGeneration = AtomicLong(initialGeneration)

    init {
        require(initialGeneration >= 1)
    }

    fun next(): Long = nextGeneration.getAndIncrement()
}
