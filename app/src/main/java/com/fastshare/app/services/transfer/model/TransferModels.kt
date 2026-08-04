package com.fastshare.app.services.transfer.model

import com.fastshare.app.domain.model.TransferItem
import com.fastshare.app.domain.model.TransferSession

/** Payload handed to the engine when the user taps send. */
data class SendRequest(
    val sessionId: String,
    val peerDeviceId: String,
    val peerIp: String,
    val peerPort: Int,
    val peerFingerprint: String,
    val items: List<TransferItem>,
    val totalSize: Long,
    val manifestChecksum: String,
    val resumeSupported: Boolean = true,
)

/** Commands the UI layer can issue to an active engine session. */
sealed interface TransferCommand {
    data class Approve(val sessionId: String, val accept: Boolean, val alwaysAllow: Boolean = false) : TransferCommand
    data class Pause(val sessionId: String) : TransferCommand
    data class Resume(val sessionId: String) : TransferCommand
    data class Cancel(val sessionId: String, val reason: String = "user_cancelled") : TransferCommand
}

/** Snapshot exposed to the UI; wraps the domain session plus stream-level detail. */
data class TransferUiState(
    val sessionId: String,
    val session: TransferSession?,
    val streamCount: Int,
)
