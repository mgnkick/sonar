"""
Optional real-time Web Dashboard for WiFi Pulse using FastAPI & WebSockets.
Provides real-time browser UI with Tailwind CSS.
"""

import asyncio
import json
from dataclasses import asdict
from typing import List

from .wifi_scanner import WifiScanner
from .ping_service import AsyncPingService
from .traceroute_service import AsyncTracerouteService
from .network_info import NetworkInfoService
from .models import PingPacket, PingSummary, TracerouteHop

HTML_DASHBOARD = """<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>WiFi Pulse - Python Network Suite</title>
  <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-950 text-slate-100 min-h-screen font-sans">
  <div class="max-w-6xl mx-auto p-4 md:p-8">
    <header class="flex items-center justify-between pb-6 border-b border-slate-800">
      <div>
        <h1 class="text-2xl font-bold text-cyan-400">WiFi Pulse &amp; Network Suite</h1>
        <p class="text-xs text-slate-400 mt-1">Python 3.10+ • Wi-Fi Mesh-сканер, реальный Ping, Traceroute и Connection Info</p>
      </div>
      <div id="connStatus" class="px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
        WebSocket: Подключение...
      </div>
    </header>

    <!-- Connection Info Banner -->
    <div id="networkInfoBar" class="mt-6 p-4 rounded-2xl bg-slate-900 border border-slate-800">
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-xs font-bold uppercase tracking-wider text-cyan-400">🌐 Параметры сетевого подключения</h3>
        <button onclick="sendAction('get_net_info')" class="text-xs text-slate-400 hover:text-slate-200">Обновить</button>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3 text-xs">
        <div>
          <span class="text-slate-500 block text-[11px]">Локальный IP</span>
          <span id="infoLocalIp" class="font-mono font-bold text-slate-200">Загрузка...</span>
        </div>
        <div>
          <span class="text-slate-500 block text-[11px]">Основной шлюз</span>
          <span id="infoGateway" class="font-mono text-slate-300">-</span>
        </div>
        <div>
          <span class="text-slate-500 block text-[11px]">Интерфейс</span>
          <span id="infoIface" class="font-mono text-emerald-400">-</span>
        </div>
        <div>
          <span class="text-slate-500 block text-[11px]">DNS-серверы</span>
          <span id="infoDns" class="font-mono text-slate-300">-</span>
        </div>
        <div>
          <span class="text-slate-500 block text-[11px]">Публичный IP (WAN)</span>
          <span id="infoPublicIp" class="font-mono text-purple-400">-</span>
        </div>
      </div>
    </div>

    <!-- Controls -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-6">
      <div class="p-4 rounded-2xl bg-slate-900 border border-slate-800">
        <h3 class="text-sm font-bold text-slate-200 mb-2">1. Сканер эфира (Wi-Fi)</h3>
        <div class="flex gap-2">
          <button onclick="sendAction('scan_wifi')" class="flex-1 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs transition">
            Сканировать эфир
          </button>
          <button onclick="sendAction('scan_sample')" title="Демонстрация Mesh/Repeater топологии" class="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold text-xs transition">
            Демо Mesh
          </button>
        </div>
      </div>

      <div class="p-4 rounded-2xl bg-slate-900 border border-slate-800">
        <h3 class="text-sm font-bold text-slate-200 mb-2">2. Реальный Ping</h3>
        <div class="flex gap-2">
          <input id="pingTarget" type="text" value="8.8.8.8" class="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-3 py-1.5 text-xs text-slate-200">
          <button onclick="startPing()" class="px-4 py-1.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition">
            Пинг
          </button>
        </div>
      </div>

      <div class="p-4 rounded-2xl bg-slate-900 border border-slate-800">
        <h3 class="text-sm font-bold text-slate-200 mb-2">3. Трассировка (Traceroute)</h3>
        <div class="flex gap-2">
          <input id="traceTarget" type="text" value="8.8.8.8" class="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-3 py-1.5 text-xs text-slate-200">
          <button onclick="startTrace()" class="px-4 py-1.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs transition">
            Трасса
          </button>
        </div>
      </div>
    </div>

    <!-- Results Views -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-6">
      <!-- Wi-Fi Networks Group Table -->
      <div class="p-4 rounded-2xl bg-slate-900 border border-slate-800">
        <h2 class="text-base font-bold text-slate-100 mb-3">📡 Точки доступа и Mesh-структура</h2>
        <div id="wifiResults" class="space-y-3 max-h-[520px] overflow-y-auto text-xs">
          <div class="text-slate-500 text-center py-8">Нажмите «Сканировать эфир» или «Демо Mesh» для просмотра сетей</div>
        </div>
      </div>

      <!-- Realtime Terminal Console -->
      <div class="p-4 rounded-2xl bg-slate-900 border border-slate-800 flex flex-col">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-base font-bold text-slate-100">Терминал диагностики</h2>
          <button onclick="clearConsole()" class="text-xs text-slate-400 hover:text-slate-200">Очистить</button>
        </div>
        <div id="terminal" class="flex-1 min-h-[350px] max-h-[520px] overflow-y-auto bg-black p-3 rounded-xl font-mono text-xs text-slate-300 space-y-1">
          <div class="text-slate-600">// Готов к сетевой диагностике</div>
        </div>
      </div>
    </div>
  </div>

  <script>
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const ws = new WebSocket(`${protocol}//${location.host}/ws`);
    const term = document.getElementById('terminal');
    const wifiResults = document.getElementById('wifiResults');
    const connStatus = document.getElementById('connStatus');

    ws.onopen = () => {
      connStatus.textContent = 'WebSocket: Подключено';
      connStatus.className = 'px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
      sendAction('get_net_info');
    };

    ws.onclose = () => {
      connStatus.textContent = 'WebSocket: Отключен';
      connStatus.className = 'px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/20 text-rose-400 border border-rose-500/30';
    };

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === 'log') {
        const line = document.createElement('div');
        line.textContent = data.text;
        line.className = data.highlight ? 'text-cyan-400 font-bold' : (data.error ? 'text-rose-400' : 'text-slate-300');
        term.appendChild(line);
        term.scrollTop = term.scrollHeight;
      } else if (data.type === 'wifi_scan') {
        renderWifi(data.groups);
      } else if (data.type === 'net_info') {
        renderNetInfo(data.info);
      }
    };

    function sendAction(action, payload = {}) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action, ...payload }));
      }
    }

    function startPing() {
      const target = document.getElementById('pingTarget').value;
      sendAction('start_ping', { target, count: 4 });
    }

    function startTrace() {
      const target = document.getElementById('traceTarget').value;
      sendAction('start_trace', { target, max_hops: 20 });
    }

    function clearConsole() {
      term.innerHTML = '<div class="text-slate-600">// Лог очищен</div>';
    }

    function renderNetInfo(info) {
      if (!info) return;
      document.getElementById('infoLocalIp').textContent = info.local_ip || '-';
      document.getElementById('infoGateway').textContent = info.gateway_ip || '-';
      document.getElementById('infoIface').textContent = info.active_interface || '-';
      document.getElementById('infoDns').textContent = (info.dns_servers || []).join(', ') || '-';
      document.getElementById('infoPublicIp').textContent = info.public_ip || 'Оффлайн';
    }

    function renderWifi(groups) {
      if (!groups || groups.length === 0) {
        wifiResults.innerHTML = '<div class="text-amber-400 text-center py-4">Точки не найдены или нет Wi-Fi адаптера</div>';
        return;
      }
      wifiResults.innerHTML = groups.map(g => `
        <div class="p-3 rounded-xl border border-slate-800 bg-slate-950/60">
          <div class="flex items-center justify-between">
            <span class="font-bold text-sm text-slate-100">${g.ssid}</span>
            <span class="px-2 py-0.5 rounded text-[10px] font-bold ${g.topology_type === 'MESH_NETWORK' ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' : (g.topology_type === 'REPEATER_DETECTED' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30' : 'bg-slate-800 text-slate-400')}">
              ${g.topology_type}
            </span>
          </div>
          <div class="flex items-center gap-2 mt-1 text-slate-400 text-[11px]">
            <span class="text-cyan-400">${g.primary_vendor}</span>
            <span>•</span>
            <span>${g.total_nodes_count} BSSID</span>
            <span>•</span>
            <span class="text-emerald-400 font-mono font-bold">${g.best_rssi} dBm</span>
          </div>
          ${g.nodes.length > 1 ? `
            <div class="mt-2 pt-2 border-t border-slate-800/80 space-y-1.5 pl-2 text-[10px] font-mono">
              ${g.nodes.map(n => `
                <div class="flex items-center justify-between text-slate-400">
                  <span>${n.node_role === 'MASTER_ROUTER' ? '👑 Master' : (n.node_role === 'REPEATER_SUSPECT' ? '⚠️ Репитер' : '🛰️ Mesh')} ${n.bssid}</span>
                  <span>CH ${n.channel} (${n.band})</span>
                  <span class="${n.rssi >= -65 ? 'text-emerald-400' : 'text-amber-400'} font-bold">${n.rssi} dBm</span>
                </div>
              `).join('')}
            </div>
          ` : ''}
        </div>
      `).join('');
    }
  </script>
</body>
</html>
"""

def create_app():
    """Builds FastAPI application with WebSocket streaming support."""
    try:
        from fastapi import FastAPI, WebSocket, WebSocketDisconnect
        from fastapi.responses import HTMLResponse
    except ImportError:
        raise RuntimeError("Для запуска веб-интерфейса установите fastapi и uvicorn: pip install fastapi uvicorn")

    app = FastAPI(title="WiFi Pulse Network Suite")
    scanner = WifiScanner()
    ping_service = AsyncPingService()
    trace_service = AsyncTracerouteService()
    net_service = NetworkInfoService()

    @app.get("/", response_class=HTMLResponse)
    async def index():
        return HTML_DASHBOARD

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket):
        await websocket.accept()
        try:
            while True:
                msg_text = await websocket.receive_text()
                msg = json.loads(msg_text)
                action = msg.get("action")

                if action == "get_net_info":
                    info = await net_service.get_connection_info()
                    await websocket.send_json({
                        "type": "net_info",
                        "info": asdict(info)
                    })

                elif action in ("scan_wifi", "scan_sample"):
                    if action == "scan_sample":
                        await websocket.send_json({"type": "log", "text": "Загрузка эталонного multi-BSSID Mesh/Repeater эфира...", "highlight": True})
                        sample_nodes = WifiScanner.get_sample_mesh_capture_nodes()
                        groups = scanner.aggregate_groups(sample_nodes)
                    else:
                        await websocket.send_json({"type": "log", "text": "Сканирование физического радиоэфира...", "highlight": True})
                        groups = await scanner.scan_and_analyze()

                    # Convert dataclasses to dict
                    clean_groups = []
                    for g in groups:
                        clean_groups.append({
                            "ssid": g.ssid,
                            "topology_type": g.topology_type.value,
                            "primary_vendor": g.primary_vendor,
                            "has_mixed_vendors": g.has_mixed_vendors,
                            "best_rssi": g.best_rssi,
                            "total_nodes_count": g.total_nodes_count,
                            "nodes": [
                                {
                                    "bssid": n.bssid,
                                    "rssi": n.rssi,
                                    "channel": n.channel,
                                    "band": n.band.value,
                                    "vendor": n.vendor,
                                    "node_role": n.node_role.value
                                }
                                for n in g.nodes
                            ]
                        })
                    await websocket.send_json({"type": "wifi_scan", "groups": clean_groups})
                    await websocket.send_json({"type": "log", "text": f"✓ Обработка завершена. Найдено сетей: {len(groups)}", "highlight": True})

                elif action == "start_ping":
                    target = msg.get("target", "8.8.8.8")
                    count = int(msg.get("count", 4))
                    summary_sent = False
                    async for item in ping_service.ping_stream(target, count=count):
                        if isinstance(item, PingPacket):
                            txt = f"{item.bytes_count} bytes from {item.ip}: seq={item.sequence} time={item.rtt_ms} ms"
                            await websocket.send_json({"type": "log", "text": txt})
                        elif isinstance(item, PingSummary):
                            if not summary_sent:
                                summary_sent = True
                                txt = f"--- Итог: отправлено {item.packets_transmitted}, получено {item.packets_received}, потери {item.packet_loss_percent}% (avg {item.avg_rtt_ms} ms) ---"
                                await websocket.send_json({"type": "log", "text": txt, "highlight": True})
                        else:
                            await websocket.send_json({"type": "log", "text": str(item)})

                elif action == "start_trace":
                    target = msg.get("target", "8.8.8.8")
                    max_hops = int(msg.get("max_hops", 20))
                    await websocket.send_json({"type": "log", "text": f"Трассировка маршрута к {target}...", "highlight": True})
                    async for hop in trace_service.traceroute_stream(target, max_hops=max_hops):
                        if hop.is_timeout:
                            txt = f"Hop {hop.hop_number:>2}: * * * (Таймаут)"
                            await websocket.send_json({"type": "log", "text": txt, "error": True})
                        else:
                            host_str = f" ({hop.host_name})" if hop.host_name else ""
                            reached_flag = " [Цель достигнута]" if hop.is_target_reached else ""
                            txt = f"Hop {hop.hop_number:>2}: {hop.ip}{host_str} - {hop.rtt_ms} ms{reached_flag}"
                            await websocket.send_json({"type": "log", "text": txt})
        except WebSocketDisconnect:
            pass

    return app
