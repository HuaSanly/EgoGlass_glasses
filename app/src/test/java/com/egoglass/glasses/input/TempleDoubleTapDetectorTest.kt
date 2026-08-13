package com.egoglass.glasses.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TempleDoubleTapDetectorTest {
    @Test
    fun singleTapDoesNotTrigger() {
        val detector = TempleDoubleTapDetector()
        assertFalse(detector.onTap(1_000))
    }

    @Test
    fun doubleTapWithinWindowTriggersOnce() {
        val detector = TempleDoubleTapDetector()
        assertFalse(detector.onTap(1_000))
        assertTrue(detector.onTap(1_331))
        assertFalse(detector.onTap(1_500))
    }

    @Test
    fun tooFastOrTooSlowTapDoesNotTrigger() {
        val detector = TempleDoubleTapDetector()
        assertFalse(detector.onTap(1_000))
        assertFalse(detector.onTap(1_050))
        assertFalse(detector.onTap(2_000))
        assertFalse(detector.onTap(2_501))
    }

    @Test
    fun thirdTapStartsANewPairInsteadOfTogglingAgain() {
        val detector = TempleDoubleTapDetector()
        assertFalse(detector.onTap(1_000))
        assertTrue(detector.onTap(1_300))
        assertFalse(detector.onTap(1_600))
        assertTrue(detector.onTap(1_900))
    }
}
