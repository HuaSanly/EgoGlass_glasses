package com.egoglass.glasses.sensors

interface ImuSourceListener {
    fun onCapabilities(capabilities: ImuCapabilities)

    fun onSample(sample: ImuSample)

    fun onImuError(message: String)
}

interface ImuSource {
    fun start(listener: ImuSourceListener)

    fun stop()
}
