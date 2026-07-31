package com.egoglass.glasses.capture

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ReusableByteArrayPoolTest {
    @Test
    fun `reuses released buffers with the requested size`() {
        val pool = ReusableByteArrayPool(capacity = 2)
        val first = pool.acquire(12)
        pool.release(first)

        assertSame(first, pool.acquire(12))
    }

    @Test
    fun `discards stale buffers after a resolution change`() {
        val pool = ReusableByteArrayPool(capacity = 2)
        pool.release(ByteArray(12))

        assertNotSame(pool.acquire(24), pool.acquire(24))
    }
}
