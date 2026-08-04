package com.fastshare.app.domain.model

/** A persisted, immutable record of a finished (or failed) transfer. */
data class TransferRecord(
    val id: String,
    val direction: TransferDirection,
    val peerDeviceId: String,
    val peerName: String,
    val peerPlatform: DevicePlatform,
    val itemCount: Int,
    val totalBytes: Long,
    val transferredBytes: Long,
    val state: TransferState,
    val startedAt: Long,
    val finishedAt: Long?,
    val durationMillis: Long,
    val averageBytesPerSecond: Long,
    val destination: String?,
    val error: String?,
    val items: List<TransferRecordItem> = emptyList(),
) {
    val isSuccess: Boolean get() = state == TransferState.COMPLETED
}

data class TransferRecordItem(
    val id: String,
    val recordId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val kind: PayloadKind,
    val state: TransferState,
    val localUri: String?,
    val sha256: String?,
)

/** A device the user has paired with; trust is keyed by TLS fingerprint, not by IP. */
data class TrustedDevice(
    val deviceId: String,
    val deviceName: String,
    val nickname: String?,
    val platform: DevicePlatform,
    val fingerprint: String,
    val publicKey: String,
    val alwaysAllow: Boolean,
    val isFavorite: Boolean,
    val lastIpAddress: String?,
    val lastPort: Int,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val transferCount: Int,
    val totalBytesExchanged: Long,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: deviceName
}

data class HistoryStats(
    val totalTransfers: Int = 0,
    val completedTransfers: Int = 0,
    val failedTransfers: Int = 0,
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val filesSent: Int = 0,
    val filesReceived: Int = 0,
) {
    val successRate: Float get() = if (totalTransfers == 0) 0f else completedTransfers.toFloat() / totalTransfers
}
