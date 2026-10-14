package com.wifipulse.analyzer.data.repository

import com.wifipulse.analyzer.data.model.*

object WifiTopologyAnalyzer {

    // Common OUI (Organizationally Unique Identifier) prefixes for Wi-Fi vendors
    private val OUI_DATABASE: Map<String, String> = mapOf(
        "50:D4:F7" to "Keenetic",
        "28:28:5D" to "Keenetic",
        "E4:18:6B" to "Keenetic",
        "EC:43:F6" to "Keenetic",
        "18:E8:29" to "Keenetic",
        "AC:84:C6" to "TP-Link",
        "50:C7:BF" to "TP-Link",
        "74:DA:88" to "TP-Link",
        "B0:95:75" to "TP-Link",
        "D8:47:32" to "TP-Link",
        "E8:48:B8" to "TP-Link",
        "C0:25:E9" to "TP-Link (Deco Mesh)",
        "28:EE:52" to "Xiaomi",
        "64:90:C1" to "Xiaomi",
        "7C:49:EB" to "Xiaomi",
        "50:64:2B" to "Xiaomi",
        "D4:61:9D" to "Xiaomi Mesh",
        "E4:38:83" to "ASUS",
        "04:D9:F5" to "ASUS",
        "2C:FD:A1" to "ASUS (AiMesh)",
        "38:2C:4A" to "ASUS",
        "AC:9E:17" to "ASUS",
        "CC:2D:21" to "Tenda",
        "50:2B:73" to "Tenda (Nova Mesh)",
        "00:0C:43" to "Tenda (Extender)",
        "C8:3A:35" to "Tenda",
        "D4:6E:0E" to "Tenda",
        "48:8F:5A" to "MikroTik",
        "B8:69:F4" to "MikroTik",
        "64:D1:54" to "MikroTik (CAPsMAN)",
        "CC:2D:E0" to "MikroTik",
        "74:83:C2" to "Ubiquiti UniFi",
        "B4:FB:E4" to "Ubiquiti UniFi",
        "F0:9F:C2" to "Ubiquiti UniFi",
        "68:D7:9A" to "Ubiquiti UniFi",
        "C4:AD:34" to "Huawei",
        "00:E0:FC" to "Huawei",
        "48:46:FB" to "Huawei Mesh",
        "00:1E:58" to "D-Link",
        "14:D6:4D" to "D-Link",
        "B8:27:EB" to "Raspberry Pi / IoT",
        "DC:A6:32" to "Raspberry Pi Foundation",
        "00:1A:2B" to "Cisco / Meraki",
        "F8:B7:E2" to "Netgear (Orbi Mesh)",
        "20:4E:7F" to "Netgear",
        "00:09:5B" to "Netgear Extender",
        "F4:F5:E8" to "Google Nest Wifi",
        "D8:6C:63" to "Google Wifi",
        "BC:83:85" to "Apple AirPort",
        "00:17:F2" to "Apple",
        "3C:22:FB" to "Apple",
        "F0:18:98" to "Apple",
        "A4:5E:60" to "Apple",
        "00:1A:11" to "Google",
        "F4:F5:D8" to "Google Nest",
        "44:07:0B" to "Google Nest",
        "18:FE:34" to "Espressif / IoT",
        "24:0A:C4" to "Espressif / IoT",
        "30:AE:A4" to "Espressif / IoT",
        "AC:15:A2" to "Espressif / IoT",
        "B4:E6:2D" to "Espressif / IoT",
        "00:23:CD" to "Samsung",
        "8C:77:12" to "Samsung",
        "E8:50:8B" to "Samsung",
        "00:26:5A" to "Huawei",
        "70:4D:7B" to "Huawei",
        "CC:96:A0" to "Huawei",
        "00:1D:0F" to "ASUS",
        "2C:56:DC" to "ASUS",
        "A8:5E:45" to "TP-Link",
        "14:EB:B6" to "TP-Link",
        "EC:08:6B" to "TP-Link",
        "F4:EC:38" to "TP-Link",
        "60:32:B1" to "Xiaomi",
        "64:CC:2E" to "Xiaomi",
        "8C:BE:BE" to "Xiaomi",
        "00:18:E7" to "Tenda",
        "C0:4A:00" to "Netgear",
        "2C:30:33" to "Netgear",
        "00:24:01" to "D-Link",
        "1C:7E:E5" to "D-Link",
        "00:15:6D" to "Ubiquiti",
        "24:A4:3C" to "Ubiquiti",
        "00:18:0A" to "Cisco",
        "00:40:96" to "Cisco",
        "00:1B:2F" to "Cisco",
        "00:14:BF" to "Linksys",
        "20:AA:4B" to "Linksys",
        "C8:D7:19" to "Linksys",
        "00:0F:66" to "Belkin",
        "94:10:3E" to "Belkin",
        "00:1F:33" to "Belkin",
        "00:1C:DF" to "ZTE",
        "34:4B:50" to "ZTE",
        "D8:32:14" to "ZTE",
        "00:25:9C" to "Huawei / Honor",
        "F8:3D:FF" to "Honor / Huawei",
        "B0:BE:76" to "TP-Link",
        "F4:6D:04" to "TP-Link",
        "5C:A6:E6" to "TP-Link",
        "EC:08:6B" to "TP-Link",
        "A0:F3:C1" to "TP-Link",
        "98:DA:C4" to "TP-Link",
        "C0:06:C3" to "TP-Link",
        "60:A4:B7" to "TP-Link",
        "14:CC:20" to "TP-Link",
        "E8:DE:27" to "TP-Link",
        "E0:28:6D" to "TP-Link",
        "84:D8:1B" to "TP-Link",
        "C4:E9:84" to "TP-Link",
        "F8:1A:67" to "TP-Link",
        "74:FE:CE" to "TP-Link",
        "B0:4E:26" to "TP-Link",
        "D8:0D:17" to "TP-Link",
        "EC:26:CA" to "TP-Link",
        "F0:A7:31" to "TP-Link",
        "50:FF:20" to "TP-Link",
        "18:D6:C7" to "TP-Link",
        "A4:2B:B0" to "Xiaomi",
        "78:11:DC" to "Xiaomi",
        "64:B4:73" to "Xiaomi",
        "F8:A4:5F" to "Xiaomi",
        "8C:DE:F9" to "Xiaomi",
        "34:80:B3" to "Xiaomi",
        "E8:6F:38" to "Xiaomi",
        "A4:83:E7" to "Apple",
        "00:1B:63" to "Apple",
        "A8:96:8A" to "Apple",
        "F0:99:BF" to "Apple",
        "D0:03:4B" to "Apple",
        "C8:69:CD" to "Apple",
        "A0:99:9B" to "Apple",
        "FC:FB:FB" to "Cisco",
        "00:00:0C" to "Cisco",
        "00:26:CB" to "Cisco",
        "00:1E:49" to "Cisco",
        "00:25:84" to "Cisco",
        "00:50:56" to "VMware",
        "00:0C:29" to "VMware",
        "08:00:27" to "Oracle VirtualBox",
        "52:54:00" to "QEMU / KVM",
        "00:15:5D" to "Microsoft Hyper-V",
        "8C:85:90" to "Intel",
        "3C:58:C2" to "Intel",
        "A4:4C:C8" to "Intel",
        "00:1F:3B" to "Intel",
        "00:13:02" to "Intel",
        "00:1B:21" to "Intel",
        "00:90:F5" to "Samsung",
        "8C:79:F5" to "Samsung",
        "00:21:19" to "Samsung",
        "40:4E:36" to "Huawei",
        "40:4D:8E" to "Huawei",
        "A8:CA:7B" to "Huawei",
        "24:6F:28" to "Espressif / ESP32",
        "AC:67:B2" to "Espressif / IoT",
        "7C:9E:BD" to "Espressif / IoT",
        "FC:65:DE" to "Google Nest",
        "50:02:91" to "Amazon Devices",
        "6C:21:A2" to "OnePlus",
        "A8:9C:ED" to "OnePlus",
        "00:1D:0F" to "ASUS",
        "2C:56:DC" to "ASUS",
        "D8:50:E6" to "ASUS",
        "AC:22:0B" to "ASUS",
        "00:0F:66" to "Belkin",
        "94:10:3E" to "Belkin",
        "00:1C:DF" to "ZTE",
        "34:4B:50" to "ZTE",
        "D8:32:14" to "ZTE",
        "00:22:6B" to "Sagemcom",
        "C0:3F:0E" to "Sagemcom",
        "00:1E:42" to "FiberHome",
        "A8:57:4E" to "FiberHome",
        "00:1B:11" to "D-Link",
        "1C:7E:E5" to "D-Link",
        "00:24:01" to "D-Link",
        "00:18:0A" to "Cisco",
        "00:40:96" to "Cisco",
        "00:1B:2F" to "Cisco",
        "00:14:BF" to "Linksys",
        "20:AA:4B" to "Linksys",
        "C8:D7:19" to "Linksys",
        "00:15:6D" to "Ubiquiti",
        "24:A4:3C" to "Ubiquiti",
        "B4:FB:E4" to "Ubiquiti UniFi",
        "74:83:C2" to "Ubiquiti UniFi"
    )

    fun resolveVendor(bssid: String): String {
        val clean = bssid.uppercase().replace("-", ":")
        if (clean.length >= 8) {
            val prefix = clean.substring(0, 8)
            OUI_DATABASE[prefix]?.let { return it }
            val firstOctet = clean.take(2).toIntOrNull(16)
            if (firstOctet != null && (firstOctet and 0x02) != 0) {
                return "Локальный / рандомизированный MAC"
            }
        }
        return "Неизвестный вендор (${clean.take(8)})"
    }

    /**
     * Parse 802.11k, 802.11r, 802.11v roaming capabilities
     */
    fun parseRoamingSupport(capabilities: String): RoamingSupport {
        val upper = capabilities.uppercase()
        val hasR = upper.contains("FT/") || upper.contains("[FT") || upper.contains("FAST-TRANSITION")
        val hasK = upper.contains("RRM") || upper.contains("RADIO-RESOURCE-MANAGEMENT") || upper.contains("[K]")
        val hasV = upper.contains("WNM") || upper.contains("BSS-TRANSITION") || upper.contains("[V]")
        return RoamingSupport(has80211k = hasK, has80211r = hasR, has80211v = hasV)
    }

    /**
     * Group a flat list of ScanResult-derived WifiNetworks into structured WifiNetworkGroups
     * and apply Mesh / Repeater heuristics.
     */
    fun aggregateIntoGroups(
        networks: List<WifiNetwork>,
        previousExpandedSsids: Set<String> = emptySet()
    ): List<WifiNetworkGroup> {
        val groupedBySsid = networks.groupBy { it.ssid }

        return groupedBySsid.map { (ssid, nets) ->
            val sortedNodes = nets.sortedByDescending { it.rssi }
            val first = sortedNodes.first()
            val primaryVendor = resolveVendor(first.bssid)

            val vendors = sortedNodes.map { resolveVendor(it.bssid) }
            val distinctVendors = vendors.distinct()
            val hasMixedVendors = distinctVendors.size > 1

            val roamingPresent = sortedNodes.any {
                val r = parseRoamingSupport(it.capabilities)
                r.isSupported
            }

            // Determine network topology
            val topology = when {
                sortedNodes.size == 1 -> NetworkTopologyType.STANDALONE
                first.securityType == SecurityType.ENTERPRISE -> NetworkTopologyType.ENTERPRISE_ROAMING
                hasMixedVendors -> NetworkTopologyType.REPEATER_DETECTED
                roamingPresent || sortedNodes.size >= 2 -> NetworkTopologyType.MESH_NETWORK
                else -> NetworkTopologyType.MESH_NETWORK
            }

            // Assign individual roles to each BSSID node
            val nodes = sortedNodes.mapIndexed { index, net ->
                val nodeVendor = resolveVendor(net.bssid)
                val roaming = parseRoamingSupport(net.capabilities)

                val role = when {
                    sortedNodes.size == 1 -> NodeRole.STANDALONE_AP
                    index == 0 && topology != NetworkTopologyType.REPEATER_DETECTED -> NodeRole.MASTER_ROUTER
                    hasMixedVendors && nodeVendor != primaryVendor -> NodeRole.REPEATER_SUSPECT
                    topology == NetworkTopologyType.REPEATER_DETECTED && index > 0 -> NodeRole.REPEATER_SUSPECT
                    else -> NodeRole.MESH_NODE
                }

                BssIdNode(
                    bssid = net.bssid,
                    rssi = net.rssi,
                    frequency = net.frequency,
                    channel = net.channel,
                    band = net.band,
                    channelWidth = net.channelWidth,
                    securityType = net.securityType,
                    capabilities = net.capabilities,
                    vendor = nodeVendor,
                    nodeRole = role,
                    roamingSupport = roaming,
                    timestamp = net.timestamp
                )
            }

            WifiNetworkGroup(
                ssid = ssid,
                nodes = nodes,
                topologyType = topology,
                primaryVendor = primaryVendor,
                hasMixedVendors = hasMixedVendors,
                isExpanded = previousExpandedSsids.contains(ssid)
            )
        }.sortedByDescending { it.bestRssi }
    }
}
