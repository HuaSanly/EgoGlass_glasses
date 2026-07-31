package com.egoglass.glasses.capture

import java.util.concurrent.ArrayBlockingQueue

internal class ReusableByteArrayPool(capacity: Int) {
    private val buffers = ArrayBlockingQueue<ByteArray>(capacity)

    init {
        require(capacity > 0)
    }

    fun acquire(size: Int): ByteArray {
        require(size > 0)
        while (true) {
            val candidate = buffers.poll() ?: return ByteArray(size)
            if (candidate.size == size) return candidate
        }
    }

    fun release(buffer: ByteArray) {
        buffers.offer(buffer)
    }
}
