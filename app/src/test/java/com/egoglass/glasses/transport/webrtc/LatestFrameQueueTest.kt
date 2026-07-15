package com.egoglass.glasses.transport.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LatestFrameQueueTest {
    @Test
    fun replacesPendingFrameInsteadOfGrowingLatency() {
        val queue = LatestFrameQueue<Int>()

        assertFalse(queue.offerLatest(1))
        assertTrue(queue.offerLatest(2))

        assertEquals(2, queue.poll(0))
        assertNull(queue.poll(0))
    }

    @Test
    fun workerInterruptionStopsPollingWithoutAnUncaughtException() {
        val queue = LatestFrameQueue<Int>()
        val started = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            started.countDown()
            assertNull(queue.poll(10_000))
            interrupted.set(Thread.currentThread().isInterrupted)
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error ->
                failure.set(error)
            }
        }

        worker.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))
        worker.interrupt()
        worker.join(1_000)

        assertFalse(worker.isAlive)
        assertTrue(interrupted.get())
        assertNull(failure.get())
    }
}
