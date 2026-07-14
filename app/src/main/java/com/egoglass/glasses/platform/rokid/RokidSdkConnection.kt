package com.egoglass.glasses.platform.rokid

import android.content.Context
import android.util.Log
import com.egoglass.glasses.domain.connection.SdkConnection
import com.egoglass.glasses.domain.connection.SdkConnectionListener
import com.egoglass.glasses.domain.connection.SdkConnectionState
import com.egoglass.glasses.domain.connection.SdkConnectionStateStore
import com.rokid.security.glass3.open.sdk.GlassSdk
import com.rokid.security.glass3.open.sdk.client.IServiceConnectionCallback
import com.rokid.security.system.server.IClientCallback

private const val CLIENT_ID = "EgoGlassGlasses"
private const val TAG = "EgoGlassSdk"

fun createRokidSdkConnection(context: Context): SdkConnection =
    RokidSdkConnection(context.applicationContext)

private class RokidSdkConnection(
    private val applicationContext: Context,
) : SdkConnection {
    private val stateStore = SdkConnectionStateStore()

    override val state: SdkConnectionState
        get() = stateStore.current

    private val clientCallback = object : IClientCallback.Stub() {
        override fun onReady() {
            publish(SdkConnectionState.READY)
        }
    }

    private val serviceConnectionCallback = object : IServiceConnectionCallback {
        override fun onServiceConnected() {
            publish(SdkConnectionState.REGISTERING)
            runCatching {
                GlassSdk.registerClient(CLIENT_ID, clientCallback)
            }.onFailure { error ->
                Log.e(TAG, "client_registration_failed", error)
                publish(SdkConnectionState.ERROR)
            }
        }

        override fun onServiceDisconnected() {
            publish(SdkConnectionState.DISCONNECTED)
        }

        override fun onBindingDied() {
            publish(SdkConnectionState.ERROR)
        }
    }

    override fun addListener(listener: SdkConnectionListener) {
        stateStore.addListener(listener)
    }

    override fun removeListener(listener: SdkConnectionListener) {
        stateStore.removeListener(listener)
    }

    @Synchronized
    override fun start() {
        if (state == SdkConnectionState.CONNECTING ||
            state == SdkConnectionState.REGISTERING ||
            state == SdkConnectionState.READY
        ) {
            return
        }

        stateStore.activate()
        if (GlassSdk.isReady()) {
            publish(SdkConnectionState.READY)
            return
        }

        publish(SdkConnectionState.CONNECTING)
        runCatching {
            GlassSdk.bindSecurityService(applicationContext, serviceConnectionCallback)
        }.onFailure { error ->
            Log.e(TAG, "service_binding_failed", error)
            publish(SdkConnectionState.ERROR)
        }
    }

    @Synchronized
    override fun close() {
        stateStore.deactivate()
        Log.i(TAG, "sdk_state=${SdkConnectionState.IDLE.name}")
        runCatching { GlassSdk.release() }
            .onFailure { error -> Log.w(TAG, "sdk_release_failed", error) }
    }

    private fun publish(state: SdkConnectionState) {
        if (stateStore.publishIfActive(state)) {
            Log.i(TAG, "sdk_state=${state.name}")
        }
    }
}
