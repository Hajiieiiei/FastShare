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
