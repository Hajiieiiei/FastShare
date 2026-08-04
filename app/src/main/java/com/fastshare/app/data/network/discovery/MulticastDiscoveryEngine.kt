package com.fastshare.app.data.network.discovery

import android.content.Context
import android.util.Log
import com.fastshare.app.core.network.NetworkUtils
import com.fastshare.app.data.network.protocol.DiscoveryPacket
import com.fastshare.app.data.network.protocol.Protocol
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UDP multicast presence beacon on 224.0.0.171:53320. Supplements mDNS:
 * Android-to-Android discovery is instant (no 5s Nsd resolve), and peers are announced
 * with the full capability set so the UI can show transfer options before first contact.
 *
 * Battery: announce every 10s while visible; query on demand. All sockets are kept on the
 * Wi-Fi interface via NetworkInterface by-name lookup so a VPN/loopback never leaks packets.
 */
@Singleton
class MulticastDiscoveryEngine @Inject constructor(
    private val context: Context,
) {
    private val tag = "MulticastDiscovery"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var announceJob: Job? = null
    @Volatile private var lastPacket: DiscoveryPacket? = null

    /** Starts a 10s interval announce loop; replacing the prior packet each tick. */
    fun startAnnouncing(buildPacket: () -> DiscoveryPacket) {
        if (announceJob?.isActive == true) return
        announceJob = scope.launch {
            while (isActive) {
                val packet = buildPacket().also { lastPacket = it }
                runCatching { send(packet, DiscoveryPacket.PacketType.ANNOUNCE) }
                    .onFailure { Log.w(tag, "announce failed", it) }
                delay(ANNOUNCE_INTERVAL_MS)
            }
        }
    }

    fun stopAnnouncing() {
        announceJob?.cancel()
        announceJob = null
    }

    fun sendQuery() {
        val packet = lastPacket ?: return
        runCatching { send(packet, DiscoveryPacket.PacketType.QUERY) }
            .onFailure { Log.w(tag, "query failed", it) }
    }

    fun sendBye() {
        val packet = lastPacket ?: return
        runCatching { send(packet, DiscoveryPacket.PacketType.BYE) }
    }

    private fun send(packet: DiscoveryPacket, type: DiscoveryPacket.PacketType) {
        val localIp = NetworkUtils.bestLocalIpv4() ?: return
        val iface = runCatching { java.net.NetworkInterface.getByInetAddress(localIp) }.getOrNull() ?: return
        DatagramSocket().use { socket ->
            socket.reuseAddress = true
            socket.networkInterface = iface
            val payload = Protocol.json.encodeToString(
                DiscoveryPacket.serializer(),
                packet.copy(
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    platform = if (packet.platform == DevicePlatform.UNKNOWN) DevicePlatform.ANDROID else packet.platform,
                    deviceType = if (packet.deviceType == DeviceType.UNKNOWN) DeviceType.PHONE else packet.deviceType,
                ),
            ).toByteArray(Charsets.UTF_8)
            val target = InetAddress.getByName(MULTICAST_GROUP)
            socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(target, MULTICAST_PORT)))
        }
    }

    /** Continuous listening loop; emits every valid packet received paired with the sender IP. */
    fun listen(): Flow<Pair<DiscoveryPacket, String>> = callbackFlow {
        val localIp = NetworkUtils.bestLocalIpv4() ?: run { close(); return@callbackFlow }
        val iface = runCatching { java.net.NetworkInterface.getByInetAddress(localIp) }.getOrNull() ?: run { close(); return@callbackFlow }
        val socket = MulticastSocket(MULTICAST_PORT).apply {
            reuseAddress = true
            soTimeout = LISTEN_TIMEOUT_MS
            networkInterface = iface
            joinGroup(InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP), MULTICAST_PORT), iface)
        }
        val buffer = ByteArray(MAX_PACKET_SIZE)
        val receiveJob = scope.launch {
            while (isActive) {
                try {
                    val datagram = DatagramPacket(buffer, buffer.size)
                    socket.receive(datagram)
                    // Ignore packets we sent ourselves.
                    if (datagram.address.hostAddress == localIp.hostAddress) continue
                    runCatching {
                        val decoded = Protocol.json.decodeFromString(
                            DiscoveryPacket.serializer(),
                            String(datagram.data, datagram.offset, datagram.length, Charsets.UTF_8),
                        )
                        trySend(decoded to datagram.address.hostAddress)
                    }.onFailure { Log.w(tag, "bad packet: ${it.message}") }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal; keeps the receive loop responsive to cancellation.
                }
            }
        }
        awaitClose {
            receiveJob.cancel()
            runCatching { socket.leaveGroup(InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP), MULTICAST_PORT), iface) }
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val MULTICAST_GROUP = "224.0.0.171"
        const val MULTICAST_PORT = 53320
        const val ANNOUNCE_INTERVAL_MS = 10_000L
        const val LISTEN_TIMEOUT_MS = 4_000
        const val MAX_PACKET_SIZE = 2048
    }
}
