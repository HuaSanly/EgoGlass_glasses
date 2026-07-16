package com.egoglass.glasses

import android.app.Application
import com.egoglass.glasses.domain.connection.SdkConnection
import com.egoglass.glasses.platform.rokid.createRokidSdkConnection
import com.egoglass.glasses.platform.rokid.createRokidNv21FrameSource
import com.egoglass.glasses.platform.android.createAndroidImuSource
import com.egoglass.glasses.streaming.DefaultStreamingSession
import com.egoglass.glasses.streaming.StreamingSession
import com.egoglass.glasses.transport.discovery.ClientDiscovery
import com.egoglass.glasses.transport.discovery.createUdpClientDiscovery
import com.egoglass.glasses.transport.webrtc.createAndroidWebRtcPublisher

class EgoGlassApplication : Application() {
    lateinit var sdkConnection: SdkConnection
        private set
    lateinit var streamingSession: StreamingSession
        private set
    lateinit var clientDiscovery: ClientDiscovery
        private set

    override fun onCreate() {
        super.onCreate()
        sdkConnection = createRokidSdkConnection(applicationContext)
        clientDiscovery = createUdpClientDiscovery()
        streamingSession = DefaultStreamingSession(
            createRokidNv21FrameSource(),
            createAndroidWebRtcPublisher(applicationContext),
            createAndroidImuSource(applicationContext),
        )
    }
}
