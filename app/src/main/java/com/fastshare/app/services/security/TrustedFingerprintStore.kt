package com.fastshare.app.services.security

import com.fastshare.app.data.local.dao.TrustedDeviceDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-through cache of paired-device fingerprints. Kept separate from the repository layer so
 * the TLS trust manager can consult it synchronously during a handshake without pulling in
 * higher-level dependencies.
 */
@Singleton
class TrustedFingerprintStore @Inject constructor(
    private val dao: TrustedDeviceDao,
) {
    @Volatile private var cache: Map<String, String> = emptyMap()

    /** deviceId -> fingerprint, refreshed by [refresh] and by the observing flow. */
    fun snapshot(): Map<String, String> = cache

    suspend fun refresh() {
        cache = dao.getAll().associate { it.deviceId to it.fingerprint }
    }

    fun observe(): Flow<Map<String, String>> =
        dao.observeAll().map { list -> list.associate { it.deviceId to it.fingerprint }.also { cache = it } }

    fun fingerprintFor(deviceId: String): String? = cache[deviceId]

    fun isTrusted(deviceId: String, fingerprint: String): Boolean =
        cache[deviceId]?.equals(fingerprint, ignoreCase = true) == true

    suspend fun isAlwaysAllowed(deviceId: String): Boolean =
        dao.findById(deviceId)?.alwaysAllow == true
}
