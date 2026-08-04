package com.fastshare.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Enumeration

object NetworkUtils {

    data class LocalAddress(val ipAddress: InetAddress, val interfaceName: String, val isWifi: Boolean)
    data class ConnectionSummary(val ip: String?, val isWifi: Boolean, val ssid: String?)

    fun bestLocalIpv4(): InetAddress? {
        var fallback: InetAddress? = null
        try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces() ?: return null
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses: Enumeration<InetAddress> = networkInterface.inetAddresses
                for (address in addresses) {
                    if (address !is Inet4Address || address.isLoopbackAddress || address.isLinkLocalAddress) continue
                    val bytes = address.address
                    val isPrivate = when {
                        bytes[0] == 10.toByte() -> true
                        bytes[0] == 172.toByte() && bytes[1] in 16..31 -> true
                        bytes[0] == 192.toByte() && bytes[1] == 168.toByte() -> true
                        else -> false
                    }
                    if (!isPrivate) continue
                    val name = networkInterface.name
                    if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) {
                        return address
                    }
                    if (fallback == null) fallback = address
                }
            }
        } catch (_: SocketException) {}
        return fallback
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun connectionSummary(context: Context): ConnectionSummary {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return ConnectionSummary(null, false, null)
        val caps = cm.getNetworkCapabilities(network)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val ssid = if (isWifi) wifiSsid(context) else null
        val ip = bestLocalIpv4()?.hostAddress
        return ConnectionSummary(ip, isWifi, ssid)
    }

    fun wifiSsid(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 30) {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val network = cm.activeNetwork ?: return null
            val linkProps = cm.getLinkProperties(network) ?: return null
            // On API 33+, NetworkCapabilities.ssid exists; on lower, we return the transport info which is not
            // accessible without location permission. Best-effort returning null is safe for our UI.
            return null
        }
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
    }

    fun isLocalTo(ip: InetAddress): Boolean {
        if (ip.isLoopbackAddress) return false
        return runCatching {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces() ?: return false
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp) continue
                for (address in networkInterface.inetAddresses) {
                    if (address !is Inet4Address || address.isLoopbackAddress) continue
                    val a = address.address
                    val b = ip.address
                    if (a.size != b.size) continue
                    var matches = true
                    for (i in a.indices) {
                        if (a[i] != b[i]) { matches = false; break }
                    }
                    if (matches) return true
                }
            }
            false
        }.getOrDefault(false)
    }

    suspend fun resolveHostname(host: String): InetAddress? = withContext(Dispatchers.IO) {
        runCatching { InetAddress.getByName(host) }.getOrNull()
    }
}
