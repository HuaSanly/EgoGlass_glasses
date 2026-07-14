package com.egoglass.glasses.domain.connection

fun interface SdkConnectionListener {
    fun onStateChanged(state: SdkConnectionState)
}

interface SdkConnection {
    val state: SdkConnectionState

    fun addListener(listener: SdkConnectionListener)

    fun removeListener(listener: SdkConnectionListener)

    fun start()

    fun close()
}
