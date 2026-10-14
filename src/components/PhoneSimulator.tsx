import React, { useState, useEffect } from 'react';
import { WifiNetwork, ConnectionDetails, SignalHistorySample } from '../types/wifi';
import { AndroidDashboard } from './AndroidDashboard';
import { AndroidSignalAnalysis } from './AndroidSignalAnalysis';
import { AndroidSmartAudit } from './AndroidSmartAudit';
import { AndroidConnectionInfo } from './AndroidConnectionInfo';
import { AndroidPermissions } from './AndroidPermissions';
import { AndroidDiagnostics } from './AndroidDiagnostics';
import { Wifi, BatteryMedium, Signal, Sun, Moon, Radio, BarChart3, Info, Route, ShieldCheck } from 'lucide-react';

interface PhoneSimulatorProps {
  networks: WifiNetwork[];
  isScanning: boolean;
  cooldownSeconds: number;
  onScan: () => void;
  selectedBssid: string;
  onSelectNetwork: (bssid: string) => void;
  signalHistory: SignalHistorySample[];
  connectionDetails: ConnectionDetails;
  onRefreshConnection: () => void;
}

export const PhoneSimulator: React.FC<PhoneSimulatorProps> = ({
  networks,
  isScanning,
  cooldownSeconds,
  onScan,
  selectedBssid,
  onSelectNetwork,
  signalHistory,
  connectionDetails,
  onRefreshConnection
}) => {
  const [currentTab, setCurrentTab] = useState<'dashboard' | 'audit' | 'diagnostics' | 'analysis'>('dashboard');
  const [hasPermissions, setHasPermissions] = useState(true);
  const [isDark, setIsDark] = useState(true);
  const [currentTime, setCurrentTime] = useState('12:00');

  useEffect(() => {
    const updateClock = () => {
      const now = new Date();
      setCurrentTime(
        `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
      );
    };
    updateClock();
    const interval = setInterval(updateClock, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="flex flex-col items-center">
      {/* Simulator Device Shell */}
      <div
        className={`relative w-[370px] h-[740px] rounded-[48px] p-3 shadow-2xl transition-colors duration-300 border-4 ${
          isDark
            ? 'bg-slate-950 border-slate-800 shadow-cyan-500/10'
            : 'bg-slate-200 border-slate-300 shadow-slate-400/20'
        }`}
      >
        {/* Device Screen Body */}
        <div
          className={`relative w-full h-full rounded-[38px] overflow-hidden flex flex-col transition-colors duration-300 ${
            isDark ? 'bg-slate-950 text-slate-100' : 'bg-slate-50 text-slate-900'
          }`}
        >
          {/* Top Status Bar */}
          <div className="h-9 px-6 pt-2 flex items-center justify-between text-[11px] font-semibold tracking-tight z-30 select-none">
            <span className="font-mono">{currentTime}</span>

            {/* Front Camera Punch-hole */}
            <div className="w-3.5 h-3.5 rounded-full bg-black ring-1 ring-slate-800 shadow-inner" />

            <div className="flex items-center gap-1.5 text-xs">
              <Wifi className="w-3.5 h-3.5 text-cyan-400" />
              <Signal className="w-3.5 h-3.5" />
              <div className="flex items-center gap-0.5 font-mono text-[10px]">
                <span>94%</span>
                <BatteryMedium className="w-4 h-4" />
              </div>
            </div>
          </div>

          {/* Screen Content */}
          <div className="flex-1 relative overflow-hidden">
            {!hasPermissions ? (
              <AndroidPermissions
                onGrant={() => {
                  setHasPermissions(true);
                  onScan();
                }}
                isDark={isDark}
              />
            ) : currentTab === 'dashboard' ? (
              <AndroidDashboard
                networks={networks}
                isScanning={isScanning}
                cooldownSeconds={cooldownSeconds}
                onScan={onScan}
                selectedBssid={selectedBssid}
                onSelectNetwork={onSelectNetwork}
                onNavigateToAnalysis={() => setCurrentTab('analysis')}
                isDark={isDark}
              />
            ) : currentTab === 'audit' ? (
              <AndroidSmartAudit
                networks={networks}
                connectionDetails={connectionDetails}
                isDark={isDark}
                onRefresh={onScan}
                isScanning={isScanning}
              />
            ) : currentTab === 'diagnostics' ? (
              <AndroidDiagnostics
                isDark={isDark}
                gatewayIp={connectionDetails.gateway}
              />
            ) : (
              <AndroidSignalAnalysis
                networks={networks}
                selectedBssid={selectedBssid}
                onSelectNetwork={onSelectNetwork}
                signalHistory={signalHistory}
                isDark={isDark}
              />
            )}
          </div>

          {/* Android Jetpack Compose NavigationBar (Bottom Nav) */}
          {hasPermissions && (
            <div
              className={`h-16 px-2 border-t flex items-center justify-around z-20 transition-colors ${
                isDark ? 'border-slate-800/80 bg-slate-950/95' : 'border-slate-200 bg-white/95'
              } backdrop-blur-md`}
            >
              {[
                { id: 'dashboard', label: 'Сети', icon: Radio },
                { id: 'audit', label: 'Аудит', icon: ShieldCheck },
                { id: 'diagnostics', label: 'Диагностика', icon: Route },
                { id: 'analysis', label: 'Спектр', icon: BarChart3 }
              ].map((item) => {
                const IconComp = item.icon;
                const isActive = currentTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => setCurrentTab(item.id as any)}
                    className="flex flex-col items-center justify-center gap-0.5 flex-1 transition active:scale-95"
                  >
                    <div
                      className={`px-4 py-1 rounded-full transition ${
                        isActive
                          ? 'bg-cyan-500/20 text-cyan-400 font-bold'
                          : isDark
                          ? 'text-slate-400 hover:text-slate-200'
                          : 'text-slate-500 hover:text-slate-800'
                      }`}
                    >
                      <IconComp className="w-4 h-4" />
                    </div>
                    <span
                      className={`text-[10px] tracking-tight ${
                        isActive
                          ? 'text-cyan-400 font-bold'
                          : isDark
                          ? 'text-slate-400'
                          : 'text-slate-500'
                      }`}
                    >
                      {item.label}
                    </span>
                  </button>
                );
              })}
            </div>
          )}

          {/* Bottom Android Gesture Pill */}
          <div className="h-3 flex items-center justify-center pb-1">
            <div className={`w-32 h-1 rounded-full ${isDark ? 'bg-slate-700' : 'bg-slate-300'}`} />
          </div>
        </div>
      </div>

      {/* Under-Phone Controls */}
      <div className="mt-4 flex items-center gap-2">
        <button
          onClick={() => setIsDark(!isDark)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-700/60 bg-slate-800/60 hover:bg-slate-700/60 text-slate-300 text-xs transition"
          title="Сменить тему оформления"
        >
          {isDark ? <Sun className="w-3.5 h-3.5 text-amber-400" /> : <Moon className="w-3.5 h-3.5 text-cyan-400" />}
          <span>{isDark ? 'Светлая тема' : 'Темная тема'}</span>
        </button>

        <button
          onClick={() => setHasPermissions(!hasPermissions)}
          className="px-3 py-1.5 rounded-xl border border-slate-700/60 bg-slate-800/60 hover:bg-slate-700/60 text-slate-300 text-xs transition"
        >
          {hasPermissions ? 'Тест: Сброс прав' : 'Тест: Права выданы'}
        </button>
      </div>
    </div>
  );
};
