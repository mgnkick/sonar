export type WifiBand = '2.4 GHz' | '5 GHz' | '6 GHz';

export type SecurityType =
  | 'WPA3-SAE'
  | 'WPA2-PSK'
  | 'WPA/WPA2 Mixed'
  | 'WPA2/WPA3 Mixed'
  | 'WEP'
  | 'Open'
  | 'Enterprise';

export type NodeRole =
  | 'MASTER_ROUTER'
  | 'MESH_NODE'
  | 'REPEATER_SUSPECT'
  | 'STANDALONE_AP';

export type NetworkTopologyType =
  | 'STANDALONE'
  | 'MESH_NETWORK'
  | 'ENTERPRISE_ROAMING'
  | 'REPEATER_DETECTED';

export interface RoamingSupport {
  has80211k: boolean;
  has80211r: boolean;
  has80211v: boolean;
}

export interface BssIdNode {
  bssid: string;
  rssi: number;
  frequency: number;
  channel: number;
  band: WifiBand;
  channelWidth: 20 | 40 | 80 | 160;
  security: SecurityType;
  capabilities: string;
  vendor: string;
  nodeRole: NodeRole;
  roamingSupport: RoamingSupport;
  distanceMeters: number;
  color: string;
}

export interface WifiNetworkGroup {
  ssid: string;
  nodes: BssIdNode[];
  topologyType: NetworkTopologyType;
  primaryVendor: string;
  hasMixedVendors: boolean;
  bestRssi: number;
  bestBand: WifiBand;
  bestSecurity: SecurityType;
  totalNodesCount: number;
  isExpanded?: boolean;
}

export interface WifiNetwork {
  id: string;
  ssid: string;
  bssid: string;
  rssi: number; // in dBm, e.g. -54
  frequency: number; // MHz
  channel: number;
  band: WifiBand;
  channelWidth: 20 | 40 | 80 | 160; // MHz
  security: SecurityType;
  distanceMeters: number;
  capabilities: string;
  color: string;
  vendor?: string;
  nodeRole?: NodeRole;
  roamingSupport?: RoamingSupport;
}

export interface ConnectionDetails {
  isConnected: boolean;
  ssid: string;
  bssid: string;
  rssi: number;
  ipAddress: string;
  gateway: string;
  subnetMask: string;
  dns1: string;
  dns2: string;
  linkSpeedMbps: number;
  txLinkSpeedMbps: number;
  rxLinkSpeedMbps: number;
  frequency: number;
  standard: string;
  macAddress: string;
  isHidden: boolean;
}

export interface ChannelRatingInfo {
  channel: number;
  frequency: number;
  band: WifiBand;
  networkCount: number;
  score: number; // 1 to 10
  isRecommended: boolean;
  overlappingNetworks: string[];
}

export interface SignalHistorySample {
  timestamp: number;
  rssi: number;
}
