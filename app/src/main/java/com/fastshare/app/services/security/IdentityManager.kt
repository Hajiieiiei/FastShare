package com.fastshare.app.services.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fastshare.app.data.local.datastore.identityDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns this device's stable identity: a persistent UUID, the user-facing device name,
 * and the long-lived TLS keystore. The UUID is generated once and stored in DataStore —
 * never derived from hardware IDs, so the app carries no cross-app trackable identifier.
 */
@Singleton
class IdentityManager @Inject constructor(
    private val context: Context,
    private val certificateProvider: CertificateProvider,
) {
    private val mutex = Mutex()

    @Volatile private var cachedDeviceId: String? = null
    @Volatile private var cachedDeviceName: String? = null

    suspend fun deviceId(): String = cachedDeviceId ?: mutex.withLock {
        cachedDeviceId ?: withContext(Dispatchers.IO) {
            val store = context.identityDataStore
            val existing = store.data.first()[KEY_DEVICE_ID]
            val id = existing ?: UUID.randomUUID().toString()
            if (existing == null) store.edit { it[KEY_DEVICE_ID] = id }
            cachedDeviceId = id
            id
        }
    }

    suspend fun deviceName(): String = cachedDeviceName ?: mutex.withLock {
        cachedDeviceName ?: withContext(Dispatchers.IO) {
            val store = context.identityDataStore
            val existing = store.data.first()[KEY_DEVICE_NAME]
            val name = existing ?: defaultDeviceName()
            if (existing == null) store.edit { it[KEY_DEVICE_NAME] = name }
            cachedDeviceName = name
            name
        }
    }

    suspend fun setDeviceName(name: String) {
        val sanitized = name.trim().take(MAX_NAME_LENGTH).ifBlank { defaultDeviceName() }
        withContext(Dispatchers.IO) {
            context.identityDataStore.edit { it[KEY_DEVICE_NAME] = sanitized }
        }
        cachedDeviceName = sanitized
    }

    /** SHA-256 of the DER-encoded TLS certificate; the value users compare when pairing. */
    suspend fun fingerprint(): String = certificateProvider.fingerprint()

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        val raw = when {
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isBlank() -> model
            else -> "$manufacturer $model"
        }.trim()
        val fallback = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        return (fallback?.takeIf { it.isNotBlank() } ?: raw.ifBlank { "Android Device" }).take(MAX_NAME_LENGTH)
    }

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        const val MAX_NAME_LENGTH = 40
    }
}
