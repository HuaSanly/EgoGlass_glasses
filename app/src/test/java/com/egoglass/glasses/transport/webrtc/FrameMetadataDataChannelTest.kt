package com.egoglass.glasses.transport.webrtc

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameMetadataDataChannelTest {
    @Test
    fun `metadata channel is reliable but unordered to avoid head of line blocking`() {
        val init = createFrameMetadataChannelInit()

        assertEquals(false, init.ordered)
        assertEquals(-1, init.maxRetransmitTimeMs)
        assertEquals(-1, init.maxRetransmits)
    }
}
