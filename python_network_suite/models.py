"""
Data models for Wi-Fi scanning, topology analysis, Ping and Traceroute.
Uses standard library dataclasses for zero-dependency execution on Python 3.10+.
"""

from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import List, Optional, Set, Dict, Any


class WifiBand(str, Enum):
    BAND_2_4_GHZ = "2.4 GHz"
    BAND_5_GHZ = "5 GHz"
    BAND_6_GHZ = "6 GHz"
    UNKNOWN = "Unknown"


class SecurityType(str, Enum):
    OPEN = "Open"
    WEP = "WEP"
    WPA_PSK = "WPA-PSK"
    WPA2_PSK = "WPA2-PSK"
    WPA3_SAE = "WPA3-SAE"
    WPA_WPA2_MIXED = "WPA/WPA2"
    WPA2_WPA3_MIXED = "WPA2/WPA3"
    ENTERPRISE = "Enterprise (802.1X)"
    UNKNOWN = "Unknown"


class NodeRole(str, Enum):
    MASTER_ROUTER = "MASTER_ROUTER"          # Primary gateway router
    MESH_NODE = "MESH_NODE"                  # Mesh satellite / repeater AP
    REPEATER_SUSPECT = "REPEATER_SUSPECT"    # Suspected 3rd-party Wi-Fi extender
    STANDALONE_AP = "STANDALONE_AP"          # Single access point


class NetworkTopologyType(str, Enum):
    STANDALONE = "STANDALONE"                # 1 BSSID
    MESH_NETWORK = "MESH_NETWORK"            # Multiple BSSIDs, matching vendor or 802.11k/r/v
    ENTERPRISE_ROAMING = "ENTERPRISE_ROAMING"# Corporate 802.1X multi-AP
    REPEATER_DETECTED = "REPEATER_DETECTED"  # Different vendors on the same SSID


@dataclass
class RoamingSupport:
    has_80211k: bool = False  # Radio Resource Management
    has_80211r: bool = False  # Fast BSS Transition
    has_80211v: bool = False  # Wireless Network Management

    @property
    def is_supported(self) -> bool:
        return self.has_80211k or self.has_80211r or self.has_80211v

    @property
    def summary_text(self) -> str:
        standards = []
        if self.has_80211k:
            standards.append("802.11k")
        if self.has_80211r:
            standards.append("802.11r")
        if self.has_80211v:
            standards.append("802.11v")
        return "/".join(standards) if standards else "Нет роуминга"


@dataclass
class BssIdNode:
    bssid: str
    rssi: int                      # dBm (-30 to -95)
    frequency_mhz: int             # e.g. 2412, 5180, 5975
    channel: int                   # e.g. 1, 6, 11, 36, 149
    band: WifiBand
    channel_width_mhz: int         # 20, 40, 80, 160
    security: SecurityType
    capabilities: str
    vendor: str                    # OUI resolved vendor name
    ssid: str = ""                 # Network name / SSID
    node_role: NodeRole = NodeRole.STANDALONE_AP
    roaming: RoamingSupport = field(default_factory=RoamingSupport)
    signal_percent: int = 0
    distance_meters: float = 0.0

    def __post_init__(self):
        # Calculate percentage (0 - 100%)
        self.signal_percent = max(0, min(100, (self.rssi + 100) * 2))
        # Free-Space Path Loss (FSPL) approximation for distance
        if self.frequency_mhz > 0:
            exp = (27.55 - (20 * 3.14159 * 0.318309886 * (self.frequency_mhz / 1000.0)) + abs(self.rssi)) / 20.0
            self.distance_meters = round(max(0.5, 10 ** (exp - 1.2)), 1)
        else:
            self.distance_meters = 0.0


@dataclass
class WifiNetworkGroup:
    ssid: str
    nodes: List[BssIdNode]
    topology_type: NetworkTopologyType
    primary_vendor: str
    has_mixed_vendors: bool = False
    best_rssi: int = -100
    best_band: WifiBand = WifiBand.UNKNOWN
    best_security: SecurityType = SecurityType.OPEN
    total_nodes_count: int = 0

    def __post_init__(self):
        if self.nodes:
            best_node = max(self.nodes, key=lambda n: n.rssi)
            self.best_rssi = best_node.rssi
            self.best_band = best_node.band
            self.best_security = best_node.security
            self.total_nodes_count = len(self.nodes)


@dataclass
class PingPacket:
    sequence: int
    bytes_count: int
    ip: str
    ttl: int
    rtt_ms: float
    is_timeout: bool = False
    raw_line: str = ""


@dataclass
class PingSummary:
    target: str
    packets_transmitted: int = 0
    packets_received: int = 0
    packet_loss_percent: float = 0.0
    min_rtt_ms: float = 0.0
    avg_rtt_ms: float = 0.0
    max_rtt_ms: float = 0.0
    mdev_rtt_ms: float = 0.0


@dataclass
class TracerouteHop:
    hop_number: int
    ip: Optional[str] = None
    host_name: Optional[str] = None
    rtt_ms: Optional[float] = None
    is_timeout: bool = False
    is_target_reached: bool = False
    raw_output: str = ""


@dataclass
class ConnectionInfo:
    local_ip: str
    gateway_ip: str
    active_interface: str
    dns_servers: List[str] = field(default_factory=list)
    mac_address: str = ""
    public_ip: Optional[str] = None
    is_connected: bool = True

