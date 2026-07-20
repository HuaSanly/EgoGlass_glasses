package com.egoglass.glasses.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CameraStartGenerationCounterTest {
    @Test
    fun `increments for every reserved camera start`() {
        val generations = CameraStartGenerationCounter()

        assertEquals(1L, generations.next())
        assertEquals(2L, generations.next())
    }

    @Test
    fun `rejects invalid initial generation`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraStartGenerationCounter(0)
        }
    }
}
