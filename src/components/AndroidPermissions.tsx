import React from 'react';
import { MapPin, Radio, ShieldAlert, Check, Settings } from 'lucide-react';

interface AndroidPermissionsProps {
  onGrant: () => void;
  isDark: boolean;
}

export const AndroidPermissions: React.FC<AndroidPermissionsProps> = ({ onGrant, isDark }) => {
  return (
    <div className="flex flex-col h-full justify-center p-6 text-center select-none">
      <div className="w-16 h-16 rounded-3xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 mx-auto mb-4">
        <Radio className="w-8 h-8 animate-pulse" />
      </div>

      <h2 className="text-xl font-bold tracking-tight mb-2">Требуются разрешения</h2>
      <p className="text-xs text-slate-400 leading-relaxed mb-6">
        Политика безопасности Android (начиная с Android 8.0 Oreo и Android 13+) требует специальных разрешений для получения списка BSSID/SSID окружающих Wi-Fi сетей.
      </p>

      <div className="space-y-3 text-left mb-6">
        <div
          className={`p-3 rounded-2xl border flex items-start gap-3 ${
            isDark ? 'bg-slate-900 border-slate-800' : 'bg-slate-50 border-slate-200'
          }`}
        >
          <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 mt-0.5">
            <MapPin className="w-4 h-4" />
          </div>
          <div>
            <h4 className="text-xs font-bold text-slate-200">
              Точная геолокация (ACCESS_FINE_LOCATION)
            </h4>
            <p className="text-[11px] text-slate-400 leading-tight mt-0.5">
              Предотвращает скрытую идентификацию физического положения устройства через окружающие Wi-Fi маяки.
            </p>
          </div>
        </div>

        <div
          className={`p-3 rounded-2xl border flex items-start gap-3 ${
            isDark ? 'bg-slate-900 border-slate-800' : 'bg-slate-50 border-slate-200'
          }`}
        >
          <div className="p-2 rounded-xl bg-purple-500/10 text-purple-400 mt-0.5">
            <Radio className="w-4 h-4" />
          </div>
          <div>
            <h4 className="text-xs font-bold text-slate-200">
              Устройства поблизости (NEARBY_WIFI_DEVICES)
            </h4>
            <p className="text-[11px] text-slate-400 leading-tight mt-0.5">
              Для современных устройств на базе Android 13 (API 33) и выше.
            </p>
          </div>
        </div>
      </div>

      <button
        onClick={onGrant}
        className="w-full py-3 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-500 text-slate-950 font-bold text-xs shadow-lg shadow-cyan-500/25 hover:brightness-110 active:scale-95 transition"
      >
        Предоставить разрешения
      </button>
    </div>
  );
};
