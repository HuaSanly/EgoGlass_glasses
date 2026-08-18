package com.egoglass.glasses.transport.webrtc

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

internal class LatestFrameQueue<T>(
    capacity: Int = 1,
    private val onDiscard: (T) -> Unit = {},
) {
    private val queue = ArrayBlockingQueue<T>(capacity)

    init {
        require(capacity > 0)
    }

    fun offerLatest(value: T): Boolean {
        if (queue.offer(value)) return false
        queue.poll()?.let(onDiscard)
        check(queue.offer(value))
        return true
    }

    fun poll(timeoutMs: Long): T? = try {
        queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    }

    fun size(): Int = queue.size

    fun clear() {
        while (true) onDiscard(queue.poll() ?: return)
    }
}
