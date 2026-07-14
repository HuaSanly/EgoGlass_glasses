package com.egoglass.glasses

import android.app.Application
import com.egoglass.glasses.domain.connection.SdkConnection
import com.egoglass.glasses.platform.rokid.createRokidSdkConnection

class EgoGlassApplication : Application() {
    lateinit var sdkConnection: SdkConnection
        private set

    override fun onCreate() {
        super.onCreate()
        sdkConnection = createRokidSdkConnection(applicationContext)
    }
}
