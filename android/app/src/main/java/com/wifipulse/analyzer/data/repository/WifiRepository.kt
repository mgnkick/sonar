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
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                try {
                    @Suppress("DEPRECATION")
                    val results = wifiManager.scanResults ?: emptyList()
                    val mappedList = results.mapNotNull { it.toWifiNetwork() }
                        .sortedByDescending { it.rssi }
                    trySend(mappedList)
                } catch (e: SecurityException) {
                    trySend(emptyList())
                }
            }
        }

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, intentFilter)

        // Emit initial scan results immediately if available
        try {
            @Suppress("DEPRECATION")
            val initialResults = wifiManager.scanResults ?: emptyList()
            trySend(initialResults.mapNotNull { it.toWifiNetwork() }.sortedByDescending { it.rssi })
        } catch (e: SecurityException) {
            trySend(emptyList())
        }

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
        val activeNetwork = connectivityManager.activeNetwork ?: return WifiConnectionDetails()
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return WifiConnectionDetails()

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return WifiConnectionDetails(isConnected = false)
        }

        @Suppress("DEPRECATION")
        val wifiInfo: WifiInfo? = wifiManager.connectionInfo
        val dhcpInfo: DhcpInfo? = wifiManager.dhcpInfo

        val rawSsid = wifiInfo?.ssid?.trim('\"') ?: ""
        val ssid = if (rawSsid == "<unknown ssid>" || rawSsid.isEmpty()) "Подключено к Wi-Fi" else rawSsid
        val bssid = wifiInfo?.bssid ?: "00:00:00:00:00:00"
        val rssi = wifiInfo?.rssi ?: 0
        val linkSpeed = wifiInfo?.linkSpeed ?: 0
        val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) wifiInfo?.frequency ?: 0 else 0

        var txSpeed = linkSpeed
        var rxSpeed = linkSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            txSpeed = wifiInfo?.txLinkSpeedMbps ?: linkSpeed
            rxSpeed = wifiInfo?.rxLinkSpeedMbps ?: linkSpeed
        }

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
            gateway = formatIpAddress(dhcpInfo?.gateway ?: 0),
            subnetMask = formatIpAddress(dhcpInfo?.netmask ?: 0),
            dns1 = formatIpAddress(dhcpInfo?.dns1 ?: 0),
            dns2 = formatIpAddress(dhcpInfo?.dns2 ?: 0),
            linkSpeedMbps = linkSpeed,
            txLinkSpeedMbps = txSpeed,
            rxLinkSpeedMbps = rxSpeed,
            frequency = frequency,
            standard = standard,
            macAddress = wifiInfo?.macAddress ?: "02:00:00:00:00:00",
            isHidden = wifiInfo?.hiddenSSID ?: false
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

    private fun ScanResult.toWifiNetwork(): WifiNetwork? {
        val rawSsid = this.SSID?.trim() ?: ""
        val ssid = if (rawSsid.isEmpty()) "[Скрытая сеть]" else rawSsid
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
