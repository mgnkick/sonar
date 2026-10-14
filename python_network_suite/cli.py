"""
Command-Line Interface with rich terminal formatting and interactive mode.
Works with standard ANSI colors out-of-the-box, with enhanced Rich support if installed.
Supports both interactive navigation and direct CLI flags (--scan, --ping, --trace, --info).
"""

import argparse
import asyncio
import os
import sys
from typing import List

from .models import (
    WifiNetworkGroup,
    NetworkTopologyType,
    NodeRole,
    PingPacket,
    PingSummary,
    TracerouteHop,
    ConnectionInfo
)
from .wifi_scanner import WifiScanner
from .ping_service import AsyncPingService
from .traceroute_service import AsyncTracerouteService
from .network_info import NetworkInfoService

# Check for Rich library
try:
    from rich.console import Console
    from rich.table import Table
    from rich.panel import Panel
    from rich import print as rprint
    HAVE_RICH = True
    console = Console()
except ImportError:
    HAVE_RICH = False
    console = None

# ANSI colors for zero-dependency fallback
C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_CYAN = "\033[36m"
C_GREEN = "\033[32m"
C_YELLOW = "\033[33m"
C_RED = "\033[31m"
C_BLUE = "\033[34m"
C_MAGENTA = "\033[35m"
C_GRAY = "\033[90m"


def print_banner():
    banner = f"""
{C_CYAN}{C_BOLD}======================================================================
  WiFi Pulse & Network Suite (Python 3.10+)
  Анализатор Wi-Fi, Mesh/Репитеров, Реальный Пинг и Трассировка
======================================================================{C_RESET}
"""
    print(banner)


def display_wifi_groups(groups: List[WifiNetworkGroup]):
    """Displays aggregated Wi-Fi networks with Mesh and Repeater highlights."""
    if not groups:
        print(f"{C_YELLOW}Точки доступа не обнаружены или в системе нет активного Wi-Fi адаптера.{C_RESET}")
        return

    if HAVE_RICH and console:
        table = Table(title="📡 Обнаруженные Wi-Fi сети и топология Mesh/Репитеров", show_header=True, header_style="bold cyan")
        table.add_column("SSID / Имя сети", style="bold white", width=24)
        table.add_column("Топология", style="bold", width=22)
        table.add_column("Главный вендор", style="cyan", width=18)
        table.add_column("Узлы (BSSID)", justify="center", width=12)
        table.add_column("Макс. сигнал", justify="right", width=14)
        table.add_column("Диапазоны", width=16)

        for g in groups:
            topo_style = "green" if g.topology_type == NetworkTopologyType.MESH_NETWORK else (
                "yellow" if g.topology_type == NetworkTopologyType.REPEATER_DETECTED else "white"
            )
            topo_label = {
                NetworkTopologyType.MESH_NETWORK: "Mesh-система",
                NetworkTopologyType.REPEATER_DETECTED: "Обнаружен репитер",
                NetworkTopologyType.ENTERPRISE_ROAMING: "Enterprise 802.1X",
                NetworkTopologyType.STANDALONE: "Одиночная AP"
            }.get(g.topology_type, g.topology_type.value)

            bands_str = ", ".join(sorted(list(set(n.band.value for n in g.nodes))))

            table.add_row(
                g.ssid,
                f"[{topo_style}]{topo_label}[/{topo_style}]",
                g.primary_vendor,
                str(g.total_nodes_count),
                f"{g.best_rssi} dBm",
                bands_str
            )

        console.print(table)

        # Print detailed BSSID nodes for Mesh or Multi-AP networks
        console.print("\n[bold cyan]🔍 Детализация физических узлов (BSSID):[/bold cyan]")
        for g in groups:
            if g.total_nodes_count > 1 or g.topology_type != NetworkTopologyType.STANDALONE:
                sub_table = Table(title=f"Узлы сети: {g.ssid} ({g.topology_type.value})", show_header=True, header_style="bold magenta")
                sub_table.add_column("Роль узла", width=24)
                sub_table.add_column("MAC (BSSID)", style="cyan", width=20)
                sub_table.add_column("Вендор", style="cyan", width=18)
                sub_table.add_column("Канал / Частота", width=20)
                sub_table.add_column("RSSI (dBm)", justify="right", width=14)
                sub_table.add_column("Роуминг", width=16)

                for n in g.nodes:
                    role_str = {
                        NodeRole.MASTER_ROUTER: "[bold cyan]Основной роутер[/bold cyan]",
                        NodeRole.MESH_NODE: "[bold green]Mesh-узел[/bold green]",
                        NodeRole.REPEATER_SUSPECT: "[bold yellow]Репитер (усилитель)[/bold yellow]",
                        NodeRole.STANDALONE_AP: "Точка доступа"
                    }.get(n.node_role, n.node_role.value)

                    sig_color = "green" if n.rssi >= -60 else ("yellow" if n.rssi >= -75 else "red")

                    sub_table.add_row(
                        role_str,
                        n.bssid,
                        n.vendor,
                        f"CH {n.channel} ({n.frequency_mhz} MHz)",
                        f"[{sig_color}]{n.rssi} dBm ({n.signal_percent}%)[/{sig_color}]",
                        n.roaming.summary_text
                    )
                console.print(sub_table)
    else:
        # Standard ANSI terminal output
        print(f"\n{C_BOLD}{'SSID':<24} {'ТОПОЛОГИЯ':<22} {'ВЕНДОР':<18} {'BSSID':<6} {'СИГНАЛ':<12} {'ДИАПАЗОНЫ'}{C_RESET}")
        print("-" * 95)

        for g in groups:
            color = C_GREEN if g.topology_type == NetworkTopologyType.MESH_NETWORK else (
                C_YELLOW if g.topology_type == NetworkTopologyType.REPEATER_DETECTED else C_RESET
            )
            bands_str = ", ".join(sorted(list(set(n.band.value for n in g.nodes))))
            print(f"{C_BOLD}{g.ssid[:23]:<24}{C_RESET} {color}{g.topology_type.value:<22}{C_RESET} {g.primary_vendor[:17]:<18} {g.total_nodes_count:<6} {g.best_rssi} dBm     {bands_str}")

            if g.total_nodes_count > 1:
                for n in g.nodes:
                    role_prefix = "  ├─" if n != g.nodes[-1] else "  └─"
                    role_name = "Шлюз/Master" if n.node_role == NodeRole.MASTER_ROUTER else (
                        "Репитер" if n.node_role == NodeRole.REPEATER_SUSPECT else "Mesh-узел"
                    )
                    print(f"{C_GRAY}{role_prefix} [{role_name}] {n.bssid} | {n.vendor} | CH {n.channel} ({n.frequency_mhz} MHz) | {n.rssi} dBm | {n.roaming.summary_text}{C_RESET}")


def display_connection_info(info: ConnectionInfo):
    """Displays active host network adapter and routing configuration."""
    if HAVE_RICH and console:
        panel_content = f"""[bold white]Локальный IP:[/bold white]       [cyan]{info.local_ip}[/cyan]
[bold white]Основной шлюз:[/bold white]      [cyan]{info.gateway_ip}[/cyan]
[bold white]Активный интерфейс:[/bold white] [green]{info.active_interface}[/green]
[bold white]MAC-адрес:[/bold white]          [cyan]{info.mac_address}[/cyan]
[bold white]DNS-серверы:[/bold white]        {', '.join(info.dns_servers)}
[bold white]Публичный IP (WAN):[/bold white] [magenta]{info.public_ip or 'Не удалось определить'}[/magenta]"""
        console.print(Panel(panel_content, title="🌐 Информация о текущем подключении", border_style="cyan"))
    else:
        print(f"\n{C_CYAN}{C_BOLD}--- Информация о текущем сетевом подключении ---{C_RESET}")
        print(f"Локальный IP-адрес:      {C_BOLD}{info.local_ip}{C_RESET}")
        print(f"Основной шлюз (Gateway): {C_BOLD}{info.gateway_ip}{C_RESET}")
        print(f"Сетевой интерфейс:       {C_GREEN}{info.active_interface}{C_RESET}")
        print(f"MAC-адрес адаптера:      {info.mac_address}")
        print(f"DNS-серверы:             {', '.join(info.dns_servers)}")
        print(f"Внешний (публичный) IP:  {C_MAGENTA}{info.public_ip or 'Не удалось определить'}{C_RESET}\n")


async def run_ping_cli(target: str, count: int = 4, timeout: int = 2):
    """Executes real Ping and prints live output."""
    service = AsyncPingService()
    print(f"\n{C_CYAN}{C_BOLD}Запуск реального Ping до {target}... (Ctrl+C для отмены){C_RESET}\n")

    summary_shown = False
    try:
        async for item in service.ping_stream(target, count=count, timeout_seconds=timeout):
            if isinstance(item, PingPacket):
                sig_color = C_GREEN if item.rtt_ms < 40 else (C_YELLOW if item.rtt_ms < 90 else C_RED)
                print(f"{item.bytes_count} bytes from {item.ip}: seq={item.sequence} ttl={item.ttl} time={sig_color}{item.rtt_ms} ms{C_RESET}")
            elif isinstance(item, PingSummary):
                if not summary_shown:
                    summary_shown = True
                    print(f"\n{C_BOLD}--- Статистика Ping для {item.target} ---{C_RESET}")
                    print(f"Пакетов: передано {item.packets_transmitted}, получено {item.packets_received}, потери {item.packet_loss_percent}%")
                    print(f"{C_GREEN}rtt min/avg/max = {item.min_rtt_ms} / {item.avg_rtt_ms} / {item.max_rtt_ms} ms{C_RESET}\n")
            else:
                print(f"{C_GRAY}{item}{C_RESET}")
    except KeyboardInterrupt:
        print(f"\n{C_YELLOW}Пинг прерван пользователем.{C_RESET}")


async def run_traceroute_cli(target: str, max_hops: int = 30, timeout: int = 2):
    """Executes real Traceroute and prints live hops."""
    service = AsyncTracerouteService()
    print(f"\n{C_CYAN}{C_BOLD}Запуск реальной трассировки к {target}... (Ctrl+C для отмены){C_RESET}\n")

    try:
        async for hop in service.traceroute_stream(target, max_hops=max_hops, timeout_seconds=timeout):
            if hop.is_timeout:
                print(f" {C_BOLD}{hop.hop_number:>2}{C_RESET}  {C_RED}* * * (Превышен интервал ожидания){C_RESET}")
            else:
                host_disp = f" ({hop.host_name})" if hop.host_name else ""
                rtt_color = C_GREEN if hop.rtt_ms and hop.rtt_ms < 35 else (C_YELLOW if hop.rtt_ms and hop.rtt_ms < 80 else C_RED)
                flag = f" {C_CYAN}[Цель достигнута!]{C_RESET}" if hop.is_target_reached else ""
                print(f" {C_BOLD}{hop.hop_number:>2}{C_RESET}  {hop.ip}{host_disp}  {rtt_color}{hop.rtt_ms} ms{C_RESET}{flag}")

            if hop.is_target_reached:
                print(f"\n{C_GREEN}✓ Трассировка успешно достигла целевого хоста!{C_RESET}\n")
                break
    except KeyboardInterrupt:
        print(f"\n{C_YELLOW}Трассировка прервана пользователем.{C_RESET}")


async def interactive_menu():
    """Main menu loop for interactive CLI execution."""
    scanner = WifiScanner()
    net_service = NetworkInfoService()

    while True:
        print_banner()
        print("1. 📡 Сканировать Wi-Fi сети и анализировать Mesh / Репитеры")
        print("2. 🔍 Демонстрационный анализ Mesh-эфира (Keenetic, Xiaomi, Extenders)")
        print("3. 🏓 Запустить реальный Пинг (Ping)")
        print("4. 🗺️  Запустить реальную Трассировку (Traceroute)")
        print("5. 🌐 Информация о текущем сетевом подключении (IP, Gateway, DNS)")
        print("6. ❌ Выход")

        try:
            choice = input("\nВыберите действие [1-6]: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nВыход из программы. До свидания!")
            break

        if choice == "1":
            print(f"\n{C_CYAN}Сканирование реального радиоэфира через системный адаптер...{C_RESET}")
            groups = await scanner.scan_and_analyze()
            if not groups:
                print(f"{C_YELLOW}Внимание: Физический беспроводной адаптер не вернул точек доступа (нет Wi-Fi карты или отключен).{C_RESET}")
                sub = input("Показать анализ эталонного multi-BSSID Mesh/Repeater эфира? [y/N]: ").strip().lower()
                if sub == "y":
                    sample_nodes = WifiScanner.get_sample_mesh_capture_nodes()
                    sample_groups = scanner.aggregate_groups(sample_nodes)
                    display_wifi_groups(sample_groups)
            else:
                display_wifi_groups(groups)
            input(f"\n{C_GRAY}Нажмите Enter для продолжения...{C_RESET}")
        elif choice == "2":
            sample_nodes = WifiScanner.get_sample_mesh_capture_nodes()
            sample_groups = scanner.aggregate_groups(sample_nodes)
            print(f"\n{C_CYAN}Анализ эталонного эфира (Keenetic Mesh, TP-Link Deco, ASUS + Tenda Repeater):{C_RESET}")
            display_wifi_groups(sample_groups)
            input(f"\n{C_GRAY}Нажмите Enter для продолжения...{C_RESET}")
        elif choice == "3":
            target = input("Введите целевой хост (IP или домен, по умолч. 8.8.8.8): ").strip() or "8.8.8.8"
            count_str = input("Количество пакетов (по умолч. 4, 0 для непрерывного): ").strip() or "4"
            count = int(count_str) if count_str.isdigit() else 4
            await run_ping_cli(target, count=count)
            input(f"{C_GRAY}Нажмите Enter для продолжения...{C_RESET}")
        elif choice == "4":
            target = input("Введите целевой хост для трассировки (по умолч. 8.8.8.8): ").strip() or "8.8.8.8"
            await run_traceroute_cli(target)
            input(f"{C_GRAY}Нажмите Enter для продолжения...{C_RESET}")
        elif choice == "5":
            print(f"\n{C_CYAN}Определение параметров подключения...{C_RESET}")
            info = await net_service.get_connection_info()
            display_connection_info(info)
            input(f"{C_GRAY}Нажмите Enter для продолжения...{C_RESET}")
        elif choice in ("6", "q", "exit"):
            print("Выход из программы. До свидания!")
            break
        else:
            print("Неверный выбор. Попробуйте снова.")


def main():
    parser = argparse.ArgumentParser(
        description="WiFi Pulse & Network Suite: Wi-Fi Mesh/Repeater Scanner, Ping & Traceroute"
    )
    parser.add_argument("--scan", action="store_true", help="Запустить сканирование эфира Wi-Fi")
    parser.add_argument("--sample", action="store_true", help="Проанализировать демонстрационный Mesh/Repeater эфир")
    parser.add_argument("--ping", type=str, metavar="HOST", help="Запустить реальный пинг до указанного хоста")
    parser.add_argument("-c", "--count", type=int, default=4, help="Количество пакетов пинга (по умолч. 4)")
    parser.add_argument("--trace", type=str, metavar="HOST", help="Запустить трассировку (traceroute) до хоста")
    parser.add_argument("--info", action="store_true", help="Отобразить информацию о текущем сетевом подключении")
    parser.add_argument("--web", action="store_true", help="Запустить веб-сервер с Tailwind UI дашбордом")
    parser.add_argument("--port", type=int, default=8000, help="Порт для веб-сервера (по умолчанию 8000)")

    args = parser.parse_args()

    if args.web:
        try:
            import uvicorn
            from .web_server import create_app
            app = create_app()
            print(f"Запуск веб-дашборда на http://127.0.0.1:{args.port}...")
            uvicorn.run(app, host="0.0.0.0", port=args.port)
            return
        except ImportError:
            print("Для запуска веб-сервера установите fastapi и uvicorn: pip install fastapi uvicorn")
            sys.exit(1)

    if args.scan:
        scanner = WifiScanner()
        groups = asyncio.run(scanner.scan_and_analyze())
        display_wifi_groups(groups)
    elif args.sample:
        scanner = WifiScanner()
        sample_nodes = WifiScanner.get_sample_mesh_capture_nodes()
        groups = scanner.aggregate_groups(sample_nodes)
        display_wifi_groups(groups)
    elif args.ping:
        asyncio.run(run_ping_cli(args.ping, count=args.count))
    elif args.trace:
        asyncio.run(run_traceroute_cli(args.trace))
    elif args.info:
        service = NetworkInfoService()
        info = asyncio.run(service.get_connection_info())
        display_connection_info(info)
    else:
        # Launch interactive menu
        asyncio.run(interactive_menu())


if __name__ == "__main__":
    main()
