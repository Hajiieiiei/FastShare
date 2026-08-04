package com.fastshare.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Platform of a peer, used for iconography and transport quirks. */
@Serializable
enum class DevicePlatform {
    @SerialName("android") ANDROID,
    @SerialName("ios") IOS,
    @SerialName("windows") WINDOWS,
    @SerialName("macos") MACOS,
    @SerialName("linux") LINUX,
    @SerialName("web") WEB,
    @SerialName("unknown") UNKNOWN;

    companion object {
        fun fromWire(value: String?): DevicePlatform = when (value?.lowercase()) {
            "android" -> ANDROID
            "ios", "ipados" -> IOS
            "windows" -> WINDOWS
            "macos", "darwin" -> MACOS
            "linux" -> LINUX
            "web" -> WEB
            else -> UNKNOWN
        }
    }
}

/** Physical form factor advertised by a peer. */
@Serializable
enum class DeviceType {
    @SerialName("phone") PHONE,
    @SerialName("tablet") TABLET,
    @SerialName("desktop") DESKTOP,
    @SerialName("laptop") LAPTOP,
    @SerialName("tv") TV,
    @SerialName("headless") HEADLESS,
    @SerialName("unknown") UNKNOWN
}

/** Optional protocol features a peer supports; allows forward-compatible negotiation. */
@Serializable
enum class Capability {
    @SerialName("resume") RESUME,
    @SerialName("folders") FOLDERS,
    @SerialName("clipboard") CLIPBOARD,
    @SerialName("text") TEXT,
    @SerialName("contacts") CONTACTS,
    @SerialName("apps") APPS,
    @SerialName("multi_stream") MULTI_STREAM,
    @SerialName("websocket") WEBSOCKET,
    @SerialName("quick_pair") QUICK_PAIR;

    companion object {
        val DEFAULTS: Set<Capability> = setOf(RESUME, FOLDERS, CLIPBOARD, TEXT, MULTI_STREAM, WEBSOCKET, QUICK_PAIR)
        fun fromWire(values: List<String>?): Set<Capability> =
            values?.mapNotNull { raw -> entries.firstOrNull { it.wire == raw } }?.toSet() ?: emptySet()
    }

    val wire: String
        get() = when (this) {
            RESUME -> "resume"; FOLDERS -> "folders"; CLIPBOARD -> "clipboard"; TEXT -> "text"
            CONTACTS -> "contacts"; APPS -> "apps"; MULTI_STREAM -> "multi_stream"
            WEBSOCKET -> "websocket"; QUICK_PAIR -> "quick_pair"
        }
}

/**
 * A peer on the local network. [deviceId] is a stable, locally generated UUID that survives
 * IP changes; discovery keys devices by it so a DHCP renewal does not create a duplicate entry.
 */
@Serializable
data class DeviceInfo(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("deviceName") val deviceName: String,
    @SerialName("platform") val platform: DevicePlatform = DevicePlatform.UNKNOWN,
    @SerialName("deviceType") val deviceType: DeviceType = DeviceType.UNKNOWN,
    @SerialName("appVersion") val appVersion: String = "",
    @SerialName("protocolVersion") val protocolVersion: Int = 1,
    @SerialName("ipAddress") val ipAddress: String = "",
    @SerialName("port") val port: Int = 0,
    @SerialName("capabilities") val capabilities: Set<Capability> = emptySet(),
    @SerialName("fingerprint") val fingerprint: String = "",
    @SerialName("usesTls") val usesTls: Boolean = true,
) {
    val baseUrl: String get() = "${if (usesTls) "https" else "http"}://$ipAddress:$port"

    fun supports(capability: Capability): Boolean = capabilities.contains(capability)

    val isAddressable: Boolean get() = ipAddress.isNotBlank() && port in 1..65535
}

/** Discovery-layer view of a device: identity plus volatile liveness/signal data. */
data class DiscoveredDevice(
    val info: DeviceInfo,
    val lastSeenAt: Long,
    val signalStrength: SignalStrength = SignalStrength.UNKNOWN,
    val rttMillis: Long? = null,
    val source: DiscoverySource = DiscoverySource.MDNS,
    val isTrusted: Boolean = false,
    val isFavorite: Boolean = false,
    val nickname: String? = null,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: info.deviceName

    fun isStale(now: Long, timeoutMillis: Long): Boolean = now - lastSeenAt > timeoutMillis
}

enum class SignalStrength { EXCELLENT, GOOD, FAIR, WEAK, UNKNOWN;
    companion object {
        /** Maps measured round-trip latency to a coarse bucket shown as bars in the device card. */
        fun fromRtt(rttMillis: Long?): SignalStrength = when {
            rttMillis == null -> UNKNOWN
            rttMillis < 15 -> EXCELLENT
            rttMillis < 40 -> GOOD
            rttMillis < 120 -> FAIR
            else -> WEAK
        }
    }
}

enum class DiscoverySource { MDNS, MULTICAST, MANUAL, QR_CODE, HISTORY }
