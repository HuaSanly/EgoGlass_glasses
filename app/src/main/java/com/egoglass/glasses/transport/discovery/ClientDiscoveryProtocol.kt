package com.egoglass.glasses.transport.discovery

import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig
import org.json.JSONObject
import java.nio.charset.StandardCharsets

internal const val DISCOVERY_PORT = 8771
internal const val DISCOVERY_MAX_DATAGRAM_BYTES = 2048
private const val SCHEMA_VERSION = "1.0"
private const val REQUEST_TYPE = "client_discovery_request"
private const val RESPONSE_TYPE = "client_discovery_response"
private val NONCE_PATTERN = Regex("^[a-f0-9]{32}$")
private val RESPONSE_FIELDS = setOf(
    "schema_version",
    "message_type",
    "nonce",
    "signaling_url",
    "pairing_token",
)

internal fun encodeDiscoveryRequest(nonce: String): ByteArray {
    val request = JSONObject()
    discoveryRequestFields(nonce).forEach(request::put)
    return request.toString().toByteArray(StandardCharsets.UTF_8)
}

internal fun decodeDiscoveryResponse(
    payload: ByteArray,
    expectedNonce: String,
): WebRtcSessionConfig {
    requireDiscoveryPayloadSize(payload.size)
    val response = JSONObject(payload.toString(StandardCharsets.UTF_8))
    val fields = response.keys().asSequence().associateWith(response::getString)
    return sessionConfigFromDiscoveryFields(fields, expectedNonce)
}

internal fun discoveryRequestFields(nonce: String): Map<String, String> {
    require(NONCE_PATTERN.matches(nonce))
    return linkedMapOf(
        "schema_version" to SCHEMA_VERSION,
        "message_type" to REQUEST_TYPE,
        "nonce" to nonce,
    )
}

internal fun sessionConfigFromDiscoveryFields(
    fields: Map<String, String>,
    expectedNonce: String,
): WebRtcSessionConfig {
    require(NONCE_PATTERN.matches(expectedNonce))
    require(fields.keys == RESPONSE_FIELDS)
    require(fields.getValue("schema_version") == SCHEMA_VERSION)
    require(fields.getValue("message_type") == RESPONSE_TYPE)
    require(fields.getValue("nonce") == expectedNonce)
    return WebRtcSessionConfig(
        signalingUrl = fields.getValue("signaling_url"),
        pairingToken = fields.getValue("pairing_token"),
    )
}

internal fun requireDiscoveryPayloadSize(size: Int) {
    require(size in 1..DISCOVERY_MAX_DATAGRAM_BYTES)
}
