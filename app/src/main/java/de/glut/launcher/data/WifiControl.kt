package de.glut.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class WifiNetwork(
    val ssid: String,
    val level: Int,
    val secure: Boolean,
    val connected: Boolean,
    val saved: Boolean,
    val band: String,
)

data class WifiState(
    val enabled: Boolean,
    val ssid: String?,
    val ip: String?,
    val gateway: String?,
    val rssi: Int,
    val speedMbps: Int,
    val band: String?,
)

/** WLAN über die klassische WifiManager-Schnittstelle (Fire OS 7 = Android 9). */
@Suppress("DEPRECATION")
class WifiControl(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private fun hasLocation() =
        app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** SSID und Suchergebnisse braucht Android nur mit Standortrecht – per Root vergeben. */
    suspend fun ensureAccess() {
        if (hasLocation()) return
        RootShell.run("pm grant ${app.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
        RootShell.run("settings put secure location_mode 3")
    }

    fun state(): WifiState {
        val info = manager.connectionInfo
        val connected = manager.isWifiEnabled && info != null && info.networkId != -1
        val ssid = info?.ssid?.trim('"')?.takeIf { connected && it != WifiManager.UNKNOWN_SSID }
        return WifiState(
            enabled = manager.isWifiEnabled,
            ssid = ssid,
            ip = if (connected) formatIp(info.ipAddress) else null,
            gateway = if (connected) formatIp(manager.dhcpInfo?.gateway ?: 0) else null,
            rssi = if (connected) info.rssi else 0,
            speedMbps = if (connected) info.linkSpeed else 0,
            band = if (connected) band(info.frequency) else null,
        )
    }

    suspend fun scan(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        ensureAccess()
        manager.startScan()
        delay(2500)
        val connectedSsid = state().ssid
        val saved = manager.configuredNetworks?.map { it.SSID.trim('"') }.orEmpty().toSet()
        (manager.scanResults ?: emptyList())
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (ssid, results) ->
                val best = results.maxBy { it.level }
                WifiNetwork(
                    ssid = ssid,
                    level = best.level,
                    secure = listOf("WPA", "WEP", "PSK", "EAP", "SAE").any { best.capabilities.contains(it) },
                    connected = ssid == connectedSsid,
                    saved = ssid in saved,
                    band = band(best.frequency),
                )
            }
            .sortedWith(compareByDescending<WifiNetwork> { it.connected }.thenByDescending { it.level })
    }

    fun setEnabled(enabled: Boolean) {
        manager.isWifiEnabled = enabled
    }

    /** [password] = null verbindet ein gespeichertes oder offenes Netzwerk. */
    fun connect(network: WifiNetwork, password: String?): Boolean {
        val saved = manager.configuredNetworks?.firstOrNull { it.SSID.trim('"') == network.ssid }
        val id = if (saved != null && password == null) {
            saved.networkId
        } else {
            val config = WifiConfiguration().apply {
                SSID = "\"${network.ssid}\""
                if (network.secure) preSharedKey = "\"$password\"" else allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            manager.addNetwork(config)
        }
        if (id < 0) return false
        manager.disconnect()
        val ok = manager.enableNetwork(id, true)
        manager.reconnect()
        manager.saveConfiguration()
        return ok
    }

    fun forget(ssid: String): Boolean {
        val id = manager.configuredNetworks?.firstOrNull { it.SSID.trim('"') == ssid }?.networkId ?: return false
        return manager.removeNetwork(id).also { manager.saveConfiguration() }
    }

    private fun band(frequency: Int) = if (frequency >= 4900) "5 GHz" else "2,4 GHz"

    private fun formatIp(value: Int) =
        "${value and 0xff}.${value shr 8 and 0xff}.${value shr 16 and 0xff}.${value shr 24 and 0xff}"
}
