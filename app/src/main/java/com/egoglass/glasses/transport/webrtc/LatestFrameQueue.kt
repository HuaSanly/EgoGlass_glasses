package com.egoglass.glasses.transport.webrtc

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

internal class LatestFrameQueue<T>(
    private val onDiscard: (T) -> Unit = {},
) {
    private val queue = ArrayBlockingQueue<T>(1)

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

    fun clear() {
        while (true) onDiscard(queue.poll() ?: return)
    }
}
