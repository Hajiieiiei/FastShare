package com.fastshare.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PayloadKind {
    @SerialName("file") FILE,
    @SerialName("image") IMAGE,
    @SerialName("video") VIDEO,
    @SerialName("audio") AUDIO,
    @SerialName("document") DOCUMENT,
    @SerialName("apk") APK,
    @SerialName("archive") ARCHIVE,
    @SerialName("text") TEXT,
    @SerialName("clipboard") CLIPBOARD,
    @SerialName("contact") CONTACT;

    companion object {
        fun fromMime(mime: String?, name: String): PayloadKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                mime?.startsWith("image/") == true -> IMAGE
                mime?.startsWith("video/") == true -> VIDEO
                mime?.startsWith("audio/") == true -> AUDIO
                ext == "apk" || mime == "application/vnd.android.package-archive" -> APK
                ext in setOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2") -> ARCHIVE
                ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "odt") -> DOCUMENT
                else -> FILE
            }
        }
    }
}

/**
 * One entry in a transfer. [relativePath] preserves folder structure so a whole directory
 * can be reconstructed byte-identically on the receiver.
 */
@Serializable
data class TransferItem(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long,
    @SerialName("mimeType") val mimeType: String = "application/octet-stream",
    @SerialName("kind") val kind: PayloadKind = PayloadKind.FILE,
    @SerialName("relativePath") val relativePath: String = name,
    @SerialName("sha256") val sha256: String? = null,
    @SerialName("lastModified") val lastModified: Long = 0L,
    @SerialName("inlineContent") val inlineContent: String? = null,
) {
    /** Local content URI (sender side only); never serialized across the wire. */
    @kotlinx.serialization.Transient
    var localUri: String? = null

    val isInline: Boolean get() = inlineContent != null
}

enum class TransferDirection { SEND, RECEIVE }

enum class TransferState {
    PENDING,
    AWAITING_APPROVAL,
    CONNECTING,
    IN_PROGRESS,
    PAUSED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REJECTED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == REJECTED
    val isActive: Boolean get() = this == CONNECTING || this == IN_PROGRESS || this == VERIFYING
}

/** Per-item progress inside a session. */
data class ItemProgress(
    val itemId: String,
    val name: String,
    val totalBytes: Long,
    val transferredBytes: Long = 0L,
    val state: TransferState = TransferState.PENDING,
    val error: String? = null,
) {
    val fraction: Float get() = if (totalBytes <= 0) 0f else (transferredBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

/** Aggregate, UI-facing snapshot of a transfer session. */
data class TransferSession(
    val id: String,
    val direction: TransferDirection,
    val peer: DeviceInfo,
    val items: List<TransferItem>,
    val state: TransferState = TransferState.PENDING,
    val totalBytes: Long = items.sumOf { it.size },
    val transferredBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val etaMillis: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val currentItemId: String? = null,
    val itemProgress: Map<String, ItemProgress> = emptyMap(),
    val error: String? = null,
    val destinationTree: String? = null,
) {
    val fraction: Float get() = if (totalBytes <= 0) 0f else (transferredBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
    val remainingBytes: Long get() = (totalBytes - transferredBytes).coerceAtLeast(0)
    val completedItems: Int get() = itemProgress.values.count { it.state == TransferState.COMPLETED }
    val currentItemName: String? get() = currentItemId?.let { id -> items.firstOrNull { it.id == id }?.name }
}

/** An inbound request that the user must approve or reject. */
data class IncomingRequest(
    val sessionId: String,
    val peer: DeviceInfo,
    val items: List<TransferItem>,
    val totalBytes: Long,
    val receivedAt: Long = System.currentTimeMillis(),
    val autoAccepted: Boolean = false,
) {
    val fileCount: Int get() = items.size
    val isTextOnly: Boolean get() = items.isNotEmpty() && items.all { it.kind == PayloadKind.TEXT || it.kind == PayloadKind.CLIPBOARD }
}
