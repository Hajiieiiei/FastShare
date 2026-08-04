package com.fastshare.app.data.network.protocol

import com.fastshare.app.domain.model.DeviceInfo
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UDP multicast announcement used alongside mDNS. mDNS is authoritative for desktop
 * interop; the multicast beacon covers Android devices where NsdManager is unreliable on
 * some OEM builds, and makes rediscovery after a Wi-Fi reconnect near-instant.
 */
@Serializable
data class DiscoveryPacket(
    @SerialName("t") val type: PacketType,
    @SerialName("pv") val protocolVersion: Int = Protocol.VERSION,
    @SerialName("id") val deviceId: String,
    @SerialName("n") val deviceName: String,
    @SerialName("pf") val platform: DevicePlatform = DevicePlatform.ANDROID,
    @SerialName("dt") val deviceType: DeviceType = DeviceType.PHONE,
    @SerialName("av") val appVersion: String = "",
    @SerialName("p") val port: Int,
    @SerialName("fp") val fingerprint: String = "",
    @SerialName("caps") val capabilities: List<String> = emptyList(),
    @SerialName("tls") val usesTls: Boolean = true,
    @SerialName("ts") val timestamp: Long = System.currentTimeMillis(),
) {
    @Serializable
    enum class PacketType {
        /** Periodic presence beacon. */
        @SerialName("announce") ANNOUNCE,
        /** Active scan; recipients answer immediately with an ANNOUNCE. */
        @SerialName("query") QUERY,
        /** Directed reply to a QUERY. */
        @SerialName("reply") REPLY,
        /** Graceful shutdown so peers can drop us without waiting for timeout. */
        @SerialName("bye") BYE,
    }

    fun toDeviceInfo(ipAddress: String): DeviceInfo = DeviceInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = platform,
        deviceType = deviceType,
        appVersion = appVersion,
        protocolVersion = protocolVersion,
        ipAddress = ipAddress,
        port = port,
        capabilities = com.fastshare.app.domain.model.Capability.fromWire(capabilities),
        fingerprint = fingerprint,
        usesTls = usesTls,
    )
}

/** Payload encoded into a pairing QR code; contains everything needed for a direct connect. */
@Serializable
data class QrPayload(
    @SerialName("v") val version: Int = Protocol.VERSION,
    @SerialName("id") val deviceId: String,
    @SerialName("n") val deviceName: String,
    @SerialName("ip") val ipAddress: String,
    @SerialName("p") val port: Int,
    @SerialName("fp") val fingerprint: String,
    @SerialName("pf") val platform: DevicePlatform = DevicePlatform.ANDROID,
) {
    fun encode(): String = "fastshare://connect?d=" + Protocol.json.encodeToString(serializer(), this)
        .encodeBase64Url()

    companion object {
        private const val SCHEME_PREFIX = "fastshare://connect?d="

        fun decode(raw: String): QrPayload? = runCatching {
            val payload = raw.trim().removePrefix(SCHEME_PREFIX)
            Protocol.json.decodeFromString(serializer(), payload.decodeBase64Url())
        }.getOrNull()
    }
}

private fun String.encodeBase64Url(): String =
    android.util.Base64.encodeToString(toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)

private fun String.decodeBase64Url(): String =
    String(android.util.Base64.decode(this, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
