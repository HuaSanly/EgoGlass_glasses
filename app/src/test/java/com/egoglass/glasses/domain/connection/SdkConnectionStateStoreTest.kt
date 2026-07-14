package com.egoglass.glasses.domain.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkConnectionStateStoreTest {
    @Test
    fun listenerReceivesCurrentStateImmediately() {
        val store = SdkConnectionStateStore(SdkConnectionState.CONNECTING)
        val received = mutableListOf<SdkConnectionState>()

        store.addListener { received += it }

        assertEquals(listOf(SdkConnectionState.CONNECTING), received)
    }

    @Test
    fun duplicateStatesAreNotPublished() {
        val store = SdkConnectionStateStore()
        val received = mutableListOf<SdkConnectionState>()
        store.addListener { received += it }
        store.activate()

        store.publishIfActive(SdkConnectionState.CONNECTING)
        store.publishIfActive(SdkConnectionState.CONNECTING)
        store.publishIfActive(SdkConnectionState.REGISTERING)
        store.publishIfActive(SdkConnectionState.READY)

        assertEquals(
            listOf(
                SdkConnectionState.IDLE,
                SdkConnectionState.CONNECTING,
                SdkConnectionState.REGISTERING,
                SdkConnectionState.READY,
            ),
            received,
        )
    }

    @Test
    fun removedListenerReceivesNoFurtherStates() {
        val store = SdkConnectionStateStore()
        val received = mutableListOf<SdkConnectionState>()
        val listener = SdkConnectionListener { received += it }
        store.addListener(listener)

        store.removeListener(listener)
        store.activate()
        store.publishIfActive(SdkConnectionState.ERROR)

        assertEquals(listOf(SdkConnectionState.IDLE), received)
    }

    @Test
    fun callbacksAfterDeactivationAreIgnored() {
        val store = SdkConnectionStateStore()
        val received = mutableListOf<SdkConnectionState>()
        store.addListener { received += it }
        store.activate()
        store.publishIfActive(SdkConnectionState.CONNECTING)

        store.deactivate()
        val accepted = store.publishIfActive(SdkConnectionState.READY)

        assertFalse(accepted)
        assertEquals(SdkConnectionState.IDLE, store.current)
        assertEquals(
            listOf(
                SdkConnectionState.IDLE,
                SdkConnectionState.CONNECTING,
                SdkConnectionState.IDLE,
            ),
            received,
        )
    }

    @Test
    fun onlyFailureStatesCanRetry() {
        assertTrue(SdkConnectionState.DISCONNECTED.canRetry)
        assertTrue(SdkConnectionState.ERROR.canRetry)
        assertFalse(SdkConnectionState.IDLE.canRetry)
        assertFalse(SdkConnectionState.READY.canRetry)
    }
}
