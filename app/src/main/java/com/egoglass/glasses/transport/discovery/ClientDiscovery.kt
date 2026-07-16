package com.egoglass.glasses.transport.discovery

import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig

interface ClientDiscoveryListener {
    fun onDiscovered(config: WebRtcSessionConfig)

    fun onError(message: String)
}

interface ClientDiscovery {
    fun discover(listener: ClientDiscoveryListener)

    fun cancel()
}
