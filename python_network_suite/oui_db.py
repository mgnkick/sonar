"""
OUI (Organizationally Unique Identifier) MAC address lookup database.
Maps 24-bit MAC prefixes (e.g. 50:D4:F7) to hardware manufacturers.
"""

from typing import Dict

OUI_DATABASE: Dict[str, str] = {
    # Keenetic
    "50:D4:F7": "Keenetic",
    "28:28:5D": "Keenetic",
    "E4:18:6B": "Keenetic",
    "EC:43:F6": "Keenetic",
    "18:E8:29": "Keenetic",
    "04:BF:6D": "Keenetic",

    # TP-Link & Deco Mesh
    "AC:84:C6": "TP-Link",
    "50:C7:BF": "TP-Link",
    "74:DA:88": "TP-Link",
    "B0:95:75": "TP-Link",
    "D8:47:32": "TP-Link",
    "E8:48:B8": "TP-Link",
    "C0:25:E9": "TP-Link (Deco Mesh)",
    "60:32:B1": "TP-Link",
    "98:48:27": "TP-Link",

    # Xiaomi / Redmi
    "28:EE:52": "Xiaomi",
    "64:90:C1": "Xiaomi",
    "7C:49:EB": "Xiaomi",
    "50:64:2B": "Xiaomi",
    "D4:61:9D": "Xiaomi (Mesh)",
    "8C:DE:F9": "Xiaomi",
    "78:11:DC": "Xiaomi",

    # ASUS & AiMesh
    "E4:38:83": "ASUS",
    "04:D9:F5": "ASUS",
    "2C:FD:A1": "ASUS (AiMesh)",
    "38:2C:4A": "ASUS",
    "AC:9E:17": "ASUS",
    "10:7B:44": "ASUS",

    # Tenda & Nova Mesh & Extenders
    "CC:2D:21": "Tenda",
    "50:2B:73": "Tenda (Nova Mesh)",
    "00:0C:43": "Tenda (Extender)",
    "C8:3A:35": "Tenda",
    "D4:6E:0E": "Tenda",
    "04:95:E6": "Tenda",

    # MikroTik
    "48:8F:5A": "MikroTik",
    "B8:69:F4": "MikroTik",
    "64:D1:54": "MikroTik (CAPsMAN)",
    "CC:2D:E0": "MikroTik",
    "2C:C8:1B": "MikroTik",
    "DC:2C:6E": "MikroTik",

    # Ubiquiti UniFi
    "74:83:C2": "Ubiquiti UniFi",
    "B4:FB:E4": "Ubiquiti UniFi",
    "F0:9F:C2": "Ubiquiti UniFi",
    "68:D7:9A": "Ubiquiti UniFi",
    "78:45:58": "Ubiquiti UniFi",
    "00:27:22": "Ubiquiti",

    # Huawei & Honor
    "C4:AD:34": "Huawei",
    "00:E0:FC": "Huawei",
    "48:46:FB": "Huawei (Mesh)",
    "28:6E:D4": "Huawei",
    "70:72:0C": "Huawei",

    # D-Link
    "00:1E:58": "D-Link",
    "14:D6:4D": "D-Link",
    "28:10:7B": "D-Link",
    "B0:C5:54": "D-Link",

    # Netgear
    "F8:B7:E2": "Netgear (Orbi Mesh)",
    "20:4E:7F": "Netgear",
    "00:09:5B": "Netgear (Extender)",
    "A0:04:60": "Netgear",

    # Google / Nest
    "F4:F5:E8": "Google Nest Wifi",
    "D8:6C:63": "Google Wifi",

    # Apple
    "BC:83:85": "Apple AirPort",
    "A4:5E:60": "Apple",

    # Cisco / Meraki
    "00:1A:2B": "Cisco",
    "00:18:0A": "Cisco Meraki",

    # AVM Fritz!Box
    "34:31:C4": "AVM Fritz!Box",
    "DC:39:6F": "AVM Fritz!Box",

    # Raspberry Pi / IoT
    "B8:27:EB": "Raspberry Pi",
    "DC:A6:32": "Raspberry Pi Foundation",
    "E4:5F:01": "Raspberry Pi"
}


def resolve_mac_vendor(bssid: str) -> str:
    """
    Resolves MAC address prefix to vendor name.
    """
    clean = bssid.upper().replace("-", ":").strip()
    if len(clean) >= 8:
        prefix = clean[:8]
        if prefix in OUI_DATABASE:
            return OUI_DATABASE[prefix]
    return f"Вендор ({clean[:8]})"
