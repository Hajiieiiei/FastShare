package com.fastshare.app.data.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.fastshare.app.domain.model.DeviceInfo
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * mDNS/DNS-SD discovery via the platform NsdManager. Registers our own service so
 * Desktop/macOS peers that speak DNS-SD natively can see us, and resolves
 * `_fastshare._tcp` services advertised by peers.
 *
 * NsdManager on stock Android does not reliably announce over multicast for other Android
 * devices; the companion [MulticastDiscoveryEngine] covers that path.
 */
@Singleton
class NsdDiscoveryEngine @Inject constructor(
    private val context: Context,
) {
    private val tag = "NsdDiscovery"

    @Volatile private var manager: NsdManager? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var registered = false

    @Volatile var servicePort: Int = 0

    fun registerService(deviceName: String, deviceId: String, port: Int, fingerprint: String) {
        servicePort = port
        val nsdManager = manager ?: context.getSystemService(NsdManager::class.java).also { manager = it }
        unregisterService()
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registered = true
                Log.d(tag, "Registered ${info.serviceName} on ${info.port}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(tag, "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                registered = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(tag, "Unregistration failed: $errorCode")
            }
        }
        registrationListener = listener
        val info = NsdServiceInfo().apply {
            serviceName = "$deviceName-$deviceId".take(62)
            serviceType = "_fastshare._tcp."
            setPort(port)
            setAttribute("id", deviceId)
            setAttribute("pf", DevicePlatform.ANDROID.name.lowercase())
            setAttribute("av", com.fastshare.app.BuildConfig.VERSION_NAME)
            // First 16 hex chars of the certificate fingerprint — enough to display in the card.
            setAttribute("fp", fingerprint.take(16))
        }
        runCatching {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { Log.e(tag, "registerService failed", it) }
    }

    fun unregisterService() {
        val nsdManager = manager ?: return
        val listener = registrationListener ?: return
        if (registered) {
            runCatching { nsdManager.unregisterService(listener) }
            registered = false
        }
        registrationListener = null
    }

    /** Emits resolved peers; re-runs discovery each time the flow is collected. */
    fun discover(): Flow<DeviceInfo> = callbackFlow {
        val nsdManager = manager ?: context.getSystemService(NsdManager::class.java).also { manager = it }
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(tag, "Discovery started for $serviceType")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                Log.d(tag, "Found ${info.serviceName} type=${info.serviceType}")
                if (info.serviceType != "_fastshare._tcp.") return
                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(tag, "Resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val attributes = resolved.attributes
                        val deviceId = attributes["id"]?.stringContent()
                            ?: resolved.serviceName.substringAfterLast('-')
                        val platformRaw = attributes["pf"]?.stringContent()
                        val appVersion = attributes["av"]?.stringContent()
                        val fingerprintPrefix = attributes["fp"]?.stringContent().orEmpty()
                        val baseName = resolved.serviceName.substringBeforeLast('-').ifBlank { resolved.serviceName }
                        val device = DeviceInfo(
                            deviceId = deviceId,
                            deviceName = baseName,
                            platform = DevicePlatform.fromWire(platformRaw),
                            deviceType = DeviceType.UNKNOWN,
                            appVersion = appVersion ?: "",
                            protocolVersion = 1,
                            ipAddress = resolved.host?.hostAddress ?: "",
                            port = resolved.port,
                            capabilities = com.fastshare.app.domain.model.Capability.DEFAULTS,
                            fingerprint = fingerprintPrefix,
                            usesTls = true,
                        )
                        if (device.isAddressable) trySend(device)
                    }
                })
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                Log.d(tag, "Lost ${info.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(tag, "Start discovery failed: $errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(tag, "Stop discovery failed: $errorCode")
            }
        }
        runCatching {
            nsdManager.discoverServices("_fastshare._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { close(it) }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }

    /** NsdServiceInfo attribute keys map to byte arrays; this helper decodes UTF-8 strings. */
    private fun ByteArray.stringContent(): String = String(this, Charsets.UTF_8)
    private fun Map<String, ByteArray>.stringContent(key: String): String? = this[key]?.stringContent()
}
