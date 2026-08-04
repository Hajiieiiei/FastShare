package com.fastshare.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.PayloadKind
import com.fastshare.app.domain.model.TransferDirection
import com.fastshare.app.domain.model.TransferState

@Entity(tableName = "transfer_records")
data class TransferRecordEntity(
    @PrimaryKey val id: String,
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
)

@Entity(
    tableName = "transfer_record_items",
    foreignKeys = [
        ForeignKey(
            entity = TransferRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordId")],
)
data class TransferRecordItemEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val kind: PayloadKind,
    val state: TransferState,
    val localUri: String?,
    val sha256: String?,
)

@Entity(tableName = "trusted_devices")
data class TrustedDeviceEntity(
    @PrimaryKey val deviceId: String,
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
)

@Entity(tableName = "transfer_events")
data class TransferEventEntity(
    @PrimaryKey val id: String,
    val transferId: String,
    val timestamp: Long,
    val eventType: String,
    val payload: String,
)
