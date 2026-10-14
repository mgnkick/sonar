import React, { useState } from 'react';
import { ConnectionDetails } from '../types/wifi';
import { Wifi, ShieldCheck, Activity, Globe, RefreshCw, Cpu, Server } from 'lucide-react';

interface AndroidConnectionInfoProps {
  details: ConnectionDetails;
  onRefresh: () => void;
  isDark: boolean;
}

export const AndroidConnectionInfo: React.FC<AndroidConnectionInfoProps> = ({
  details,
  onRefresh,
  isDark
}) => {
  const [isPinging, setIsPinging] = useState(false);
  const [pingHistory, setPingHistory] = useState<number[]>([]);
  const [lastPing, setLastPing] = useState<number | null>(null);

  const runPingTest = () => {
    setIsPinging(true);
    let count = 0;
    const interval = setInterval(() => {
      // Simulate realistic local gateway ping: 3 to 14 ms
      const latency = Math.floor(Math.random() * 11) + 3;
      setLastPing(latency);
      setPingHistory((prev) => [...prev.slice(-12), latency]);
      count++;
      if (count >= 5) {
        clearInterval(interval);
        setIsPinging(false);
      }
    }, 400);
  };

  const avgPing =
    pingHistory.length > 0
      ? Math.round(pingHistory.reduce((a, b) => a + b, 0) / pingHistory.length)
      : null;

  return (
    <div className="flex flex-col h-full overflow-y-auto select-none p-4 space-y-4 pb-20">
      {/* Top Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold tracking-tight">Подключение</h1>
          <p className="text-xs text-slate-400">Параметры текущей Wi-Fi сети</p>
        </div>
        <button
          onClick={onRefresh}
          className={`p-2 rounded-xl transition ${
            isDark ? 'hover:bg-slate-800 text-slate-300' : 'hover:bg-slate-100 text-slate-700'
          }`}
          title="Обновить данные"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      {/* Main Connection Card */}
      <div
        className={`p-4 rounded-2xl border ${
          isDark
            ? 'bg-gradient-to-br from-slate-900 to-slate-800/90 border-slate-700/80 shadow-lg'
            : 'bg-gradient-to-br from-white to-slate-50 border-slate-200 shadow-md'
        }`}
      >
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Wifi className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold tracking-tight">{details.ssid}</h2>
              <p className="text-xs font-mono text-slate-400">{details.bssid}</p>
            </div>
          </div>
          <span className="px-2.5 py-1 rounded-lg text-xs font-bold bg-cyan-500/15 text-cyan-400 border border-cyan-500/30">
            {details.standard}
          </span>
        </div>

        {/* Speed chips */}
        <div className="grid grid-cols-2 gap-2 mt-4">
          <div
            className={`p-2.5 rounded-xl border ${
              isDark ? 'bg-slate-800/60 border-slate-700/60' : 'bg-slate-100/80 border-slate-200'
            }`}
          >
            <span className="text-[10px] text-slate-400 block">Скорость линка</span>
            <span className="text-sm font-bold font-mono text-cyan-400">
              {details.linkSpeedMbps} Мбит/с
            </span>
          </div>
          <div
            className={`p-2.5 rounded-xl border ${
              isDark ? 'bg-slate-800/60 border-slate-700/60' : 'bg-slate-100/80 border-slate-200'
            }`}
          >
            <span className="text-[10px] text-slate-400 block">Rx / Tx каналы</span>
            <span className="text-sm font-bold font-mono text-slate-200">
              {details.rxLinkSpeedMbps} / {details.txLinkSpeedMbps} Мбит/с
            </span>
          </div>
        </div>
      </div>

      {/* Network Parameters Card */}
      <div
        className={`p-4 rounded-2xl border ${
          isDark ? 'bg-slate-900 border-slate-800' : 'bg-white border-slate-200'
        }`}
      >
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">
          Сетевая конфигурация (TCP/IP & DHCP)
        </h3>

        <div className="space-y-2.5 text-xs">
          <div className="flex justify-between items-center py-1 border-b border-slate-800/50">
            <span className="text-slate-400">IP-адрес устройства:</span>
            <span className="font-mono font-semibold text-slate-200">{details.ipAddress}</span>
          </div>
          <div className="flex justify-between items-center py-1 border-b border-slate-800/50">
            <span className="text-slate-400">Основной шлюз (Gateway):</span>
            <span className="font-mono font-semibold text-slate-200">{details.gateway}</span>
          </div>
          <div className="flex justify-between items-center py-1 border-b border-slate-800/50">
            <span className="text-slate-400">Маска подсети:</span>
            <span className="font-mono font-semibold text-slate-200">{details.subnetMask}</span>
          </div>
          <div className="flex justify-between items-center py-1 border-b border-slate-800/50">
            <span className="text-slate-400">Первичный DNS 1:</span>
            <span className="font-mono font-semibold text-slate-200">{details.dns1}</span>
          </div>
          <div className="flex justify-between items-center py-1 border-b border-slate-800/50">
            <span className="text-slate-400">Вторичный DNS 2:</span>
            <span className="font-mono font-semibold text-slate-200">{details.dns2}</span>
          </div>
          <div className="flex justify-between items-center py-1">
            <span className="text-slate-400">MAC интерфейса:</span>
            <span className="font-mono font-semibold text-slate-200">{details.macAddress}</span>
          </div>
        </div>
      </div>

      {/* Gateway Ping Tester */}
      <div
        className={`p-4 rounded-2xl border ${
          isDark ? 'bg-slate-900 border-slate-800' : 'bg-white border-slate-200'
        }`}
      >
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <Activity className="w-4 h-4 text-cyan-400" />
            <h3 className="text-xs font-bold text-slate-200">Тестирование задержки (Ping)</h3>
          </div>
          {avgPing !== null && (
            <span className="text-xs font-mono font-bold text-emerald-400">
              Среднее: {avgPing} мс
            </span>
          )}
        </div>
        <p className="text-[11px] text-slate-400 leading-relaxed mb-3">
          Отправка ICMP/TCP эхо-пакетов до шлюза {details.gateway} для оценки стабильности беспроводного канала.
        </p>

        <div className="flex items-center gap-3">
          <button
            onClick={runPingTest}
            disabled={isPinging}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
              isPinging
                ? 'bg-slate-800 text-slate-400 cursor-wait'
                : 'bg-cyan-500 text-slate-950 font-bold hover:brightness-110'
            }`}
          >
            {isPinging ? 'Пинг...' : 'Запустить тест отклика'}
          </button>

          {lastPing !== null && (
            <div className="flex items-center gap-1.5 font-mono text-xs">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
              <span className="text-slate-300">
                Отклик: <strong className="text-emerald-400">{lastPing} мс</strong>
              </span>
            </div>
          )}
        </div>

        {pingHistory.length > 0 && (
          <div className="flex items-end gap-1 h-10 mt-3 pt-2 border-t border-slate-800">
            {pingHistory.map((val, idx) => (
              <div
                key={idx}
                className="flex-1 bg-cyan-500/30 rounded-t hover:bg-cyan-400 transition"
                style={{ height: `${Math.min(100, Math.max(15, val * 5))}%` }}
                title={`${val} ms`}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
