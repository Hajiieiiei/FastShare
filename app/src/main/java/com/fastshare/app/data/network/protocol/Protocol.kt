package com.fastshare.app.data.network.protocol

import com.fastshare.app.domain.model.Capability
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import com.fastshare.app.domain.model.TransferItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * FastShare wire protocol, version 1.
 *
 * Transport: HTTP/1.1 over TLS 1.2+ on the local link only. Control messages are JSON;
 * payload bytes travel as raw octet streams on a dedicated endpoint so no base64 inflation occurs.
 *
 * Flow:
 *   1. POST /v1/hello            -> HelloRequest  / HelloResponse    (identity + key exchange)
 *   2. POST /v1/transfer/request -> TransferRequest / TransferResponse (user approval, per-item tokens)
 *   3. POST /v1/transfer/data    -> raw stream per item, resumable via Range-style offset
 *   4. POST /v1/transfer/verify  -> VerifyRequest / VerifyResponse   (checksum confirmation)
 *   5. WS   /v1/events           -> realtime progress + cancel/pause signalling
 */
object Protocol {
    const val VERSION = 1
    const val MIN_SUPPORTED_VERSION = 1

    const val PATH_HELLO = "/v1/hello"
    const val PATH_TRANSFER_REQUEST = "/v1/transfer/request"
    const val PATH_TRANSFER_DATA = "/v1/transfer/data"
    const val PATH_TRANSFER_VERIFY = "/v1/transfer/verify"
    const val PATH_TRANSFER_CANCEL = "/v1/transfer/cancel"
    const val PATH_TRANSFER_STATUS = "/v1/transfer/status"
    const val PATH_PING = "/v1/ping"
    const val PATH_INFO = "/v1/info"
    const val PATH_EVENTS = "/v1/events"

    const val HEADER_SESSION = "X-FastShare-Session"
    const val HEADER_TOKEN = "X-FastShare-Token"
    const val HEADER_ITEM_ID = "X-FastShare-Item"
    const val HEADER_OFFSET = "X-FastShare-Offset"
    const val HEADER_TOTAL_SIZE = "X-FastShare-Total"
    const val HEADER_CHECKSUM = "X-FastShare-Sha256"
    const val HEADER_DEVICE_ID = "X-FastShare-Device"
    const val HEADER_PROTOCOL = "X-FastShare-Protocol"

    /** 512 KiB balances syscall overhead against memory pressure on low-end devices. */
    const val STREAM_CHUNK_SIZE = 512 * 1024
    const val HANDSHAKE_TIMEOUT_MS = 10_000L
    const val APPROVAL_TIMEOUT_MS = 60_000L
    const val SOCKET_READ_TIMEOUT_MS = 30_000L
    const val PROGRESS_EMIT_INTERVAL_MS = 200L

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }
}

@Serializable
data class HelloRequest(
    @SerialName("protocolVersion") val protocolVersion: Int = Protocol.VERSION,
    @SerialName("deviceId") val deviceId: String,
    @SerialName("deviceName") val deviceName: String,
    @SerialName("platform") val platform: DevicePlatform,
    @SerialName("deviceType") val deviceType: DeviceType,
    @SerialName("appVersion") val appVersion: String,
    @SerialName("publicKey") val publicKey: String,
    @SerialName("fingerprint") val fingerprint: String,
    @SerialName("capabilities") val capabilities: List<String> = Capability.DEFAULTS.map { it.wire },
    @SerialName("nonce") val nonce: String,
)

@Serializable
data class HelloResponse(
    @SerialName("protocolVersion") val protocolVersion: Int = Protocol.VERSION,
    @SerialName("accepted") val accepted: Boolean,
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("deviceId") val deviceId: String,
    @SerialName("deviceName") val deviceName: String,
    @SerialName("platform") val platform: DevicePlatform,
    @SerialName("deviceType") val deviceType: DeviceType = DeviceType.UNKNOWN,
    @SerialName("appVersion") val appVersion: String = "",
    @SerialName("publicKey") val publicKey: String? = null,
    @SerialName("fingerprint") val fingerprint: String,
    @SerialName("capabilities") val capabilities: List<String> = emptyList(),
    @SerialName("nonce") val nonce: String? = null,
    @SerialName("rejectReason") val rejectReason: String? = null,
    @SerialName("trusted") val trusted: Boolean = false,
)

@Serializable
data class TransferRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("senderDeviceId") val senderDeviceId: String,
    @SerialName("senderName") val senderName: String,
    @SerialName("items") val items: List<TransferItem>,
    @SerialName("totalSize") val totalSize: Long,
    @SerialName("manifestChecksum") val manifestChecksum: String,
    @SerialName("resumeSupported") val resumeSupported: Boolean = true,
)

@Serializable
data class TransferResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("accepted") val accepted: Boolean,
    @SerialName("rejectReason") val rejectReason: String? = null,
    /** itemId -> one-time upload token; absent items were declined by the receiver. */
    @SerialName("itemTokens") val itemTokens: Map<String, String> = emptyMap(),
    /** itemId -> byte offset already on disk, enabling resume of a partial file. */
    @SerialName("resumeOffsets") val resumeOffsets: Map<String, Long> = emptyMap(),
    @SerialName("maxParallelStreams") val maxParallelStreams: Int = 1,
)

@Serializable
data class VerifyRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("itemId") val itemId: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("size") val size: Long,
)

@Serializable
data class VerifyResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("itemId") val itemId: String,
    @SerialName("valid") val valid: Boolean,
    @SerialName("actualSha256") val actualSha256: String? = null,
    @SerialName("savedAs") val savedAs: String? = null,
)

@Serializable
data class CancelRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("itemId") val itemId: String? = null,
    @SerialName("reason") val reason: String = "user_cancelled",
)

@Serializable
data class StatusResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("state") val state: String,
    @SerialName("receivedBytes") val receivedBytes: Long,
    @SerialName("totalBytes") val totalBytes: Long,
    @SerialName("itemOffsets") val itemOffsets: Map<String, Long> = emptyMap(),
)

@Serializable
data class DeviceInfoResponse(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("deviceName") val deviceName: String,
    @SerialName("platform") val platform: DevicePlatform,
    @SerialName("deviceType") val deviceType: DeviceType,
    @SerialName("appVersion") val appVersion: String,
    @SerialName("protocolVersion") val protocolVersion: Int,
    @SerialName("port") val port: Int,
    @SerialName("capabilities") val capabilities: List<String>,
    @SerialName("fingerprint") val fingerprint: String,
)

@Serializable
data class ErrorResponse(
    @SerialName("error") val error: String,
    @SerialName("detail") val detail: String? = null,
)

/** Realtime events pushed over the WebSocket channel. */
@Serializable
sealed interface WsEvent {
    @Serializable
    @SerialName("progress")
    data class Progress(
        val sessionId: String,
        val itemId: String,
        val transferredBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : WsEvent

    @Serializable
    @SerialName("item_completed")
    data class ItemCompleted(val sessionId: String, val itemId: String, val sha256: String) : WsEvent

    @Serializable
    @SerialName("session_state")
    data class SessionState(val sessionId: String, val state: String, val error: String? = null) : WsEvent

    @Serializable
    @SerialName("control")
    data class Control(val sessionId: String, val action: String) : WsEvent

    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(val timestamp: Long) : WsEvent
}
