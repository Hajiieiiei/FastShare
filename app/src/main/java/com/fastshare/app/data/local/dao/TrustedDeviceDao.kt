package com.fastshare.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fastshare.app.data.local.entity.TrustedDeviceEntity
import kotlinx.coroutines.flow.Flow

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
