package com.fastshare.app.services.security

import com.fastshare.app.data.local.dao.TrustedDeviceDao
import com.fastshare.app.data.local.entity.TrustedDeviceEntity
import com.fastshare.app.domain.model.ApprovalPolicy
import com.fastshare.app.domain.model.DeviceInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether an inbound connection may proceed and records pairing decisions.
 *
 * Decision order:
 *  1. Known device with a matching pinned fingerprint and `alwaysAllow` -> auto-accept.
 *  2. Known device whose fingerprint changed -> hard reject, surfaced as a security warning
 *     (this is the impersonation case, so it is never silently re-paired).
 *  3. Unknown device -> ask the user, showing the short fingerprint for out-of-band comparison.
 */
@Singleton
class PairingManager @Inject constructor(
    private val trustedDeviceDao: TrustedDeviceDao,
    private val fingerprintStore: TrustedFingerprintStore,
) {
    private val _securityAlerts = MutableSharedFlow<SecurityAlert>(extraBufferCapacity = 8)
    val securityAlerts: SharedFlow<SecurityAlert> = _securityAlerts.asSharedFlow()

    sealed interface Decision {
        data class AutoAccept(val trusted: TrustedDeviceEntity) : Decision
        data object AskUser : Decision
        data class Reject(val reason: String) : Decision
    }

    data class SecurityAlert(
        val deviceId: String,
        val deviceName: String,
        val expectedFingerprint: String,
        val actualFingerprint: String,
    )

    suspend fun evaluate(
        peer: DeviceInfo,
        presentedFingerprint: String,
        policy: ApprovalPolicy,
    ): Decision {
        val known = trustedDeviceDao.findById(peer.deviceId)

        if (known != null && !known.fingerprint.equals(presentedFingerprint, ignoreCase = true)) {
            _securityAlerts.tryEmit(
                SecurityAlert(peer.deviceId, peer.deviceName, known.fingerprint, presentedFingerprint),
            )
            return Decision.Reject("Certificate changed for a paired device")
        }

        return when {
            policy == ApprovalPolicy.ACCEPT_ALL -> known?.let { Decision.AutoAccept(it) } ?: Decision.AskUser
            known != null && known.alwaysAllow -> Decision.AutoAccept(known)
            policy == ApprovalPolicy.TRUSTED_AUTO && known != null -> Decision.AutoAccept(known)
            else -> Decision.AskUser
        }
    }

    /** Persists a pairing decision. [alwaysAllow] maps to the "Always allow" button. */
    suspend fun trust(peer: DeviceInfo, fingerprint: String, publicKey: String, alwaysAllow: Boolean) {
        val now = System.currentTimeMillis()
        val existing = trustedDeviceDao.findById(peer.deviceId)
        val entity = existing?.copy(
            deviceName = peer.deviceName,
            fingerprint = fingerprint,
            publicKey = publicKey,
            alwaysAllow = existing.alwaysAllow || alwaysAllow,
            lastIpAddress = peer.ipAddress,
            lastPort = peer.port,
            lastSeenAt = now,
        ) ?: TrustedDeviceEntity(
            deviceId = peer.deviceId,
            deviceName = peer.deviceName,
            nickname = null,
            platform = peer.platform,
            fingerprint = fingerprint,
            publicKey = publicKey,
            alwaysAllow = alwaysAllow,
            isFavorite = false,
            lastIpAddress = peer.ipAddress,
            lastPort = peer.port,
            pairedAt = now,
            lastSeenAt = now,
            transferCount = 0,
            totalBytesExchanged = 0,
        )
        trustedDeviceDao.upsert(entity)
        fingerprintStore.refresh()
    }

    suspend fun revoke(deviceId: String) {
        trustedDeviceDao.deleteById(deviceId)
        fingerprintStore.refresh()
    }

    suspend fun expectedFingerprint(deviceId: String): String? =
        trustedDeviceDao.findById(deviceId)?.fingerprint
}
