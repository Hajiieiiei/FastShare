package com.fastshare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fastshare.app.data.local.entity.TransferRecordEntity
import com.fastshare.app.data.local.entity.TransferRecordItemEntity
import com.fastshare.app.domain.model.TransferState
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_records ORDER BY startedAt DESC")
    fun observeRecords(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_records ORDER BY startedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<TransferRecordEntity>

    @Query("SELECT * FROM transfer_records WHERE id = :id")
    suspend fun findById(id: String): TransferRecordEntity?

    @Query("SELECT * FROM transfer_record_items WHERE recordId = :recordId")
    suspend fun itemsFor(recordId: String): List<TransferRecordItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TransferRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TransferRecordItemEntity>)

    @Query("UPDATE transfer_records SET state = :state, transferredBytes = :bytes, finishedAt = :finishedAt, durationMillis = :duration, error = :error WHERE id = :id")
    suspend fun updateState(id: String, state: TransferState, bytes: Long, finishedAt: Long?, duration: Long, error: String?)

    @Query("UPDATE transfer_records SET transferredBytes = :bytes WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long)

    @Query("DELETE FROM transfer_records WHERE startedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM transfer_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transfer_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM transfer_records")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM transfer_records WHERE direction = 'SEND' AND state = 'COMPLETED'")
    suspend fun totalBytesSent(): Long

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM transfer_records WHERE direction = 'RECEIVE' AND state = 'COMPLETED'")
    suspend fun totalBytesReceived(): Long

    @Query("SELECT COALESCE(SUM(itemCount), 0) FROM transfer_records WHERE direction = 'SEND' AND state = 'COMPLETED'")
    suspend fun totalFilesSent(): Int

    @Query("SELECT COALESCE(SUM(itemCount), 0) FROM transfer_records WHERE direction = 'RECEIVE' AND state = 'COMPLETED'")
    suspend fun totalFilesReceived(): Int

    @Query("SELECT COUNT(*) FROM transfer_records WHERE state = 'COMPLETED'")
    suspend fun completedCount(): Int

    @Query("SELECT COUNT(*) FROM transfer_records WHERE state = 'FAILED' OR state = 'CANCELLED' OR state = 'REJECTED'")
    suspend fun failedCount(): Int
}

@Dao
interface TrustedDeviceDao {
    @Query("SELECT * FROM trusted_devices ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<TrustedDeviceEntity>>

    @Query("SELECT * FROM trusted_devices ORDER BY lastSeenAt DESC")
    suspend fun getAll(): List<TrustedDeviceEntity>

    @Query("SELECT * FROM trusted_devices WHERE deviceId = :deviceId")
    suspend fun findById(deviceId: String): TrustedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrustedDeviceEntity)

    @Query("UPDATE trusted_devices SET nickname = :nickname WHERE deviceId = :deviceId")
    suspend fun setNickname(deviceId: String, nickname: String?)

    @Query("UPDATE trusted_devices SET isFavorite = :favorite WHERE deviceId = :deviceId")
    suspend fun setFavorite(deviceId: String, favorite: Boolean)

    @Query("UPDATE trusted_devices SET alwaysAllow = :alwaysAllow WHERE deviceId = :deviceId")
    suspend fun setAlwaysAllow(deviceId: String, alwaysAllow: Boolean)

    @Query("UPDATE trusted_devices SET lastIpAddress = :ip, lastPort = :port, lastSeenAt = :seenAt WHERE deviceId = :deviceId")
    suspend fun touch(deviceId: String, ip: String?, port: Int, seenAt: Long)

    @Query("UPDATE trusted_devices SET transferCount = transferCount + :count, totalBytesExchanged = totalBytesExchanged + :bytes WHERE deviceId = :deviceId")
    suspend fun recordTransfer(deviceId: String, count: Int, bytes: Long)

    @Query("DELETE FROM trusted_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)

    @Query("DELETE FROM trusted_devices")
    suspend fun clearAll()
}

@Dao
interface TransferEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TransferEventEntity)

    @Query("SELECT * FROM transfer_events WHERE transferId = :transferId ORDER BY timestamp ASC")
    suspend fun forTransfer(transferId: String): List<TransferEventEntity>

    @Query("DELETE FROM transfer_events WHERE transferId = :transferId")
    suspend fun deleteForTransfer(transferId: String)
}
