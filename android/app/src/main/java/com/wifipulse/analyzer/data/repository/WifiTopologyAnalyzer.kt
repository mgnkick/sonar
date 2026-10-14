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
        "BC:83:85" to "Apple AirPort"
    )

    fun resolveVendor(bssid: String): String {
        val clean = bssid.uppercase().replace("-", ":")
        if (clean.length >= 8) {
            val prefix = clean.substring(0, 8)
            OUI_DATABASE[prefix]?.let { return it }
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
