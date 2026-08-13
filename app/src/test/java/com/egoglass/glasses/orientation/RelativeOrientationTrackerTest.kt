package com.egoglass.glasses.orientation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RelativeOrientationTrackerTest {
    @Test
    fun firstSampleIsZero() {
        val value = RelativeOrientationTracker().update(1.0, 0.0, 0.0, 0.0)
        assertNotNull(value)
        assertEquals(0.0, value!!.yawDegrees, 1e-8)
        assertEquals(0.0, value.pitchDegrees, 1e-8)
        assertEquals(0.0, value.rollDegrees, 1e-8)
    }

    @Test
    fun resetStartsNewReference() {
        val tracker = RelativeOrientationTracker()
        tracker.update(1.0, 0.0, 0.0, 0.0)
        val half = Math.toRadians(45.0)
        tracker.update(cos(half), 0.0, sin(half), 0.0)
        tracker.reset()
        val value = tracker.update(cos(half), 0.0, sin(half), 0.0)
        assertEquals(0.0, value!!.yawDegrees, 1e-8)
    }

    @Test
    fun invalidQuaternionIsIgnored() {
        assertEquals(null, RelativeOrientationTracker().update(0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun positiveHeadAxesUseRightUpRightConvention() {
        val half = Math.toRadians(15.0)
        fun relative(w: Double, x: Double, y: Double, z: Double): RelativeOrientation {
            val tracker = RelativeOrientationTracker()
            tracker.update(1.0, 0.0, 0.0, 0.0)
            return tracker.update(w, x, y, z)!!
        }
        assertEquals(30.0, relative(cos(half), 0.0, sin(half), 0.0).yawDegrees, 1e-6)
        assertEquals(30.0, relative(cos(half), sin(half), 0.0, 0.0).pitchDegrees, 1e-6)
        assertEquals(30.0, relative(cos(half), 0.0, 0.0, sin(half)).rollDegrees, 1e-6)
    }
}
