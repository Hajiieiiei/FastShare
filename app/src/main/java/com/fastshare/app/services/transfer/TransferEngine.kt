package com.fastshare.app.services.transfer

import android.content.Context
import android.util.Log
import com.fastshare.app.data.local.dao.TrustedDeviceDao
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
import com.fastshare.app.domain.model.ItemProgress
import com.fastshare.app.domain.model.TransferDirection
import com.fastshare.app.domain.model.TransferItem
import com.fastshare.app.domain.model.TransferSession
import com.fastshare.app.domain.model.TransferState
import com.fastshare.app.services.security.CryptoEngine
import com.fastshare.app.services.security.IdentityManager
import com.fastshare.app.services.security.SessionKey
import com.fastshare.app.services.transfer.model.SendRequest
import com.fastshare.app.services.transfer.model.TransferUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central transfer orchestration: initiates outbound sessions, services inbound
 * approvals, runs multi-stream file transfer, resume, throttling, and aggregates
 * progress into UI-facing [TransferUiState] flows.
 */
@Singleton
class TransferEngine @Inject constructor(
    private val context: Context,
    private val identityManager: IdentityManager,
    private val cryptoEngine: CryptoEngine,
    private val trustedDeviceDao: TrustedDeviceDao,
    private val httpClient: TransferHttpClient,
    private val inboundServer: InboundTransferServer,
) {
    private val tag = "TransferEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sessions = MutableStateFlow<Map<String, TransferUiState>>(emptyMap())
    val sessions: StateFlow<Map<String, TransferUiState>> = _sessions.asStateFlow()

    private val _incomingRequests = MutableStateFlow<List<IncomingRequestUi>>(emptyList())
    val incomingRequests: StateFlow<List<IncomingRequestUi>> = _incomingRequests.asStateFlow()

    data class IncomingRequestUi(
        val sessionId: String,
        val peer: DeviceInfo,
        val items: List<TransferItem>,
        val totalBytes: Long,
        val receivedAt: Long,
        val autoAccepted: Boolean,
    )

    fun startInboundServer(settings: AppSettings) {
        inboundServer.start(settings)
    }

    fun bindInbound() {
        inboundServer.onApprovalRequested = { model, peer, respond ->
            _incomingRequests.update { list ->
                list + IncomingRequestUi(
                    sessionId = model.sessionId,
                    peer = peer,
                    items = model.items,
                    totalBytes = model.totalSize,
                    receivedAt = System.currentTimeMillis(),
                    autoAccepted = model.isAuto,
                )
            }
        }
    }

    fun sendFiles(request: SendRequest, settings: AppSettings) {
        val session = TransferSession(
            id = request.sessionId,
            direction = TransferDirection.SEND,
            peer = DeviceInfo(request.peerDeviceId, request.peerFingerprint, protocolVersion = 1,
                ipAddress = request.peerIp, port = request.peerPort),
            items = request.items,
        )
        val ui = TransferUiState(request.sessionId, session, 0)
        _sessions.update { it + (request.sessionId to ui) }
        launchSession(request, settings)
    }

    fun approveIncoming(sessionId: String, accept: Boolean, alwaysAllow: Boolean = false) {
        _incomingRequests.update { list -> list.filterNot { it.sessionId == sessionId } }
        inboundServer.respondToApproval(sessionId, accept, alwaysAllow)
    }

    fun removeSession(sessionId: String) {
        _sessions.update { it - sessionId }
    }

    fun getSession(sessionId: String): TransferUiState? = _sessions.value[sessionId]

    private fun launchSession(request: SendRequest, settings: AppSettings) {
        scope.launch {
            runCatching { executeSend(request, settings) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e(tag, "session ${request.sessionId} failed", e)
                    markSession(request.sessionId, TransferState.FAILED, error = e.message ?: "Unknown error")
                }
        }
    }

    private suspend fun executeSend(request: SendRequest, settings: AppSettings) {
        updateState(request.sessionId, TransferState.CONNECTING)
        val sessionKey = performHandshake(request, settings)

        updateState(request.sessionId, TransferState.AWAITING_APPROVAL)
        val transferRequest = TransferRequest(
            sessionId = request.sessionId,
            senderDeviceId = request.peerDeviceId,
            senderName = identityManager.deviceName(),
            items = request.items,
            totalSize = request.totalSize,
            manifestChecksum = request.manifestChecksum,
            resumeSupported = request.resumeSupported,
        )
        val negotiated = httpClient.requestTransfer(request.peerIp, request.peerPort, request.sessionId, transferRequest)
        if (!negotiated.accepted) throw RuntimeException("Handshake rejected: " + negotiated.rejectReason ?: "Receiver declined")

        updateState(request.sessionId, TransferState.IN_PROGRESS)
        val startTime = System.currentTimeMillis()
        val streamCount = negotiated.maxParallelStreams.coerceIn(1, settings.effectiveStreams)
        val chunked = if (streamCount > 1) request.items.chunked((request.items.size + streamCount - 1) / streamCount) else listOf(request.items)

        coroutineScope {
            val jobs = chunked.map { group ->
                async(Dispatchers.IO) {
                    group.forEach { item ->
                        val offset = negotiated.resumeOffsets[item.id] ?: 0L
                        val token = negotiated.itemTokens[item.id] ?: ""
                        val ok = httpClient.uploadItem(
                            ip = request.peerIp,
                            port = request.peerPort,
                            sessionId = request.sessionId,
                            item = item,
                            itemToken = token,
                            offset = offset,
                            speedLimitKbps = settings.transferSpeedLimitKbps,
                            onProgress = { sent, _ ->
                                updateProgress(request.sessionId, item, sent, item.size)
                            },
                        )
                        if (!ok) throw RuntimeException("Transfer failed")
                    }
                }
            }
            jobs.awaitAll()
        }

        updateState(request.sessionId, TransferState.VERIFYING)
        val lastItem = request.items.last()
        val verify = httpClient.verifyTransfer(
            ip = request.peerIp,
            port = request.peerPort,
            request = VerifyRequest(request.sessionId, lastItem.id, lastItem.sha256 ?: "", lastItem.size),
        )
        if (!verify.valid) throw RuntimeException("Checksum mismatch: " + lastItem.name)

        markSession(request.sessionId, TransferState.COMPLETED)
    }

    private suspend fun performHandshake(request: SendRequest, settings: AppSettings): SessionKey {
        val localKeyPair = cryptoEngine.generateEcdhKeyPair()
        val localNonce = cryptoEngine.randomNonce()
        val hello = HelloRequest(
            protocolVersion = Protocol.VERSION,
            deviceId = identityManager.deviceId(),
            deviceName = identityManager.deviceName(),
            platform = DevicePlatform.ANDROID,
            deviceType = DeviceType.PHONE,
            appVersion = com.fastshare.app.BuildConfig.VERSION_NAME,
            publicKey = cryptoEngine.encodePublicKey(localKeyPair),
            fingerprint = identityManager.fingerprint(),
            capabilities = Capability.DEFAULTS.map { it.wire },
            nonce = localNonce,
        )
        val response: HelloResponse = httpClient.hello(request.peerIp, request.peerPort, hello)
            ?: throw RuntimeException("No hello response")
        if (!response.accepted) throw RuntimeException("Handshake rejected: " + response.rejectReason ?: "Rejected")
        return cryptoEngine.deriveSessionKey(
            localKeyPair = localKeyPair,
            remotePublicKeyEncoded = response.publicKey ?: "",
            localNonce = localNonce,
            remoteNonce = response.nonce ?: "",
        )
    }

    private fun updateProgress(sessionId: String, item: TransferItem, sent: Long, total: Long) {
        _sessions.update { map ->
            val state = map[sessionId] ?: return@update map
            val session = state.session ?: return@update map
            val prev = session.itemProgress[item.id]
            val itemProgress = (prev ?: ItemProgress(item.id, item.name, total, 0L, TransferState.PENDING))
                .copy(transferredBytes = sent, state = TransferState.IN_PROGRESS)
            val newTransferred = session.itemProgress.values.sumOf { it.transferredBytes }
            map + (sessionId to state.copy(
                session = session.copy(
                    transferredBytes = newTransferred,
                    currentItemId = item.id,
                    itemProgress = session.itemProgress + (item.id to itemProgress),
                ),
            ))
        }
    }

    fun updateSessionState(sessionId: String, state: TransferState, error: String? = null) {
        markSession(sessionId, state, error)
    }

    private fun markSession(sessionId: String, state: TransferState, error: String? = null) {
        _sessions.update { map ->
            val ui = map[sessionId] ?: return@update map
            map + (sessionId to ui.copy(
                session = ui.session?.copy(
                    state = state,
                    error = error,
                    finishedAt = if (state.isTerminal) System.currentTimeMillis() else null,
                ),
            ))
        }
    }

    private fun updateState(sessionId: String, state: TransferState) = markSession(sessionId, state)

    fun shutdown() {
        scope.cancel()
        inboundServer.stop()
    }
}
