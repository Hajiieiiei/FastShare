package com.fastshare.app.data.network.discovery

import com.fastshare.app.data.local.dao.TrustedDeviceDao
import com.fastshare.app.domain.model.DeviceInfo
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DiscoveredDevice
import com.fastshare.app.domain.model.SignalStrength
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory registry of currently visible peers, deduplicated by deviceId so DHCP renewal
 * or dual discovery paths (mDNS + multicast) collapse into one row. Rows are re-keyed by IP
 * at lookup time, so a peer whose IP changed is found under its new address automatically.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val trustedDeviceDao: TrustedDeviceDao,
) {
    private val _devices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val devices: StateFlow<Map<String, DiscoveredDevice>> = _devices.asStateFlow()

    private val _lastManualDevice = MutableStateFlow<DeviceInfo?>(null)
    val lastManualDevice: StateFlow<DeviceInfo?> = _lastManualDevice.asStateFlow()

    @Volatile private var discoveryStartedAt: Long = 0L

    fun upsert(info: DeviceInfo, rttMillis: Long? = null, source: DiscoverySource = DiscoverySource.MDNS) {
        if (!info.isAddressable) return
        val now = System.currentTimeMillis()
        _devices.update { map ->
            val existing = map[info.deviceId]
            val isStaleEntry = existing != null && now - existing.lastSeenAt > ENTRY_TTL_MS
            val merged = if (existing != null && !isStaleEntry) {
                // Preserve the manually-entered source if the user added this device by IP.
                val mergedSource = if (existing.source == DiscoverySource.MANUAL) existing.source else source
                existing.copy(
                    info = existing.info.copy(
                        deviceName = info.deviceName,
                        platform = info.platform,
                        deviceType = info.deviceType,
                        appVersion = info.appVersion,
                        ipAddress = info.ipAddress,
                        port = info.port,
                        capabilities = existing.info.capabilities.ifEmpty { info.capabilities },
                        fingerprint = existing.info.fingerprint.ifEmpty { info.fingerprint },
                    ),
                    lastSeenAt = now,
                    rttMillis = rttMillis ?: existing.rttMillis,
                    signalStrength = SignalStrength.fromRtt(rttMillis ?: existing.rttMillis),
                    source = mergedSource,
                )
            } else {
                DiscoveredDevice(
                    info = info,
                    lastSeenAt = now,
                    signalStrength = SignalStrength.fromRtt(rttMillis),
                    rttMillis = rttMillis,
                    source = source,
                    isTrusted = false,
                    isFavorite = false,
                    nickname = null,
                )
            }
            map + (info.deviceId to merged)
        }
    }

    suspend fun attachTrustMetadata() {
        val trusted = trustedDeviceDao.getAll().associateBy { it.deviceId }
        _devices.update { map ->
            map.mapValues { (id, device) ->
                val entry = trusted[id]
                if (entry == null) device
                else device.copy(isTrusted = true, isFavorite = entry.isFavorite, nickname = entry.nickname)
            }
        }
    }

    fun remove(deviceId: String) {
        _devices.update { it - deviceId }
    }

    fun removeByIp(ip: String) {
        _devices.update { map -> map.filterNot { (_, d) -> d.info.ipAddress == ip } }
    }

    /** Prunes peers we have not heard from in [staleTimeoutMillis]. */
    fun prune(staleTimeoutMillis: Long = SELF_STALE_TIMEOUT_MS, now: Long = System.currentTimeMillis()) {
        _devices.update { map -> map.filterNot { (_, d) -> d.isStale(now, staleTimeoutMillis) } }
    }

    fun deviceById(deviceId: String): DiscoveredDevice? = _devices.value[deviceId]

    fun deviceByIp(ip: String): DiscoveredDevice? = _devices.value.values.firstOrNull { it.info.ipAddress == ip }

    suspend fun findDevice(deviceId: String, fallbackIp: String?, fallbackPort: Int): DiscoveredDevice? {
        val inMemory = deviceById(deviceId)
        if (inMemory != null) return inMemory
        if (fallbackIp != null && fallbackPort > 0) {
            val trusted = trustedDeviceDao.findById(deviceId)
            val info = DeviceInfo(
                deviceId = deviceId,
                deviceName = trusted?.deviceName ?: deviceId,
                platform = trusted?.platform ?: DevicePlatform.UNKNOWN,
                appVersion = "",
                protocolVersion = 1,
                ipAddress = fallbackIp,
                port = fallbackPort,
                capabilities = emptySet(),
                fingerprint = trusted?.fingerprint ?: "",
                usesTls = true,
            )
            val discovered = DiscoveredDevice(
                info = info, lastSeenAt = System.currentTimeMillis(), source = DiscoverySource.HISTORY,
                isTrusted = trusted != null,
            )
            _devices.update { it + (deviceId to discovered) }
            return discovered
        }
        return null
    }

    fun setManualDevice(info: DeviceInfo) {
        _lastManualDevice.value = info
        upsert(info, source = DiscoverySource.MANUAL)
    }

    fun markDiscoveryStart() { discoveryStartedAt = System.currentTimeMillis() }

    fun timeSinceDiscoveryStart(): Long = System.currentTimeMillis() - discoveryStartedAt

    fun clear() {
        _devices.value = emptyMap()
    }

    companion object {
        /** Devices expire 3x the announcement interval; a missing heartbeat means the peer left. */
        const val SELF_STALE_TIMEOUT_MS = 30_000L
        /** Individual row TTL is longer so a temporarily-silent peer is not recreated as unknown. */
        const val ENTRY_TTL_MS = 120_000L
    }
}

private typealias DiscoverySource = com.fastshare.app.domain.model.DiscoverySource
