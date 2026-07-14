package com.egoglass.glasses.domain.connection

enum class SdkConnectionState(val canRetry: Boolean) {
    IDLE(false),
    CONNECTING(false),
    REGISTERING(false),
    READY(false),
    DISCONNECTED(true),
    ERROR(true),
}
