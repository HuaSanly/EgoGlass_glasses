package com.egoglass.glasses.domain.connection

import java.util.concurrent.CopyOnWriteArraySet

internal class SdkConnectionStateStore(
    initialState: SdkConnectionState = SdkConnectionState.IDLE,
) {
    private val listeners = CopyOnWriteArraySet<SdkConnectionListener>()

    @Volatile
    var current: SdkConnectionState = initialState
        private set

    @Volatile
    private var active = false

    fun addListener(listener: SdkConnectionListener) {
        listeners.add(listener)
        listener.onStateChanged(current)
    }

    fun removeListener(listener: SdkConnectionListener) {
        listeners.remove(listener)
    }

    @Synchronized
    fun activate() {
        active = true
    }

    @Synchronized
    fun deactivate() {
        active = false
        publish(SdkConnectionState.IDLE)
    }

    @Synchronized
    fun publishIfActive(state: SdkConnectionState): Boolean {
        if (!active) return false
        publish(state)
        return true
    }

    private fun publish(state: SdkConnectionState) {
        if (current == state) return

        current = state
        listeners.forEach { it.onStateChanged(state) }
    }
}
