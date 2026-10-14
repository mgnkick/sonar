import { ConnectionDetails, WifiNetwork, WifiNetworkGroup, BssIdNode } from '../types/wifi';

export const INITIAL_NETWORKS: WifiNetwork[] = [
  // Keenetic Mesh Master (5 GHz)
  {
    id: 'net-1',
    ssid: 'Keenetic-Ultra-Mesh',
    bssid: '50:D4:F7:88:12:3A',
    rssi: -46,
    frequency: 5180,
    channel: 36,
    band: '5 GHz',
    channelWidth: 80,
    security: 'WPA3-SAE',
    distanceMeters: 1.8,
    capabilities: '[WPA3-SAE-CCMP][RSN-SAE+FT/SAE][RRM][WNM][ESS]',
    color: '#00E5FF',
    vendor: 'Keenetic',
    nodeRole: 'MASTER_ROUTER',
    roamingSupport: { has80211k: true, has80211r: true, has80211v: true }
  },
  // Keenetic Mesh Node 2 (2.4 GHz)
  {
    id: 'net-2',
    ssid: 'Keenetic-Ultra-Mesh',
    bssid: '50:D4:F7:88:12:3B',
    rssi: -52,
    frequency: 2412,
    channel: 1,
    band: '2.4 GHz',
    channelWidth: 20,
    security: 'WPA3-SAE',
    distanceMeters: 2.8,
    capabilities: '[WPA3-SAE-CCMP][RSN-SAE+FT/SAE][RRM][WNM][ESS]',
    color: '#00B0FF',
    vendor: 'Keenetic',
    nodeRole: 'MESH_NODE',
    roamingSupport: { has80211k: true, has80211r: true, has80211v: true }
  },
  // Keenetic Mesh Satellite (5 GHz, living room)
  {
    id: 'net-3',
    ssid: 'Keenetic-Ultra-Mesh',
    bssid: '28:28:5D:AA:44:11',
    rssi: -65,
    frequency: 5240,
    channel: 48,
    band: '5 GHz',
    channelWidth: 80,
    security: 'WPA3-SAE',
    distanceMeters: 5.6,
    capabilities: '[WPA3-SAE-CCMP][RSN-SAE+FT/SAE][RRM][WNM][ESS]',
    color: '#38BDF8',
    vendor: 'Keenetic (Buddy)',
    nodeRole: 'MESH_NODE',
    roamingSupport: { has80211k: true, has80211r: true, has80211v: true }
  },
  // Home Network - ASUS Router Master
  {
    id: 'net-4',
    ssid: 'Home_ASUS_WiFi',
    bssid: 'E4:38:83:9A:01:FE',
    rssi: -54,
    frequency: 5745,
    channel: 149,
    band: '5 GHz',
    channelWidth: 80,
    security: 'WPA2-PSK',
    distanceMeters: 3.2,
    capabilities: '[WPA2-PSK-CCMP][ESS]',
    color: '#3B82F6',
    vendor: 'ASUS',
    nodeRole: 'MASTER_ROUTER',
    roamingSupport: { has80211k: false, has80211r: false, has80211v: false }
  },
  // Home Network - Tenda Extender/Repeater on same SSID!
  {
    id: 'net-5',
    ssid: 'Home_ASUS_WiFi',
    bssid: '00:0C:43:55:77:22',
    rssi: -72,
    frequency: 2437,
    channel: 6,
    band: '2.4 GHz',
    channelWidth: 20,
    security: 'WPA2-PSK',
    distanceMeters: 8.4,
    capabilities: '[WPA2-PSK-CCMP][WPS][ESS]',
    color: '#F59E0B',
    vendor: 'Tenda (Extender)',
    nodeRole: 'REPEATER_SUSPECT',
    roamingSupport: { has80211k: false, has80211r: false, has80211v: false }
  },
  // TP-Link Deco Mesh Node 1
  {
    id: 'net-6',
    ssid: 'Deco_Office_Mesh',
    bssid: 'AC:84:C6:11:59:E0',
    rssi: -60,
    frequency: 5180,
    channel: 36,
    band: '5 GHz',
    channelWidth: 80,
    security: 'WPA2/WPA3 Mixed',
    distanceMeters: 4.5,
    capabilities: '[WPA2-PSK-CCMP][WPA3-SAE-CCMP][FT/PSK][RRM][WNM][ESS]',
    color: '#10B981',
    vendor: 'TP-Link (Deco)',
    nodeRole: 'MASTER_ROUTER',
    roamingSupport: { has80211k: true, has80211r: true, has80211v: true }
  },
  // TP-Link Deco Mesh Node 2
  {
    id: 'net-7',
    ssid: 'Deco_Office_Mesh',
    bssid: 'C0:25:E9:33:99:A1',
    rssi: -70,
    frequency: 2412,
    channel: 1,
    band: '2.4 GHz',
    channelWidth: 40,
    security: 'WPA2/WPA3 Mixed',
    distanceMeters: 8.1,
    capabilities: '[WPA2-PSK-CCMP][WPA3-SAE-CCMP][FT/PSK][RRM][WNM][ESS]',
    color: '#34D399',
    vendor: 'TP-Link (Deco)',
    nodeRole: 'MESH_NODE',
    roamingSupport: { has80211k: true, has80211r: true, has80211v: true }
  },
  // Standalone Single APs
  {
    id: 'net-8',
    ssid: 'Xiaomi_Router_AX3000',
    bssid: '28:EE:52:43:91:BC',
    rssi: -68,
    frequency: 5975,
    channel: 5,
    band: '6 GHz',
    channelWidth: 160,
    security: 'WPA3-SAE',
    distanceMeters: 6.9,
    capabilities: '[WPA3-SAE-CCMP][WIFI6E][ESS]',
    color: '#8B5CF6',
    vendor: 'Xiaomi',
    nodeRole: 'STANDALONE_AP',
    roamingSupport: { has80211k: false, has80211r: false, has80211v: false }
  },
  {
    id: 'net-9',
    ssid: 'IoT_Smart_Sensors',
    bssid: 'B8:27:EB:AA:32:C1',
    rssi: -78,
    frequency: 2462,
    channel: 11,
    band: '2.4 GHz',
    channelWidth: 20,
    security: 'WPA2-PSK',
    distanceMeters: 11.2,
    capabilities: '[WPA2-PSK-CCMP][ESS]',
    color: '#6366F1',
    vendor: 'Raspberry Pi',
    nodeRole: 'STANDALONE_AP',
    roamingSupport: { has80211k: false, has80211r: false, has80211v: false }
  },
  {
    id: 'net-10',
    ssid: '[Скрытая сеть]',
    bssid: '48:8F:5A:CC:01:42',
    rssi: -86,
    frequency: 2437,
    channel: 6,
    band: '2.4 GHz',
    channelWidth: 20,
    security: 'Enterprise',
    distanceMeters: 18.0,
    capabilities: '[WPA2-EAP-CCMP][ESS]',
    color: '#64748B',
    vendor: 'MikroTik',
    nodeRole: 'STANDALONE_AP',
    roamingSupport: { has80211k: false, has80211r: false, has80211v: false }
  }
];

export function buildNetworkGroups(networks: WifiNetwork[], expandedSsids: Set<string>): WifiNetworkGroup[] {
  const grouped = new Map<string, WifiNetwork[]>();

  networks.forEach((net) => {
    const list = grouped.get(net.ssid) || [];
    list.push(net);
    grouped.set(net.ssid, list);
  });

  const groups: WifiNetworkGroup[] = [];

  grouped.forEach((nets, ssid) => {
    const sorted = [...nets].sort((a, b) => b.rssi - a.rssi);
    const first = sorted[0];

    const vendors = sorted.map((n) => n.vendor || 'Unknown');
    const distinctVendors = Array.from(new Set(vendors));
    const hasMixedVendors = distinctVendors.length > 1;

    const hasRoaming = sorted.some(
      (n) => n.roamingSupport?.has80211k || n.roamingSupport?.has80211r || n.roamingSupport?.has80211v
    );

    let topology: WifiNetworkGroup['topologyType'] = 'STANDALONE';
    if (sorted.length > 1) {
      if (hasMixedVendors) {
        topology = 'REPEATER_DETECTED';
      } else if (first.security === 'Enterprise') {
        topology = 'ENTERPRISE_ROAMING';
      } else {
        topology = 'MESH_NETWORK';
      }
    }

    const nodes: BssIdNode[] = sorted.map((net, idx) => {
      let role = net.nodeRole || 'STANDALONE_AP';
      if (sorted.length > 1) {
        if (hasMixedVendors && net.vendor?.includes('Extender')) {
          role = 'REPEATER_SUSPECT';
        } else if (idx === 0) {
          role = 'MASTER_ROUTER';
        } else {
          role = 'MESH_NODE';
        }
      }

      return {
        bssid: net.bssid,
        rssi: net.rssi,
        frequency: net.frequency,
        channel: net.channel,
        band: net.band,
        channelWidth: net.channelWidth,
        security: net.security,
        capabilities: net.capabilities,
        vendor: net.vendor || 'Wi-Fi AP',
        nodeRole: role,
        roamingSupport: net.roamingSupport || { has80211k: false, has80211r: false, has80211v: false },
        distanceMeters: net.distanceMeters,
        color: net.color
      };
    });

    groups.push({
      ssid,
      nodes,
      topologyType: topology,
      primaryVendor: first.vendor || 'Unknown',
      hasMixedVendors,
      bestRssi: first.rssi,
      bestBand: first.band,
      bestSecurity: first.security,
      totalNodesCount: nodes.length,
      isExpanded: expandedSsids.has(ssid)
    });
  });

  return groups.sort((a, b) => b.bestRssi - a.bestRssi);
}

export const INITIAL_CONNECTION: ConnectionDetails = {
  isConnected: true,
  ssid: 'Keenetic-Ultra-Mesh',
  bssid: '50:D4:F7:88:12:3A',
  rssi: -46,
  ipAddress: '192.168.1.134',
  gateway: '192.168.1.1',
  subnetMask: '255.255.255.0',
  dns1: '1.1.1.1',
  dns2: '8.8.8.8',
  linkSpeedMbps: 866,
  txLinkSpeedMbps: 866,
  rxLinkSpeedMbps: 866,
  frequency: 5180,
  standard: 'Wi-Fi 6 (802.11ax) • Mesh 802.11k/r/v',
  macAddress: '14:AB:C5:77:99:12',
  isHidden: false
};
