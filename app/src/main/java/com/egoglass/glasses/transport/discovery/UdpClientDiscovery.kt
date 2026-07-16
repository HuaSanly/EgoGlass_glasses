package com.egoglass.glasses.transport.discovery

import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private const val RECEIVE_TIMEOUT_MS = 700
private const val DISCOVERY_ATTEMPTS = 6

fun createUdpClientDiscovery(): ClientDiscovery = UdpClientDiscovery()

private class UdpClientDiscovery : ClientDiscovery {
    private val generation = AtomicLong(0)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "egoglass-client-discovery")
    }

    @Volatile
    private var activeSocket: DatagramSocket? = null

    override fun discover(listener: ClientDiscoveryListener) {
        val requestGeneration = generation.incrementAndGet()
        activeSocket?.close()
        executor.execute { discover(requestGeneration, listener) }
    }

    override fun cancel() {
        generation.incrementAndGet()
        activeSocket?.close()
        activeSocket = null
    }

    private fun discover(
        requestGeneration: Long,
        listener: ClientDiscoveryListener,
    ) {
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val request = encodeDiscoveryRequest(nonce)
        try {
            DatagramSocket().use { socket ->
                activeSocket = socket
                socket.broadcast = true
                socket.soTimeout = RECEIVE_TIMEOUT_MS
                val destinations = broadcastAddresses()
                repeat(DISCOVERY_ATTEMPTS) {
                    destinations.forEach { destination ->
                        socket.send(
                            DatagramPacket(request, request.size, destination, DISCOVERY_PORT)
                        )
                    }
                    val config = receiveResponse(socket, nonce)
                    if (config != null) {
                        if (requestGeneration == generation.get()) listener.onDiscovered(config)
                        return
                    }
                }
            }
            if (requestGeneration == generation.get()) {
                listener.onError("No EgoGlass client found on this Wi-Fi network")
            }
        } catch (error: Exception) {
            if (requestGeneration == generation.get()) {
                listener.onError("Client discovery failed: ${error::class.java.simpleName}")
            }
        } finally {
            activeSocket = null
        }
    }

    private fun receiveResponse(
        socket: DatagramSocket,
        nonce: String,
    ): WebRtcSessionConfig? {
        val buffer = ByteArray(DISCOVERY_MAX_DATAGRAM_BYTES + 1)
        val response = DatagramPacket(buffer, buffer.size)
        return try {
            socket.receive(response)
            if (response.port != DISCOVERY_PORT || response.length > DISCOVERY_MAX_DATAGRAM_BYTES) {
                return null
            }
            val config = decodeDiscoveryResponse(
                response.data.copyOfRange(response.offset, response.offset + response.length),
                nonce,
            )
            if (URI(config.signalingUrl).host != response.address.hostAddress) null else config
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: SocketException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf(InetAddress.getByName("255.255.255.255"))
        NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.flatMap { it.interfaceAddresses.asSequence() }
            ?.mapNotNull { it.broadcast }
            ?.filterIsInstance<Inet4Address>()
            ?.forEach(addresses::add)
        return addresses
    }
}
