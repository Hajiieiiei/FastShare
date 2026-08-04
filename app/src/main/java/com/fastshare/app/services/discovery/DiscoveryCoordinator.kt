package com.fastshare.app.services.discovery

import android.content.Context
import android.util.Log
import com.fastshare.app.data.local.datastore.SettingsRepository
import com.fastshare.app.data.network.discovery.DeviceRepository
import com.fastshare.app.data.network.discovery.MulticastDiscoveryEngine
import com.fastshare.app.data.network.discovery.NsdDiscoveryEngine
import com.fastshare.app.data.network.protocol.DiscoveryPacket
import com.fastshare.app.domain.model.Capability
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import com.fastshare.app.domain.model.DiscoverySource
import com.fastshare.app.services.security.IdentityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates NSD + UDP-multicast discovery into a single [DeviceRepository].
 *
 * Two engines are used together: mDNS for desktop/macOS interop and a UDP beacon for
 * instant Android-to-Android discovery where NsdManager is flaky on stock OEMs.
 */
@Singleton
class DiscoveryCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nsdEngine: NsdDiscoveryEngine,
    private val multicastEngine: MulticastDiscoveryEngine,
    private val deviceRepository: DeviceRepository,
    private val identityManager: IdentityManager,
    private val settingsRepository: SettingsRepository,
) {
    private val tag = "DiscoveryCoordinator"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var listenJob: Job? = null
    @Volatile private var multicastListenJob: Job? = null
    @Volatile private var nsdJob: Job? = null
    @Volatile private var prunerJob: Job? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        listenJob = scope.launch {
            val deviceId = identityManager.deviceId()
            val deviceName = identityManager.deviceName()
            val fingerprint = identityManager.fingerprint()
            val settings = settingsRepository.current()
            val port = if (settings.listenPort > 0) settings.listenPort else 53319
            deviceRepository.markDiscoveryStart()

            runCatching {
                nsdEngine.registerService(deviceName, deviceId, port, fingerprint)
            }.onFailure { Log.w(tag, "NSD register failed", it) }

            multicastEngine.startAnnouncing {
                DiscoveryPacket(
                    type = DiscoveryPacket.PacketType.ANNOUNCE,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    platform = DevicePlatform.ANDROID,
                    deviceType = DeviceType.PHONE,
                    appVersion = BuildConfig.VERSION_NAME,
                    port = port,
                    fingerprint = fingerprint,
                    capabilities = Capability.DEFAULTS.map { it.wire },
                    usesTls = settings.requireTls,
                )
            }

            multicastListenJob = scope.launch {
                multicastEngine.listen().collect { (packet, ip) ->
                    when (packet.type) {
                        DiscoveryPacket.PacketType.BYE -> deviceRepository.remove(packet.deviceId)
                        else -> {
                            val info = packet.toDeviceInfo(ip)
                            deviceRepository.upsert(info, source = DiscoverySource.MULTICAST)
                            deviceRepository.attachTrustMetadata()
                        }
                    }
                }
            }

            nsdJob = scope.launch {
                nsdEngine.discover().collect { info ->
                    deviceRepository.upsert(info, source = DiscoverySource.MDNS)
                    deviceRepository.attachTrustMetadata()
                }
            }

            prunerJob = scope.launch {
                while (isActive) {
                    delay(PRUNE_INTERVAL_MS)
                    deviceRepository.prune()
                }
            }
        }
    }

    fun rescan() { multicastEngine.sendQuery() }

    fun stop() {
        running = false
        multicastEngine.sendBye()
        multicastEngine.stopAnnouncing()
        nsdEngine.unregisterService()
        listenJob?.cancel(); listenJob = null
        multicastListenJob?.cancel(); multicastListenJob = null
        nsdJob?.cancel(); nsdJob = null
        prunerJob?.cancel(); prunerJob = null
        deviceRepository.clear()
    }

    private companion object { const val PRUNE_INTERVAL_MS = 30_000L }
}
