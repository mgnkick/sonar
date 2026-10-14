#!/usr/bin/env python3
"""
Launcher script for WiFi Pulse & Network Suite.
Supports:
  python3 run_network_suite.py          (interactive menu)
  python3 run_network_suite.py --scan   (Wi-Fi scan & Mesh analysis)
  python3 run_network_suite.py --sample (Mesh & Repeater heuristics demo)
  python3 run_network_suite.py --ping 8.8.8.8
  python3 run_network_suite.py --trace google.com
  python3 run_network_suite.py --info   (Connection Info)
  python3 run_network_suite.py --web    (FastAPI web dashboard)
"""

from python_network_suite.cli import main

if __name__ == "__main__":
    main()
