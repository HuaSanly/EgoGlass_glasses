package com.egoglass.glasses.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFrameGuideViewTest {
    @Test
    fun landscapeCaptureFillsWidthOnPortraitDisplay() {
        val rect = CameraFrameGuideView.captureBounds(480, 640, 640, 480)
        assertEquals(0f, rect.left)
        assertEquals(140f, rect.top)
        assertEquals(480f, rect.right)
        assertEquals(500f, rect.bottom)
    }

    @Test
    fun portraitCaptureFillsHeightOnLandscapeDisplay() {
        val rect = CameraFrameGuideView.captureBounds(640, 480, 480, 640)
        assertEquals(140f, rect.left)
        assertEquals(0f, rect.top)
        assertEquals(500f, rect.right)
        assertEquals(480f, rect.bottom)
    }
}
