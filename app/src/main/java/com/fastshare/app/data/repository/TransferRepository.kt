package com.fastshare.app.data.repository

import com.fastshare.app.data.local.dao.TransferDao
import com.fastshare.app.data.local.dao.TrustedDeviceDao
import com.fastshare.app.data.local.entity.TransferRecordEntity
import com.fastshare.app.data.local.entity.TransferRecordItemEntity
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.HistoryStats
import com.fastshare.app.domain.model.TransferDirection
import com.fastshare.app.domain.model.TransferRecord
import com.fastshare.app.domain.model.TransferRecordItem
import com.fastshare.app.domain.model.TransferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val transferDao: TransferDao,
    private val trustedDeviceDao: TrustedDeviceDao,
) {
    val history: Flow<List<TransferRecord>> = flow {
        // Re-emit the latest page; the DAO's observe is used via combine below when wired.
        transferDao.observeRecords().collect { entities ->
            emit(entities.map { it.toDomain() })
        }
    }

    val stats: Flow<HistoryStats> = flow {
        emit(
            HistoryStats(
                totalTransfers = transferDao.count(),
                completedTransfers = transferDao.completedCount(),
                failedTransfers = transferDao.failedCount(),
                bytesSent = transferDao.totalBytesSent(),
                bytesReceived = transferDao.totalBytesReceived(),
                filesSent = transferDao.totalFilesSent(),
                filesReceived = transferDao.totalFilesReceived(),
            ),
        )
    }

    suspend fun getRecord(id: String): TransferRecord? {
        val entity = transferDao.findById(id) ?: return null
        val items = transferDao.itemsFor(id).map { it.toDomain(id) }
        return entity.toDomain(items)
    }

    suspend fun saveCompleted(
        id: String,
        direction: TransferDirection,
        peerDeviceId: String,
        peerName: String,
        peerPlatform: DevicePlatform,
        items: List<TransferRecordItem>,
        totalBytes: Long,
        transferredBytes: Long,
        state: TransferState,
        startedAt: Long,
        finishedAt: Long?,
        destination: String?,
        error: String?,
    ) {
        val duration = (finishedAt ?: System.currentTimeMillis()) - startedAt
        val entity = TransferRecordEntity(
            id = id,
            direction = direction,
            peerDeviceId = peerDeviceId,
            peerName = peerName,
            peerPlatform = peerPlatform,
            itemCount = items.size,
            totalBytes = totalBytes,
            transferredBytes = transferredBytes,
            state = state,
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMillis = duration.coerceAtLeast(0),
            averageBytesPerSecond = if (duration > 0) (transferredBytes * 1000 / duration) else 0,
            destination = destination,
            error = error,
        )
        val itemEntities = items.map { item ->
            TransferRecordItemEntity(
                id = item.id,
                recordId = id,
                name = item.name,
                size = item.size,
                mimeType = item.mimeType,
                kind = item.kind,
                state = item.state,
                localUri = item.localUri,
                sha256 = item.sha256,
            )
        }
        trustedDeviceDao.recordTransfer(
            deviceId = peerDeviceId,
            count = items.size,
            bytes = if (state == TransferState.COMPLETED) transferredBytes else 0,
        )
    }

    suspend fun deleteRecord(id: String) = transferDao.deleteById(id)

    suspend fun clearHistory() = transferDao.clearAll()

    suspend fun deleteOlderThan(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        transferDao.deleteOlderThan(cutoff)
    }

    suspend fun saveRecord(
        entity: TransferRecordEntity,
        itemEntities: List<TransferRecordItemEntity>,
    ) {
        transferDao.insertRecord(entity)
        transferDao.insertItems(itemEntities)
    }

    private fun TransferRecordEntity.toDomain(items: List<TransferRecordItem> = emptyList()) = TransferRecord(
        id = id,
        direction = direction,
        peerDeviceId = peerDeviceId,
        peerName = peerName,
        peerPlatform = peerPlatform,
        itemCount = itemCount,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        state = state,
        startedAt = startedAt,
        finishedAt = finishedAt,
        durationMillis = durationMillis,
        averageBytesPerSecond = averageBytesPerSecond,
        destination = destination,
        error = error,
        items = items,
    )

    private fun TransferRecordItemEntity.toDomain(recordId: String) = TransferRecordItem(
        id = id,
        recordId = recordId,
        name = name,
        size = size,
        mimeType = mimeType,
        kind = kind,
        state = state,
        localUri = localUri,
        sha256 = sha256,
    )
}
