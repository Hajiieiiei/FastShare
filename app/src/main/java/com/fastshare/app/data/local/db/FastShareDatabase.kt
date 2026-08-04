package com.fastshare.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fastshare.app.data.local.dao.TransferDao
import com.fastshare.app.data.local.dao.TransferEventDao
import com.fastshare.app.data.local.dao.TrustedDeviceDao
import com.fastshare.app.data.local.entity.TransferRecordEntity
import com.fastshare.app.data.local.entity.TransferRecordItemEntity
import com.fastshare.app.data.local.entity.TransferEventEntity
import com.fastshare.app.data.local.entity.TrustedDeviceEntity

@Database(
    entities = [
        TransferRecordEntity::class,
        TransferRecordItemEntity::class,
        TrustedDeviceEntity::class,
        TransferEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FastShareDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
    abstract fun trustedDeviceDao(): TrustedDeviceDao
    abstract fun transferEventDao(): TransferEventDao

    companion object {
        const val NAME = "fastshare.db"
    }
}
