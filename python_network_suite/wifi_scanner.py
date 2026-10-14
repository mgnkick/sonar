"""
Real cross-platform Wi-Fi scanner and Mesh/Repeater topology analyzer.
Supports Linux (nmcli / iw), Windows (netsh wlan), and macOS (airport).
Zero external dependencies required.
"""

import asyncio
import platform
import re
import shutil
import subprocess
from typing import List, Dict, Tuple, Optional

from .models import (
    BssIdNode,
    WifiNetworkGroup,
    WifiBand,
    SecurityType,
    NodeRole,
    NetworkTopologyType,
    RoamingSupport
)
from .oui_db import resolve_mac_vendor


def freq_to_channel_and_band(freq_mhz: int) -> Tuple[int, WifiBand]:
    """Convert frequency in MHz to channel number and Wi-Fi band."""
    if freq_mhz == 2484:
        return 14, WifiBand.BAND_2_4_GHZ
    elif 2412 <= freq_mhz <= 2472:
        return (freq_mhz - 2412) // 5 + 1, WifiBand.BAND_2_4_GHZ
    elif 5170 <= freq_mhz <= 5825:
        return (freq_mhz - 5170) // 5 + 34, WifiBand.BAND_5_GHZ
    elif 5925 <= freq_mhz <= 7125:
        return (freq_mhz - 5925) // 5 + 1, WifiBand.BAND_6_GHZ
    return 0, WifiBand.UNKNOWN


def channel_to_freq_and_band(channel: int) -> Tuple[int, WifiBand]:
    """Guess frequency from standard channel number."""
    if 1 <= channel <= 14:
        freq = 2484 if channel == 14 else 2412 + (channel - 1) * 5
        return freq, WifiBand.BAND_2_4_GHZ
    elif 36 <= channel <= 177:
        freq = 5170 + (channel - 34) * 5
        return freq, WifiBand.BAND_5_GHZ
    elif channel > 180:
        return 5925 + (channel - 1) * 5, WifiBand.BAND_6_GHZ
    return 0, WifiBand.UNKNOWN


def parse_security_string(sec_str: str) -> SecurityType:
    s = sec_str.upper()
    if "WPA3" in s and "WPA2" in s:
        return SecurityType.WPA2_WPA3_MIXED
    elif "WPA3" in s or "SAE" in s:
        return SecurityType.WPA3_SAE
    elif "WPA2" in s and "WPA" in s:
        return SecurityType.WPA_WPA2_MIXED
    elif "WPA2" in s:
        return SecurityType.WPA2_PSK
    elif "WPA" in s:
        return SecurityType.WPA_PSK
    elif "WEP" in s:
        return SecurityType.WEP
    elif "802.1X" in s or "EAP" in s:
        return SecurityType.ENTERPRISE
    elif "OPEN" in s or not s or s == "--":
        return SecurityType.OPEN
    return SecurityType.WPA2_PSK


def parse_roaming(capabilities: str) -> RoamingSupport:
    caps = capabilities.upper()
    has_r = "FT/" in caps or "[FT" in caps or "FAST-TRANSITION" in caps
    has_k = "RRM" in caps or "[K]" in caps or "RADIO-RESOURCE-MANAGEMENT" in caps
    has_v = "WNM" in caps or "[V]" in caps or "BSS-TRANSITION" in caps
    return RoamingSupport(has_80211k=has_k, has_80211r=has_r, has_80211v=has_v)


class WifiScanner:
    """Cross-platform Wi-Fi scanner and Mesh detector."""

    def __init__(self):
        self.os_type = platform.system()

    async def scan_raw_nodes(self) -> List[BssIdNode]:
        """Scans the physical radio spectrum and returns raw BSSID nodes."""
        if self.os_type == "Linux":
            return await self._scan_linux()
        elif self.os_type == "Windows":
            return await self._scan_windows()
        elif self.os_type == "Darwin":
            return await self._scan_macos()
        else:
            return await self._scan_linux()

    async def _scan_linux(self) -> List[BssIdNode]:
        """Linux scanner via nmcli."""
        nodes: List[BssIdNode] = []

        if shutil.which("nmcli"):
            try:
                proc = await asyncio.create_subprocess_exec(
                    "nmcli", "-t", "-f", "SSID,BSSID,SIGNAL,CHAN,FREQ,SECURITY",
                    "dev", "wifi", "list", "--rescan", "auto",
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE
                )
                stdout, _ = await proc.communicate()
                lines = stdout.decode("utf-8", errors="ignore").splitlines()

                for line in lines:
                    parts = line.split(":")
                    # nmcli colon escapes need care: SSID:BSSID(6 parts):SIGNAL:CHAN:FREQ:SECURITY
                    # Example: MyWifi:50\:D4\:F7\:88\:12\:3A:82:1:2412 MHz:WPA2
                    # We can use regex to extract BSSID
                    bssid_match = re.search(r'([0-9A-Fa-f]{2}(?:\\:[0-9A-Fa-f]{2}){5})', line)
                    if not bssid_match:
                        continue

                    raw_bssid = bssid_match.group(1).replace("\\:", ":").upper()
                    idx = line.find(bssid_match.group(1))
                    raw_ssid = line[:idx].rstrip(":")
                    tail = line[idx + len(bssid_match.group(1)):].lstrip(":")
                    tail_parts = tail.split(":")

                    ssid = raw_ssid.strip() or "[Скрытая сеть]"
                    signal_pct = int(tail_parts[0]) if len(tail_parts) > 0 and tail_parts[0].isdigit() else 50
                    channel = int(tail_parts[1]) if len(tail_parts) > 1 and tail_parts[1].isdigit() else 1

                    freq_str = tail_parts[2] if len(tail_parts) > 2 else ""
                    freq_m = re.search(r'(\d+)', freq_str)
                    freq_mhz = int(freq_m.group(1)) if freq_m else 2412

                    sec_str = tail_parts[3] if len(tail_parts) > 3 else "WPA2"
                    security = parse_security_string(sec_str)

                    rssi = int((signal_pct / 2.0) - 100)
                    _, band = freq_to_channel_and_band(freq_mhz)
                    vendor = resolve_mac_vendor(raw_bssid)
                    roaming = parse_roaming(sec_str)

                    nodes.append(BssIdNode(
                        bssid=raw_bssid,
                        ssid=ssid,
                        rssi=rssi,
                        frequency_mhz=freq_mhz,
                        channel=channel,
                        band=band,
                        channel_width_mhz=80 if band == WifiBand.BAND_5_GHZ else 20,
                        security=security,
                        capabilities=sec_str,
                        vendor=vendor,
                        roaming=roaming
                    ))

                if nodes:
                    return nodes
            except Exception:
                pass

        # Fallback to iwlist if available on Linux
        if shutil.which("iwlist"):
            try:
                proc = await asyncio.create_subprocess_exec(
                    "iwlist", "scan",
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE
                )
                stdout, _ = await proc.communicate()
                out = stdout.decode("utf-8", errors="ignore")
                curr_ssid = ""
                curr_bssid = ""
                curr_chan = 1
                curr_sig = -70
                for line in out.splitlines():
                    line = line.strip()
                    if "Cell " in line and "Address:" in line:
                        m = re.search(r'Address:\s*([0-9A-Fa-f:]{17})', line)
                        if m:
                            curr_bssid = m.group(1).upper()
                    elif "ESSID:" in line:
                        m = re.search(r'ESSID:"([^"]*)"', line)
                        if m:
                            curr_ssid = m.group(1) or "[Скрытая сеть]"
                    elif "Channel:" in line:
                        m = re.search(r'Channel:(\d+)', line)
                        if m:
                            curr_chan = int(m.group(1))
                    elif "Signal level=" in line:
                        m = re.search(r'Signal level=(-?\d+)', line)
                        if m:
                            curr_sig = int(m.group(1))
                            if curr_bssid and curr_ssid:
                                freq, band = channel_to_freq_and_band(curr_chan)
                                nodes.append(BssIdNode(
                                    bssid=curr_bssid,
                                    ssid=curr_ssid,
                                    rssi=curr_sig,
                                    frequency_mhz=freq,
                                    channel=curr_chan,
                                    band=band,
                                    channel_width_mhz=20,
                                    security=SecurityType.WPA2_PSK,
                                    capabilities="WPA2",
                                    vendor=resolve_mac_vendor(curr_bssid)
                                ))
                                curr_bssid = ""
            except Exception:
                pass

        return nodes

    async def _scan_windows(self) -> List[BssIdNode]:
        """Windows scanner using netsh wlan."""
        nodes: List[BssIdNode] = []
        try:
            proc = await asyncio.create_subprocess_exec(
                "netsh", "wlan", "show", "networks", "mode=bssid",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE
            )
            stdout, _ = await proc.communicate()
            output = stdout.decode("cp866", errors="ignore")  # Russian OEM code page or utf-8

            current_ssid = ""
            current_auth = "WPA2"

            for block in output.split("SSID "):
                if not block.strip():
                    continue

                lines = block.splitlines()
                first_line = lines[0]
                ssid_match = re.search(r':\s*(.*)', first_line)
                if ssid_match:
                    current_ssid = ssid_match.group(1).strip() or "[Скрытая сеть]"

                # Search all BSSIDs inside this SSID block
                bssid_blocks = block.split("BSSID ")
                for b_block in bssid_blocks[1:]:
                    b_lines = b_block.splitlines()
                    bssid_line = b_lines[0]
                    mac_m = re.search(r'([0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2})', bssid_line)
                    if not mac_m:
                        continue
                    bssid = mac_m.group(1).replace("-", ":").upper()

                    sig_pct = 60
                    channel = 1
                    radio_type = "802.11n"

                    for line in b_lines:
                        l = line.strip().lower()
                        if "сигнал" in l or "signal" in l:
                            pct_m = re.search(r'(\d+)%', line)
                            if pct_m:
                                sig_pct = int(pct_m.group(1))
                        elif "канал" in l or "channel" in l:
                            ch_m = re.search(r'(\d+)', line)
                            if ch_m:
                                channel = int(ch_m.group(1))
                        elif "радио" in l or "radio" in l:
                            radio_type = line.split(":")[-1].strip()
                        elif "проверка подлинности" in l or "authentication" in l:
                            current_auth = line.split(":")[-1].strip()

                    rssi = int((sig_pct / 2.0) - 100)
                    freq_mhz, band = channel_to_freq_and_band(channel)
                    vendor = resolve_mac_vendor(bssid)
                    security = parse_security_string(current_auth)

                    nodes.append(BssIdNode(
                        bssid=bssid,
                        ssid=current_ssid,
                        rssi=rssi,
                        frequency_mhz=freq_mhz,
                        channel=channel,
                        band=band,
                        channel_width_mhz=80 if band == WifiBand.BAND_5_GHZ else 20,
                        security=security,
                        capabilities=radio_type,
                        vendor=vendor,
                        roaming=parse_roaming(radio_type)
                    ))
        except Exception:
            pass

        return nodes

    async def _scan_macos(self) -> List[BssIdNode]:
        """macOS scanner using airport tool."""
        nodes: List[BssIdNode] = []
        airport_path = "/System/Library/PrivateFrameworks/Apple80211.framework/Versions/Current/Resources/airport"
        try:
            proc = await asyncio.create_subprocess_exec(
                airport_path, "-s",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE
            )
            stdout, _ = await proc.communicate()
            lines = stdout.decode("utf-8", errors="ignore").splitlines()

            for line in lines[1:]:
                parts = line.split()
                if len(parts) >= 6:
                    mac_m = re.search(r'([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})', line)
                    if mac_m:
                        bssid = mac_m.group(1).upper()
                        idx = line.find(bssid)
                        ssid = line[:idx].strip() or "[Скрытая сеть]"
                        rest = line[idx + len(bssid):].split()
                        rssi = int(rest[0]) if rest and rest[0].lstrip("-").isdigit() else -70
                        ch_str = rest[1] if len(rest) > 1 else "1"
                        channel = int(ch_str.split(",")[0]) if ch_str.split(",")[0].isdigit() else 1
                        freq_mhz, band = channel_to_freq_and_band(channel)
                        sec_str = " ".join(rest[4:]) if len(rest) > 4 else "WPA2"

                        nodes.append(BssIdNode(
                            bssid=bssid,
                            ssid=ssid,
                            rssi=rssi,
                            frequency_mhz=freq_mhz,
                            channel=channel,
                            band=band,
                            channel_width_mhz=80 if band == WifiBand.BAND_5_GHZ else 20,
                            security=parse_security_string(sec_str),
                            capabilities=sec_str,
                            vendor=resolve_mac_vendor(bssid),
                            roaming=parse_roaming(sec_str)
                        ))
        except Exception:
            pass

        return nodes

    async def scan_and_analyze(self) -> List[WifiNetworkGroup]:
        """
        Scans all nodes and aggregates them into WifiNetworkGroup
        with advanced Mesh and Repeater heuristics.
        """
        raw_nodes = await self.scan_raw_nodes()
        return self.aggregate_groups(raw_nodes)

    def aggregate_groups(self, nodes: List[BssIdNode]) -> List[WifiNetworkGroup]:
        """Groups physical BSSIDs by SSID and applies Mesh / Repeater topology heuristics."""
        grouped: Dict[str, List[BssIdNode]] = {}

        for node in nodes:
            ssid = node.ssid or "[Скрытая сеть]"
            grouped.setdefault(ssid, []).append(node)

        groups: List[WifiNetworkGroup] = []

        for ssid, group_nodes in grouped.items():
            # Sort nodes in this SSID by RSSI (strongest first)
            sorted_nodes = sorted(group_nodes, key=lambda n: n.rssi, reverse=True)
            primary_node = sorted_nodes[0]
            primary_vendor = primary_node.vendor

            vendors = [n.vendor for n in sorted_nodes]
            distinct_vendors = list(set(vendors))
            has_mixed = len(distinct_vendors) > 1

            has_roaming = any(n.roaming.is_supported for n in sorted_nodes)

            # Heuristics:
            # 1) If 1 node -> STANDALONE
            # 2) If multiple nodes on same SSID but different vendors -> REPEATER_DETECTED
            # 3) If multiple nodes with same vendor and different bands/channels or 802.11k/r/v -> MESH_NETWORK
            if len(sorted_nodes) == 1:
                topology = NetworkTopologyType.STANDALONE
            elif has_mixed:
                topology = NetworkTopologyType.REPEATER_DETECTED
            elif primary_node.security == SecurityType.ENTERPRISE:
                topology = NetworkTopologyType.ENTERPRISE_ROAMING
            else:
                topology = NetworkTopologyType.MESH_NETWORK

            # Assign roles to each node
            for idx, n in enumerate(sorted_nodes):
                if len(sorted_nodes) == 1:
                    n.node_role = NodeRole.STANDALONE_AP
                elif idx == 0 and topology != NetworkTopologyType.REPEATER_DETECTED:
                    n.node_role = NodeRole.MASTER_ROUTER
                elif has_mixed and n.vendor != primary_vendor:
                    n.node_role = NodeRole.REPEATER_SUSPECT
                elif topology == NetworkTopologyType.REPEATER_DETECTED and idx > 0:
                    n.node_role = NodeRole.REPEATER_SUSPECT
                else:
                    n.node_role = NodeRole.MESH_NODE

            groups.append(WifiNetworkGroup(
                ssid=ssid,
                nodes=sorted_nodes,
                topology_type=topology,
                primary_vendor=primary_vendor,
                has_mixed_vendors=has_mixed
            ))

        return sorted(groups, key=lambda g: g.best_rssi, reverse=True)
 
    @staticmethod
    def get_sample_mesh_capture_nodes() -> List[BssIdNode]:
        """
        Realistic captured radio packets for testing and verification of
        Mesh, Repeater and Enterprise topology heuristics when physical Wi-Fi is absent.
        """
        return [
            # 1. Keenetic Mesh (1 SSID, 3 BSSIDs, same vendor, different channels/bands)
            BssIdNode(
                ssid="Keenetic-Ultra-Mesh",
                bssid="50:D4:F7:88:12:3A",
                rssi=-42,
                frequency_mhz=5180,
                channel=36,
                band=WifiBand.BAND_5_GHZ,
                channel_width_mhz=80,
                security=SecurityType.WPA3_SAE,
                capabilities="[WPA3-SAE-CCMP][FT/SAE][RRM][BSS-TRANSITION]",
                vendor="Keenetic",
                roaming=RoamingSupport(has_80211k=True, has_80211r=True, has_80211v=True)
            ),
            BssIdNode(
                ssid="Keenetic-Ultra-Mesh",
                bssid="50:D4:F7:88:12:3B",
                rssi=-46,
                frequency_mhz=2412,
                channel=1,
                band=WifiBand.BAND_2_4_GHZ,
                channel_width_mhz=20,
                security=SecurityType.WPA3_SAE,
                capabilities="[WPA3-SAE-CCMP][FT/SAE][RRM][BSS-TRANSITION]",
                vendor="Keenetic",
                roaming=RoamingSupport(has_80211k=True, has_80211r=True, has_80211v=True)
            ),
            BssIdNode(
                ssid="Keenetic-Ultra-Mesh",
                bssid="28:28:5D:44:90:1C",
                rssi=-68,
                frequency_mhz=5220,
                channel=44,
                band=WifiBand.BAND_5_GHZ,
                channel_width_mhz=80,
                security=SecurityType.WPA3_SAE,
                capabilities="[WPA3-SAE-CCMP][FT/SAE][RRM][BSS-TRANSITION]",
                vendor="Keenetic",
                roaming=RoamingSupport(has_80211k=True, has_80211r=True, has_80211v=True)
            ),

            # 2. Repeater Suspect (Same SSID 'Home_Sweet_Home', but Master is ASUS and Satellite is Tenda Extender)
            BssIdNode(
                ssid="Home_Sweet_Home",
                bssid="E4:38:83:11:22:33",
                rssi=-50,
                frequency_mhz=2437,
                channel=6,
                band=WifiBand.BAND_2_4_GHZ,
                channel_width_mhz=20,
                security=SecurityType.WPA2_PSK,
                capabilities="[WPA2-PSK-CCMP]",
                vendor="ASUS",
                roaming=RoamingSupport()
            ),
            BssIdNode(
                ssid="Home_Sweet_Home",
                bssid="00:0C:43:AA:BB:CC",
                rssi=-76,
                frequency_mhz=2437,
                channel=6,
                band=WifiBand.BAND_2_4_GHZ,
                channel_width_mhz=20,
                security=SecurityType.WPA2_PSK,
                capabilities="[WPA2-PSK-CCMP]",
                vendor="Tenda (Extender)",
                roaming=RoamingSupport()
            ),

            # 3. TP-Link Deco Mesh
            BssIdNode(
                ssid="Deco_Living_Room",
                bssid="C0:25:E9:1A:2B:3C",
                rssi=-48,
                frequency_mhz=5240,
                channel=48,
                band=WifiBand.BAND_5_GHZ,
                channel_width_mhz=80,
                security=SecurityType.WPA2_WPA3_MIXED,
                capabilities="[WPA2-PSK+WPA3-SAE][RRM][BSS-TRANSITION]",
                vendor="TP-Link (Deco Mesh)",
                roaming=RoamingSupport(has_80211k=True, has_80211v=True)
            ),
            BssIdNode(
                ssid="Deco_Living_Room",
                bssid="C0:25:E9:9D:8E:7F",
                rssi=-62,
                frequency_mhz=5240,
                channel=48,
                band=WifiBand.BAND_5_GHZ,
                channel_width_mhz=80,
                security=SecurityType.WPA2_WPA3_MIXED,
                capabilities="[WPA2-PSK+WPA3-SAE][RRM][BSS-TRANSITION]",
                vendor="TP-Link (Deco Mesh)",
                roaming=RoamingSupport(has_80211k=True, has_80211v=True)
            ),

            # 4. Standalone Access Point (MikroTik)
            BssIdNode(
                ssid="Office_Guest",
                bssid="48:8F:5A:CC:DD:EE",
                rssi=-65,
                frequency_mhz=2462,
                channel=11,
                band=WifiBand.BAND_2_4_GHZ,
                channel_width_mhz=20,
                security=SecurityType.WPA2_PSK,
                capabilities="[WPA2-PSK-CCMP]",
                vendor="MikroTik",
                roaming=RoamingSupport()
            ),
        ]

