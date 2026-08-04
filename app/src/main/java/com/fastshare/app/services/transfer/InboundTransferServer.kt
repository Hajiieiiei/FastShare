package com.fastshare.app.services.transfer

import android.content.Context
import android.util.Log
import com.fastshare.app.data.network.protocol.CancelRequest
import com.fastshare.app.data.network.protocol.HelloRequest
import com.fastshare.app.data.network.protocol.HelloResponse
import com.fastshare.app.data.network.protocol.Protocol
import com.fastshare.app.data.network.protocol.TransferRequest
import com.fastshare.app.data.network.protocol.TransferResponse
import com.fastshare.app.data.network.protocol.VerifyRequest
import com.fastshare.app.data.network.protocol.VerifyResponse
import com.fastshare.app.domain.model.AppSettings
import com.fastshare.app.domain.model.Capability
import com.fastshare.app.domain.model.DeviceInfo
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import com.fastshare.app.domain.model.TransferItem
import com.fastshare.app.services.security.CryptoEngine
import com.fastshare.app.services.security.IdentityManager
import com.fastshare.app.services.security.PairingManager
import com.fastshare.app.services.security.TlsFactory
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedded Ktor CIO server that receives transfers.
 * Runs on the local interface only. All routes that write payload bytes resolve
 * their session before touching disk.
 */
@Singleton
class InboundTransferServer @Inject constructor(
    private val context: Context,
    private val identityManager: IdentityManager,
    private val cryptoEngine: CryptoEngine,
    private val tlsFactory: TlsFactory,
    private val pairingManager: PairingManager,
    private val transferStorage: TransferStorage,
) {
    private val tag = "InboundServer"

    private var server: io.ktor.server.engine.ApplicationEngine? = null

    val sessions = mutableMapOf<String, ServerSession>()

    data class ServerSession(
        val peer: DeviceInfo,
        val request: TransferRequest,
        val acceptedTokens: MutableMap<String, String> = mutableMapOf(),
        val resumeOffsets: MutableMap<String, Long> = mutableMapOf(),
        val receivedBytes: MutableMap<String, Long> = mutableMapOf(),
        var approved: Boolean = false,
        var startedAt: Long = System.currentTimeMillis(),
    )

    data class IncomingRequestModel(
        val sessionId: String,
        val peer: DeviceInfo,
        val items: List<TransferItem>,
        val totalSize: Long,
        val isAuto: Boolean,
    )

    private val _requests = MutableSharedFlow<IncomingRequestModel>(extraBufferCapacity = 16)
    val requests: SharedFlow<IncomingRequestModel> = _requests.asSharedFlow()

    var onApprovalRequested: ((IncomingRequestModel, DeviceInfo, (Boolean, Boolean) -> Unit) -> Unit)? = null

    fun start(settings: AppSettings) {
        if (server != null) return
        val port = if (settings.listenPort > 0) settings.listenPort else 53319
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) { json(Protocol.json) }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "internal")))
                }
            }
            install(WebSockets)
            routing {
                post(Protocol.PATH_HELLO) {
                    val body = call.receiveText()
                    val request = runCatching {
                        Protocol.json.decodeFromString(HelloRequest.serializer(), body)
                    }.getOrNull()
                    if (request == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "bad hello"))
                        return@post
                    }
                    val sessionId = cryptoEngine.randomToken(16)
                    val response = HelloResponse(
                        protocolVersion = Protocol.VERSION,
                        accepted = true,
                        sessionId = sessionId,
                        deviceId = identityManager.deviceId(),
                        deviceName = identityManager.deviceName(),
                        platform = DevicePlatform.ANDROID,
                        deviceType = DeviceType.PHONE,
                        appVersion = com.fastshare.app.BuildConfig.VERSION_NAME,
                        publicKey = cryptoEngine.encodePublicKey(cryptoEngine.generateEcdhKeyPair()),
                        fingerprint = identityManager.fingerprint(),
                        capabilities = Capability.DEFAULTS.map { it.wire },
                        nonce = cryptoEngine.randomNonce(),
                        trusted = false,
                    )
                    call.respond(response)
                }

                post(Protocol.PATH_TRANSFER_REQUEST) {
                    val body = call.receiveText()
                    val request = runCatching {
                        Protocol.json.decodeFromString(TransferRequest.serializer(), body)
                    }.getOrNull()
                    if (request == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "bad request"))
                        return@post
                    }
                    val peer = DeviceInfo(
                        deviceId = request.senderDeviceId,
                        deviceName = request.senderName,
                        platform = DevicePlatform.UNKNOWN,
                        appVersion = "",
                        protocolVersion = Protocol.VERSION,
                        ipAddress = call.request.local.remoteHost,
                        port = 0,
                    )
                    val serverSession = ServerSession(peer, request)
                    sessions[request.sessionId] = serverSession

                    val isAuto = settings.approvalPolicy == com.fastshare.app.domain.model.ApprovalPolicy.ACCEPT_ALL
                    val model = IncomingRequestModel(request.sessionId, peer, request.items, request.totalSize, isAuto)
                    if (isAuto) {
                        approveSession(request.sessionId, true, false)
                    } else {
                        onApprovalRequested?.invoke(model, peer) { accept, alwaysAllow ->
                            approveSession(request.sessionId, accept, alwaysAllow)
                        }
                    }
                    val response = TransferResponse(
                        sessionId = request.sessionId,
                        accepted = serverSession.approved,
                        rejectReason = if (serverSession.approved) null else "Pending approval",
                        itemTokens = serverSession.acceptedTokens.toMap(),
                        resumeOffsets = serverSession.resumeOffsets.toMap(),
                        maxParallelStreams = 1,
                    )
                    call.respond(response)
                }

                post(Protocol.PATH_TRANSFER_DATA) {
                    val sessionId = call.request.header(Protocol.HEADER_SESSION) ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing session"))
                        return@post
                    }
                    val itemId = call.request.header(Protocol.HEADER_ITEM_ID) ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing item"))
                        return@post
                    }
                    val offset = call.request.header(Protocol.HEADER_OFFSET)?.toLongOrNull() ?: 0L
                    val totalSize = call.request.header(Protocol.HEADER_TOTAL_SIZE)?.toLongOrNull() ?: 0L
                    val session = sessions[sessionId] ?: run {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "unknown session"))
                        return@post
                    }
                    if (!session.approved) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not approved"))
                        return@post
                    }
                    val item = session.request.items.firstOrNull { it.id == itemId } ?: run {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "unknown item"))
                        return@post
                    }
                    val sanitized = item.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val targetFile = File(context.filesDir, "incoming/$sanitized")
                    targetFile.parentFile?.mkdirs()
                    val channel = call.request.receiveChannel() as? ByteReadChannel
                    if (channel == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "no body"))
                        return@post
                    }
                    transferStorage.writeStream(targetFile, channel, offset, if (totalSize > 0) totalSize else item.size) { written ->
                        session.receivedBytes[itemId] = written
                    }
                    call.respond(HttpStatusCode.OK, mapOf("received" to session.receivedBytes[itemId]))
                }

                post(Protocol.PATH_TRANSFER_VERIFY) {
                    val body = call.receiveText()
                    val request = runCatching {
                        Protocol.json.decodeFromString(VerifyRequest.serializer(), body)
                    }.getOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "bad verify"))
                        return@post
                    }
                    val session = sessions[request.sessionId]
                    if (session == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "unknown session"))
                        return@post
                    }
                    val file = File(context.filesDir, "incoming/${request.itemId}")
                    val valid = file.exists() && file.length() == request.size
                    call.respond(
                        VerifyResponse(
                            sessionId = request.sessionId,
                            itemId = request.itemId,
                            valid = valid,
                            actualSha256 = if (valid) sha256Of(file) else null,
                            savedAs = if (valid) file.absolutePath else null,
                        ),
                    )
                }

                post(Protocol.PATH_TRANSFER_CANCEL) {
                    val body = call.receiveText()
                    val cancel = runCatching {
                        Protocol.json.decodeFromString(CancelRequest.serializer(), body)
                    }.getOrNull()
                    if (cancel != null) sessions.remove(cancel.sessionId)
                    call.respond(HttpStatusCode.OK, mapOf("cancelled" to true))
                }

                get(Protocol.PATH_PING) {
                    call.respond(HttpStatusCode.OK, mapOf("pong" to System.currentTimeMillis()))
                }

                webSocket(Protocol.PATH_EVENTS) {
                    while (true) {
                        delay(10_000)
                        send("""{"t":"heartbeat","ts":${"$"}{System.currentTimeMillis()}}""")
                    }
                }
            }
        }
        server?.start(wait = false)
        Log.i(tag, "Inbound server listening on :$port")
    }

    fun approveSession(sessionId: String, accept: Boolean, alwaysAllow: Boolean) {
        val session = sessions[sessionId] ?: return
        session.approved = accept
        if (accept) {
            session.request.items.forEach { item ->
                session.acceptedTokens[item.id] = cryptoEngine.randomToken()
            }
        }
    }

    fun respondToApproval(sessionId: String, accept: Boolean, alwaysAllow: Boolean) {
        approveSession(sessionId, accept, alwaysAllow)
    }

    fun stop() {
        server?.stop(100, 500)
        server = null
    }

    private fun sha256Of(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02X".format(it) }
    }.getOrNull()
}
