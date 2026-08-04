package com.fastshare.app.data.local.db

import androidx.room.TypeConverter
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.PayloadKind
import com.fastshare.app.domain.model.TransferDirection
import com.fastshare.app.domain.model.TransferState

class Converters {
    @TypeConverter fun directionToString(d: TransferDirection): String = d.name
    @TypeConverter fun stringToDirection(v: String): TransferDirection = TransferDirection.valueOf(v)

    @TypeConverter fun stateToString(s: TransferState): String = s.name
    @TypeConverter fun stringToState(v: String): TransferState = TransferState.valueOf(v)

    @TypeConverter fun platformToString(p: DevicePlatform): String = p.name
    @TypeConverter fun stringToPlatform(v: String): DevicePlatform = DevicePlatform.valueOf(v)

    @TypeConverter fun kindToString(k: PayloadKind): String = k.name
    @TypeConverter fun stringToKind(v: String): PayloadKind = PayloadKind.valueOf(v)
}
