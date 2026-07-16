package com.egoglass.glasses.transport.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ClientDiscoveryProtocolTest {
    private val nonce = "0123456789abcdef0123456789abcdef"

    @Test
    fun requestCarriesOnlyVersionTypeAndRandomNonce() {
        val fields = discoveryRequestFields(nonce)

        assertEquals(
            setOf("schema_version", "message_type", "nonce"),
            fields.keys,
        )
        assertEquals("1.0", fields.getValue("schema_version"))
        assertEquals("client_discovery_request", fields.getValue("message_type"))
        assertEquals(nonce, fields.getValue("nonce"))
        assertFalse(fields.containsKey("pairing_token"))
    }

    @Test
    fun responseProducesValidatedRedactingWebRtcConfig() {
        val fields = responseFields()

        val config = sessionConfigFromDiscoveryFields(fields, nonce)

        assertEquals("192.168.3.185:8770", config.displayEndpoint)
        assertFalse(config.toString().contains("runtime-pairing-token-123456"))
    }

    @Test
    fun responseRejectsWrongNonceUnknownFieldsAndOversizePayload() {
        assertThrows(IllegalArgumentException::class.java) {
            sessionConfigFromDiscoveryFields(
                responseFields(),
                "abcdef0123456789abcdef0123456789",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            sessionConfigFromDiscoveryFields(responseFields() + ("extra" to "true"), nonce)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireDiscoveryPayloadSize(DISCOVERY_MAX_DATAGRAM_BYTES + 1)
        }
    }

    private fun responseFields() = mapOf(
        "schema_version" to "1.0",
        "message_type" to "client_discovery_response",
        "nonce" to nonce,
        "signaling_url" to "http://192.168.3.185:8770/api/v1/webrtc/sessions",
        "pairing_token" to "runtime-pairing-token-123456",
    )
}
