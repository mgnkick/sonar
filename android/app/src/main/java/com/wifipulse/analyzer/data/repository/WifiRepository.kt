package com.wifipulse.analyzer.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.DhcpInfo
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.wifipulse.analyzer.data.model.SecurityType
import com.wifipulse.analyzer.data.model.WifiBand
import com.wifipulse.analyzer.data.model.WifiConnectionDetails
import com.wifipulse.analyzer.data.model.WifiNetwork
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress
import java.net.Inet4Address
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WifiRepository(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val connectivityManager: ConnectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isWifiEnabled: Boolean
        get() = wifiManager.isWifiEnabled

    /**
     * Triggers a Wi-Fi scan via WifiManager.
     * Note: Android 9+ throttles foreground scans to 4 times per 2-minute window.
     */
    fun startScan(): Boolean {
        @Suppress("DEPRECATION")
        return try {
            wifiManager.startScan()
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Flow of scan results emitting whenever SCAN_RESULTS_AVAILABLE_ACTION broadcast is received.
     */
    fun observeScanResults(): Flow<List<WifiNetwork>> = callbackFlow {
        // Some OEM Android builds broadcast SCAN_RESULTS_AVAILABLE_ACTION with
        // EXTRA_RESULTS_UPDATED=false even though scanResults still contains usable
        // cached observations. Keep the last non-empty list when the platform reports
        // no update and its cache is temporarily empty.
        var lastPublishedResults: List<WifiNetwork> = emptyList()

        fun readAndPublishScanResults(resultsUpdated: Boolean) {
            try {
                @Suppress("DEPRECATION")
                val rawResults = wifiManager.scanResults ?: emptyList()
                val mapped = mapScanResults(rawResults).sortedByDescending { it.rssi }
                if (mapped.isNotEmpty() || resultsUpdated || lastPublishedResults.isEmpty()) {
                    lastPublishedResults = mapped
                    trySend(mapped)
                } else {
                    // No fresh result and an empty OEM cache: retain known observations.
                    trySend(lastPublishedResults)
                }
            } catch (e: SecurityException) {
                // Do not erase usable observations if the OS temporarily denies a read.
                if (lastPublishedResults.isNotEmpty()) trySend(lastPublishedResults)
            } catch (e: RuntimeException) {
                // Vendor Wi-Fi implementations can throw while the radio is transitioning.
                if (lastPublishedResults.isNotEmpty()) trySend(lastPublishedResults)
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                val resultsUpdated = intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED,
                    false
                )
                readAndPublishScanResults(resultsUpdated)
            }
        }

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, intentFilter)
        }

        // Read the system cache at subscription time, without assuming it is fresh.
        readAndPublishScanResults(resultsUpdated = false)

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (ignored: IllegalArgumentException) {
                // Receiver was not registered or already unregistered
            }
        }
    }

    /**
     * Retrieves current active Wi-Fi connection details.
     */
    fun getCurrentConnectionDetails(): WifiConnectionDetails {
        val activeNetwork = connectivityManager.activeNetwork
        val activeCaps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val allNetworks = connectivityManager.allNetworks
        val capabilities = allNetworks.mapNotNull { network ->
            connectivityManager.getNetworkCapabilities(network)
        }
        val vpnActive = capabilities.any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } ||
            activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        // Prefer the active network when it is Wi-Fi; otherwise use the available
        // Wi-Fi transport. On some devices VPN is activeNetwork while Wi-Fi is still
        // the underlying transport, so retain the allNetworks fallback.
        val activeIsWifi = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wifiNetwork = if (activeIsWifi) {
            activeNetwork
        } else {
            allNetworks.firstOrNull { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        }
        val wifiCaps = wifiNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val mobileActive = wifiNetwork == null && (
            activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true ||
                (vpnActive && capabilities.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) })
            )

        if (wifiNetwork == null || wifiCaps == null) {
            return WifiConnectionDetails(
                isConnected = false,
                isVpnActive = vpnActive,
                isMobileDataActive = mobileActive
            )
        }

        // Android 10+ exposes connection information through the Wi-Fi network's
        // NetworkCapabilities. Prefer that supported path; retain the legacy fallback
        // for older Android versions and OEMs that omit transportInfo.
        val transportWifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiCaps.transportInfo as? WifiInfo
        } else null
        @Suppress("DEPRECATION")
        val wifiInfo: WifiInfo? = transportWifiInfo ?: wifiManager.connectionInfo
        @Suppress("DEPRECATION")
        val dhcpInfo: DhcpInfo? = wifiManager.dhcpInfo

        val rawSsid = wifiInfo?.ssid?.trim('"')?.trim() ?: ""
        val ssidUnavailable = rawSsid.isBlank() ||
            rawSsid.equals("<unknown ssid>", ignoreCase = true) ||
            rawSsid.equals("unknown ssid", ignoreCase = true) ||
            rawSsid == "0x"
        val ssid = if (ssidUnavailable) {
            "Подключено к Wi-Fi (SSID скрыт системой)"
        } else rawSsid
        val bssid = wifiInfo?.bssid
            ?.takeIf { it.isNotBlank() && !it.equals("02:00:00:00:00:00", ignoreCase = true) }
            ?: "BSSID недоступен"
        val rssi = wifiInfo?.rssi ?: -127
        val linkSpeed = wifiInfo?.linkSpeed ?: 0
        val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo?.frequency ?: 0 else 0
        val routeGateway = wifiNetwork?.let { network ->
            connectivityManager.getLinkProperties(network)?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway?.hostAddress
        }

        val txSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wifiInfo?.txLinkSpeedMbps ?: linkSpeed else linkSpeed
        val rxSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wifiInfo?.rxLinkSpeedMbps ?: linkSpeed else linkSpeed
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (wifiInfo?.wifiStandard) {
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                ScanResult.WIFI_STANDARD_LEGACY -> "Legacy (802.11a/b/g)"
                else -> if (frequency > 5000) "Wi-Fi 5/6" else "Wi-Fi 4 (802.11n)"
            }
        } else {
            if (frequency > 5000) "Wi-Fi 5 (802.11ac)" else "Wi-Fi 4 (802.11n)"
        }

        return WifiConnectionDetails(
            isConnected = true,
            ssid = ssid,
            bssid = bssid,
            rssi = rssi,
            ipAddress = formatIpAddress(dhcpInfo?.ipAddress ?: 0),
            gateway = routeGateway?.takeIf { it.isNotBlank() } ?: formatIpAddress(dhcpInfo?.gateway ?: 0),
            subnetMask = formatIpAddress(dhcpInfo?.netmask ?: 0),
            dns1 = formatIpAddress(dhcpInfo?.dns1 ?: 0),
            dns2 = formatIpAddress(dhcpInfo?.dns2 ?: 0),
            linkSpeedMbps = linkSpeed,
            txLinkSpeedMbps = txSpeed,
            rxLinkSpeedMbps = rxSpeed,
            frequency = frequency,
            standard = standard,
            macAddress = wifiInfo?.macAddress ?: "02:00:00:00:00:00",
            isHidden = wifiInfo?.hiddenSSID ?: false,
            isVpnActive = vpnActive,
            isMobileDataActive = mobileActive
        )
    }

    private fun formatIpAddress(ip: Int): String {
        return if (ip == 0) "0.0.0.0" else {
            try {
                val byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ip)
                InetAddress.getByAddress(byteBuffer.array()).hostAddress ?: "0.0.0.0"
            } catch (e: Exception) {
                "0.0.0.0"
            }
        }
    }

    private fun mapScanResults(results: List<ScanResult>): List<WifiNetwork> {
        val connected = getCurrentConnectionDetails()
        val mapped = results.mapNotNull { it.toWifiNetwork(connected) }.toMutableList()
        val validBssid = connected.bssid.isNotBlank() &&
            connected.bssid != "BSSID недоступен" &&
            connected.bssid != "02:00:00:00:00:00"
        if (connected.isConnected && validBssid && connected.frequency > 0) {
            val existingIndex = mapped.indexOfFirst { it.bssid.equals(connected.bssid, ignoreCase = true) }
            val connectedBand = when {
                connected.frequency in 2400..2500 -> WifiBand.BAND_2_4_GHZ
                connected.frequency in 4900..5900 -> WifiBand.BAND_5_GHZ
                connected.frequency in 5925..7125 -> WifiBand.BAND_6_GHZ
                else -> WifiBand.UNKNOWN
            }
            val knownSsid = connected.ssid.isNotBlank() &&
                !connected.ssid.startsWith("Подключено к Wi-Fi") &&
                !connected.ssid.equals("<unknown ssid>", ignoreCase = true)
            val inferredSsid = mapped.filter {
                it.band == connectedBand && it.ssid.isNotBlank() && it.ssid != "[Скрытая сеть]"
            }.groupingBy { it.ssid }.eachCount().entries.sortedByDescending { it.value }
                .let { names ->
                    val first = names.firstOrNull()
                    if (first != null && first.value >= 2 &&
                        (names.size == 1 || first.value > (names.getOrNull(1)?.value ?: 0))) first.key else null
                }
            val displaySsid = when {
                knownSsid -> connected.ssid
                inferredSsid != null -> inferredSsid + " · имя предположительно"
                else -> "[Подключено · SSID скрыт системой]"
            }
            if (existingIndex >= 0) {
                val existing = mapped[existingIndex]
                if (!knownSsid && inferredSsid != null) mapped[existingIndex] = existing.copy(ssid = displaySsid)
            } else {
                mapped += WifiNetwork(
                    ssid = displaySsid,
                    bssid = connected.bssid,
                    rssi = connected.rssi.coerceIn(-100, 0),
                    frequency = connected.frequency,
                    channel = frequencyToChannel(connected.frequency),
                    band = connectedBand,
                    channelWidth = 20,
                    securityType = SecurityType.OPEN,
                    capabilities = "Текущие параметры подключения Android; SSID может быть предположительным"
                )
            }
        }
        return mapped.distinctBy { it.bssid.lowercase() }.sortedByDescending { it.rssi }
    }

    private fun ScanResult.toWifiNetwork(connected: WifiConnectionDetails? = null): WifiNetwork? {
        val rawSsid = this.SSID?.trim() ?: ""
        val matchesCurrentConnection = connected?.isConnected == true &&
            connected.bssid.equals(this.BSSID, ignoreCase = true)
        val ssid = when {
            rawSsid.isNotEmpty() -> rawSsid
            matchesCurrentConnection && !connected?.ssid.isNullOrBlank() -> connected!!.ssid
            else -> "[Скрытая сеть]"
        }
        val bssid = this.BSSID ?: "00:00:00:00:00:00"
        val frequency = this.frequency
        val channel = frequencyToChannel(frequency)
        val band = when {
            frequency in 2400..2500 -> WifiBand.BAND_2_4_GHZ
            frequency in 4900..5900 -> WifiBand.BAND_5_GHZ
            frequency in 5925..7125 -> WifiBand.BAND_6_GHZ
            else -> WifiBand.UNKNOWN
        }

        val channelWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (this.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
                else -> 20
            }
        } else {
            20
        }

        val capabilities = this.capabilities ?: ""
        val security = parseSecurity(capabilities)

        return WifiNetwork(
            ssid = ssid,
            bssid = bssid,
            rssi = this.level,
            frequency = frequency,
            channel = channel,
            band = band,
            channelWidth = channelWidth,
            securityType = security,
            capabilities = capabilities
        )
    }

    private fun parseSecurity(capabilities: String): SecurityType {
        return when {
            capabilities.contains("WPA3") && capabilities.contains("WPA2") -> SecurityType.WPA2_WPA3_MIXED
            capabilities.contains("SAE") || capabilities.contains("WPA3") -> SecurityType.WPA3_SAE
            capabilities.contains("WPA2") && capabilities.contains("WPA") -> SecurityType.WPA_WPA2_MIXED
            capabilities.contains("WPA2") -> SecurityType.WPA2_PSK
            capabilities.contains("WPA") -> SecurityType.WPA_PSK
            capabilities.contains("WEP") -> SecurityType.WEP
            capabilities.contains("EAP") -> SecurityType.ENTERPRISE
            else -> SecurityType.OPEN
        }
    }

    companion object {
        fun frequencyToChannel(freq: Int): Int {
            return when {
                freq == 2484 -> 14
                freq in 2412..2472 -> (freq - 2412) / 5 + 1
                freq in 5170..5825 -> (freq - 5170) / 5 + 34
                freq in 5925..7125 -> (freq - 5925) / 5 + 1
                else -> 0
            }
        }

        fun channelToFrequency(channel: Int, band: WifiBand): Int {
            return when (band) {
                WifiBand.BAND_2_4_GHZ -> if (channel == 14) 2484 else 2412 + (channel - 1) * 5
                WifiBand.BAND_5_GHZ -> 5170 + (channel - 34) * 5
                WifiBand.BAND_6_GHZ -> 5925 + (channel - 1) * 5
                else -> 0
            }
        }
    }
}
