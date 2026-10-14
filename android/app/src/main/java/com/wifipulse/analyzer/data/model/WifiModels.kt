package com.wifipulse.analyzer.data.model

import kotlin.math.pow

enum class WifiBand(val displayName: String) {
    BAND_2_4_GHZ("2.4 ГГц"),
    BAND_5_GHZ("5 ГГц"),
    BAND_6_GHZ("6 ГГц"),
    UNKNOWN("Неизвестно")
}

enum class SecurityType(val displayName: String) {
    OPEN("Открытая"),
    WEP("WEP"),
    WPA_PSK("WPA-PSK"),
    WPA2_PSK("WPA2-PSK"),
    WPA3_SAE("WPA3-SAE"),
    WPA_WPA2_MIXED("WPA/WPA2"),
    WPA2_WPA3_MIXED("WPA2/WPA3"),
    ENTERPRISE("Enterprise (802.1X)")
}

data class RoamingSupport(
    val has80211k: Boolean = false, // Radio Resource Management (RRM)
    val has80211r: Boolean = false, // Fast BSS Transition (FT)
    val has80211v: Boolean = false  // Wireless Network Management (WNM)
) {
    val isSupported: Boolean get() = has80211k || has80211r || has80211v
    val summaryText: String
        get() = buildList {
            if (has80211k) add("802.11k (RRM)")
            if (has80211r) add("802.11r (FT)")
            if (has80211v) add("802.11v (WNM)")
        }.joinToString(" • ").ifEmpty { "Без бесшовного роуминга" }
}

enum class NodeRole(val displayName: String, val badgeColor: Long) {
    MASTER_ROUTER("Основной роутер / Шлюз", 0xFF00E5FF),
    MESH_NODE("Mesh-узел (Сателлит)", 0xFF10B981),
    REPEATER_SUSPECT("Потенциальный репитер / Усилитель", 0xFFF59E0B),
    STANDALONE_AP("Точка доступа (Одиночная)", 0xFF64748B)
}

enum class NetworkTopologyType(val displayName: String, val badgeColor: Long) {
    STANDALONE("Одиночная точка", 0xFF64748B),
    MESH_NETWORK("Mesh-система", 0xFF10B981),
    ENTERPRISE_ROAMING("Корпоративный роуминг", 0xFF8B5CF6),
    REPEATER_DETECTED("Обнаружен репитер", 0xFFF59E0B)
}

data class BssIdNode(
    val bssid: String,
    val rssi: Int, // in dBm
    val frequency: Int,
    val channel: Int,
    val band: WifiBand,
    val channelWidth: Int,
    val securityType: SecurityType,
    val capabilities: String,
    val vendor: String,
    val nodeRole: NodeRole,
    val roamingSupport: RoamingSupport,
    val timestamp: Long = System.currentTimeMillis()
) {
    val signalPercent: Int
        get() = ((rssi + 100).coerceIn(0, 50) * 2).coerceIn(0, 100)

    val signalQuality: SignalQuality
        get() = when {
            rssi >= -50 -> SignalQuality.EXCELLENT
            rssi >= -65 -> SignalQuality.GOOD
            rssi >= -75 -> SignalQuality.FAIR
            rssi >= -85 -> SignalQuality.POOR
            else -> SignalQuality.VERY_POOR
        }

    val estimatedDistanceMeters: Double
        get() {
            val exp = (27.55 - (20 * kotlin.math.log10(frequency.toDouble())) + kotlin.math.abs(rssi)) / 20.0
            return (10.0.pow(exp) * 10).toInt() / 10.0
        }
}

data class WifiNetworkGroup(
    val ssid: String,
    val nodes: List<BssIdNode>,
    val topologyType: NetworkTopologyType,
    val primaryVendor: String,
    val hasMixedVendors: Boolean,
    val isExpanded: Boolean = false
) {
    val bestNode: BssIdNode?
        get() = nodes.maxByOrNull { it.rssi }

    val bestRssi: Int
        get() = bestNode?.rssi ?: -100

    val bestBand: WifiBand
        get() = bestNode?.band ?: WifiBand.UNKNOWN

    val bestSecurity: SecurityType
        get() = bestNode?.securityType ?: SecurityType.OPEN

    val availableBands: Set<WifiBand>
        get() = nodes.map { it.band }.toSet()

    val totalNodesCount: Int
        get() = nodes.size

    val isMeshOrRoaming: Boolean
        get() = topologyType == NetworkTopologyType.MESH_NETWORK ||
                topologyType == NetworkTopologyType.ENTERPRISE_ROAMING ||
                topologyType == NetworkTopologyType.REPEATER_DETECTED

    val roamingSummary: RoamingSupport
        get() = RoamingSupport(
            has80211k = nodes.any { it.roamingSupport.has80211k },
            has80211r = nodes.any { it.roamingSupport.has80211r },
            has80211v = nodes.any { it.roamingSupport.has80211v }
        )
}

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: WifiBand,
    val channelWidth: Int,
    val securityType: SecurityType,
    val capabilities: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val signalPercent: Int
        get() = ((rssi + 100).coerceIn(0, 50) * 2).coerceIn(0, 100)

    val signalQuality: SignalQuality
        get() = when {
            rssi >= -50 -> SignalQuality.EXCELLENT
            rssi >= -65 -> SignalQuality.GOOD
            rssi >= -75 -> SignalQuality.FAIR
            rssi >= -85 -> SignalQuality.POOR
            else -> SignalQuality.VERY_POOR
        }

    val estimatedDistanceMeters: Double
        get() {
            val exp = (27.55 - (20 * kotlin.math.log10(frequency.toDouble())) + kotlin.math.abs(rssi)) / 20.0
            return (10.0.pow(exp) * 10).toInt() / 10.0
        }
}

enum class SignalQuality(val label: String, val hexColor: Long) {
    EXCELLENT("Отличное", 0xFF22C55E),
    GOOD("Хорошее", 0xFF10B981),
    FAIR("Среднее", 0xFFF59E0B),
    POOR("Слабое", 0xFFF97316),
    VERY_POOR("Критическое", 0xFFEF4444)
}

data class WifiConnectionDetails(
    val isConnected: Boolean = false,
    val ssid: String = "",
    val bssid: String = "",
    val rssi: Int = 0,
    val ipAddress: String = "0.0.0.0",
    val gateway: String = "0.0.0.0",
    val subnetMask: String = "255.255.255.0",
    val dns1: String = "8.8.8.8",
    val dns2: String = "8.8.4.4",
    val linkSpeedMbps: Int = 0,
    val txLinkSpeedMbps: Int = 0,
    val rxLinkSpeedMbps: Int = 0,
    val frequency: Int = 0,
    val standard: String = "Wi-Fi 5 (802.11ac)",
    val macAddress: String = "02:00:00:00:00:00",
    val isHidden: Boolean = false
)

data class ChannelRating(
    val channel: Int,
    val frequency: Int,
    val band: WifiBand,
    val networkCount: Int,
    val score: Int,
    val isRecommended: Boolean,
    val overlappingNetworks: List<String> = emptyList()
)

data class SignalHistoryPoint(
    val timestamp: Long,
    val rssi: Int
)
