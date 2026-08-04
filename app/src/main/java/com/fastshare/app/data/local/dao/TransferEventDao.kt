package com.fastshare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fastshare.app.data.local.entity.TransferEventEntity

@Dao
interface TransferEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TransferEventEntity)

    @Query("SELECT * FROM transfer_events WHERE transferId = :transferId ORDER BY timestamp ASC")
    suspend fun forTransfer(transferId: String): List<TransferEventEntity>

    @Query("DELETE FROM transfer_events WHERE transferId = :transferId")
    suspend fun deleteForTransfer(transferId: String)
}
