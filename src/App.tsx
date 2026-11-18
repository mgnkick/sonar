import React, { useState, useEffect } from 'react';
import { INITIAL_NETWORKS, INITIAL_CONNECTION } from './utils/mockWifiData';
import { WifiNetwork, SignalHistorySample, ConnectionDetails } from './types/wifi';
import { PhoneSimulator } from './components/PhoneSimulator';
import { CodeExplorer } from './components/CodeExplorer';
import { GitHubWorkflowViewer } from './components/GitHubWorkflowViewer';
import { GitHubGuideModal } from './components/GitHubGuideModal';
import { downloadAndroidProjectZip } from './utils/projectZipGenerator';
import {
  Smartphone,
  FileCode2,
  GitBranch,
  Download,
  HelpCircle,
  Radio,
  Layers,
  Cpu,
  ShieldCheck,
  CheckCircle2,
  Sparkles
} from 'lucide-react';

export default function App() {
  const [networks, setNetworks] = useState<WifiNetwork[]>(INITIAL_NETWORKS);
  const [selectedBssid, setSelectedBssid] = useState<string>(INITIAL_NETWORKS[0].bssid);
  const [isScanning, setIsScanning] = useState(false);
  const [cooldownSeconds, setCooldownSeconds] = useState(0);
  const [signalHistory, setSignalHistory] = useState<SignalHistorySample[]>([
    { timestamp: Date.now() - 4000, rssi: -48 },
    { timestamp: Date.now() - 3000, rssi: -47 },
    { timestamp: Date.now() - 2000, rssi: -49 },
    { timestamp: Date.now() - 1000, rssi: -48 }
  ]);
  const [connectionDetails, setConnectionDetails] = useState<ConnectionDetails>(INITIAL_CONNECTION);

  // Active workstation tab: 'simulator' | 'code' | 'cicd'
  const [activePane, setActivePane] = useState<'simulator' | 'code' | 'cicd'>('simulator');
  const [isGuideOpen, setIsGuideOpen] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);

  // Live real-time Wi-Fi signal fluctuation simulation (RF physics modeling)
  useEffect(() => {
    const interval = setInterval(() => {
      setNetworks((prev) =>
        prev.map((net) => {
          // Add small realistic RF noise +/- 2 dBm
          const delta = (Math.random() - 0.5) * 3;
          const newRssi = Math.round(Math.max(-95, Math.min(-35, net.rssi + delta)));
          return {
            ...net,
            rssi: newRssi
          };
        })
      );
    }, 1500);

    return () => clearInterval(interval);
  }, []);

  // Update signal history for selected network
  useEffect(() => {
    const selected = networks.find((n) => n.bssid === selectedBssid);
    if (selected) {
      setSignalHistory((prev) => [
        ...prev.slice(-35),
        { timestamp: Date.now(), rssi: selected.rssi }
      ]);
    }
  }, [networks, selectedBssid]);

  // Handle Scan trigger with 15s Android Scan Throttling simulation
  const handleScan = () => {
    if (cooldownSeconds > 0 || isScanning) return;
    setIsScanning(true);

    setTimeout(() => {
      // Re-sort networks or refresh slightly
      setNetworks((prev) =>
        [...prev].sort((a, b) => b.rssi - a.rssi)
      );
      setIsScanning(false);

      // Start 15s cooldown
      setCooldownSeconds(15);
      const timer = setInterval(() => {
        setCooldownSeconds((c) => {
          if (c <= 1) {
            clearInterval(timer);
            return 0;
          }
          return c - 1;
        });
      }, 1000);
    }, 1200);
  };

  const handleDownload = async () => {
    setIsDownloading(true);
    try {
      await downloadAndroidProjectZip();
    } catch (e) {
      console.error(e);
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-cyan-500 selection:text-black">
      {/* Top Main Navigation Header */}
      <header className="border-b border-slate-800/80 bg-slate-950/80 backdrop-blur-xl sticky top-0 z-40 px-4 lg:px-8 py-3.5">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          {/* Logo & Status Badge */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center text-slate-950 shadow-lg shadow-cyan-500/20">
              <Radio className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base font-bold tracking-tight text-white">WiFi Sonar</h1>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-cyan-500/10 text-cyan-400 border border-cyan-500/30">
                  Android 8.0+ · версия 0.9.33
                </span>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-purple-500/10 text-purple-400 border border-purple-500/30 hidden sm:inline-flex">
                  Jetpack Compose &amp; CI/CD
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Android-приложение для обследования Wi-Fi · актуальная версия исходников 0.9.33
              </p>
            </div>
          </div>

          {/* Navigation Controls */}
          <div className="flex items-center gap-2 flex-wrap">
            {/* View Switcher Tabs */}
            <div className="p-1 rounded-2xl bg-slate-900 border border-slate-800 flex items-center gap-1">
              <button
                onClick={() => setActivePane('simulator')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  activePane === 'simulator'
                    ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Smartphone className="w-3.5 h-3.5" />
                <span>Эмулятор</span>
              </button>

              <button
                onClick={() => setActivePane('code')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  activePane === 'code'
                    ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <FileCode2 className="w-3.5 h-3.5" />
                <span>Шаблон Android</span>
              </button>

              <button
                onClick={() => setActivePane('cicd')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  activePane === 'cicd'
                    ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/20'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <GitBranch className="w-3.5 h-3.5" />
                <span>CI/CD Workflow</span>
              </button>
            </div>

            {/* Step-by-step Guide Button */}
            <button
              onClick={() => setIsGuideOpen(true)}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-slate-800 bg-slate-900/90 hover:bg-slate-800 text-slate-200 text-xs font-medium transition"
            >
              <HelpCircle className="w-3.5 h-3.5 text-cyan-400" />
              <span>Инструкция GitHub</span>
            </button>

            {/* 1-Click ZIP Download Button */}
            <button
              onClick={handleDownload}
              disabled={isDownloading}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-500 text-slate-950 text-xs font-bold shadow-lg shadow-cyan-500/25 hover:brightness-110 active:scale-95 transition"
            >
              <Download className={`w-3.5 h-3.5 ${isDownloading ? 'animate-bounce' : ''}`} />
              <span>{isDownloading ? 'Сборка архива...' : 'Скачать шаблон .ZIP'}</span>
            </button>
            <a
              href="https://github.com/mgnkick/sonar/releases/latest/download/app-release.apk"
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl border border-cyan-400/50 bg-cyan-950/60 hover:bg-cyan-900/70 text-cyan-100 text-xs font-bold transition"
              aria-label="Скачать APK из последнего опубликованного GitHub Release WiFi Sonar"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Скачать APK</span>
            </a>
          </div>
        </div>
      </header>

      {/* Main Workspace Body */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 lg:p-8 flex flex-col justify-center">
        {activePane === 'simulator' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left: Interactive Android Device Simulator */}
            <div className="lg:col-span-5 flex justify-center">
              <PhoneSimulator
                networks={networks}
                isScanning={isScanning}
                cooldownSeconds={cooldownSeconds}
                onScan={handleScan}
                selectedBssid={selectedBssid}
                onSelectNetwork={setSelectedBssid}
                signalHistory={signalHistory}
                connectionDetails={connectionDetails}
                onRefreshConnection={() => {
                  setConnectionDetails((prev) => ({
                    ...prev,
                    linkSpeedMbps: Math.floor(Math.random() * 400) + 600
                  }));
                }}
              />
            </div>

            {/* Right: Feature Highlights & Quick Action Center */}
            <div className="lg:col-span-7 space-y-5">
              <div className="p-4 rounded-2xl border border-amber-500/30 bg-amber-500/5 text-xs leading-relaxed text-amber-100">
                <strong className="block mb-1">Демонстрация интерфейса</strong>
                В эмуляторе показаны тестовые сети и примерные результаты. Он не сканирует Wi-Fi-эфир телефона и не заменяет проверки в Android-приложении. Для реальных измерений установите APK; кнопка скачивания ведёт на последний опубликованный GitHub Release.
              </div>
              {/* Architecture & Stack Card */}
              <div className="p-6 rounded-3xl border border-slate-800 bg-slate-900/60 backdrop-blur-md space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-5 h-5 text-cyan-400" />
                    <h2 className="text-base font-bold text-white">
                      Архитектура мобильного приложения
                    </h2>
                  </div>
                  <span className="text-xs font-mono text-emerald-400 font-semibold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Исходники 0.9.33
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                  <div className="p-3 rounded-2xl border border-slate-800 bg-slate-950/60">
                    <span className="text-slate-400 block text-[11px]">Язык и стек</span>
                    <strong className="text-slate-100 text-sm mt-0.5 block">Kotlin 2.0.0</strong>
                    <span className="text-slate-400 text-[11px]">Jetpack Compose (Material 3)</span>
                  </div>

                  <div className="p-3 rounded-2xl border border-slate-800 bg-slate-950/60">
                    <span className="text-slate-400 block text-[11px]">Система сборки</span>
                    <strong className="text-slate-100 text-sm mt-0.5 block">Gradle 8.7 (Kotlin DSL)</strong>
                    <span className="text-slate-400 text-[11px]">libs.versions.toml Catalog</span>
                  </div>

                  <div className="p-3 rounded-2xl border border-slate-800 bg-slate-950/60">
                    <span className="text-slate-400 block text-[11px]">Системные API Android</span>
                    <strong className="text-slate-100 text-sm mt-0.5 block">WifiManager, ScanResult</strong>
                    <span className="text-slate-400 text-[11px]">ConnectivityManager, LinkProperties</span>
                  </div>

                  <div className="p-3 rounded-2xl border border-slate-800 bg-slate-950/60">
                    <span className="text-slate-400 block text-[11px]">CI/CD Автоматизация</span>
                    <strong className="text-slate-100 text-sm mt-0.5 block">GitHub Actions (.yml)</strong>
                    <span className="text-slate-400 text-[11px]">JDK 17 + сборка .apk артефакта</span>
                  </div>
                </div>

                {/* Key Features List */}
                <div className="pt-2 border-t border-slate-800/80 space-y-2 text-xs text-slate-300">
                  <div className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-cyan-400 mt-0.5 flex-shrink-0" />
                    <span>
                      <strong>Сканирование сетей</strong>: обнаружение точек доступа в диапазонах 2,4, 5 и 6 ГГц, группировка SSID и BSSID, фильтры и оценка уровня сигнала.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-cyan-400 mt-0.5 flex-shrink-0" />
                    <span>
                      <strong>Сигнал и радиоэфир</strong>: график RSSI, отслеживание выбранной точки доступа, спектр видимых сетей и оценки каналов. Это анализ данных Wi-Fi scan, а не аппаратный RF-анализатор.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-cyan-400 mt-0.5 flex-shrink-0" />
                    <span>
                      <strong>Смарт-аудит</strong>: рекомендации по перекрытию каналов 2,4 ГГц, ширине 40 МГц, DFS, открытым сетям, качеству сигнала и безопасности. Балл и загруженность — эвристическая оценка по видимым сетям.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-cyan-400 mt-0.5 flex-shrink-0" />
                    <span>
                      <strong>Диагностика сети</strong>: Ping, трассировка с анализом потерь, поиск устройств ЭХО и проверка TCP-портов в локальной подсети; есть пошаговая проверка шлюза, DNS и интернета и консоль с управлением запуском.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-cyan-400 mt-0.5 flex-shrink-0" />
                    <span>
                      <strong>Подключение и мониторинг</strong>: IP, шлюз, маска, DNS, скорости Rx/Tx, проверка отклика шлюза и фоновое системное уведомление.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-cyan-400 mt-0.5 flex-shrink-0" />
                    <span>
                      <strong>Карта покрытия</strong>: загрузка плана или фото, точки замеров, отметки оборудования и фигур, локальное сохранение и экспорт схемы в PNG.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-cyan-400 mt-0.5 flex-shrink-0" />
                    <span>
                      <strong>История и перенос данных</strong>: журнал подключений и обнаруженных открытых сетей, экспорт отчётов и ZIP-резервная копия. Пароли Wi-Fi в копию не входят.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-cyan-400 mt-0.5 flex-shrink-0" />
                    <span>
                      <strong>Подсказки «?»</strong>: пояснения к инструментам и интерпретации результатов на русском и английском языках.
                    </span>
                  </div>
                </div>
              </div>

              {/* Quick Actions Card */}
              <div className="p-6 rounded-3xl border border-cyan-500/20 bg-gradient-to-r from-cyan-950/20 to-blue-950/20 backdrop-blur-md flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h3 className="text-sm font-bold text-white">Готовы собрать APK на GitHub?</h3>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Скачайте стартовый шаблон Android-проекта или просмотрите его файлы и пример GitHub Actions.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setActivePane('code')}
                    className="px-3.5 py-2 rounded-xl border border-slate-700 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 transition"
                  >
                    Открыть шаблон
                  </button>
                  <button
                    onClick={() => setIsGuideOpen(true)}
                    className="px-4 py-2 rounded-xl bg-cyan-500 text-slate-950 text-xs font-bold shadow-md shadow-cyan-500/20 hover:brightness-110 active:scale-95 transition"
                  >
                    Инструкция Push
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {activePane === 'code' && <CodeExplorer />}

        {activePane === 'cicd' && <GitHubWorkflowViewer />}
      </main>

      {/* GitHub Setup Guide Modal */}
      <GitHubGuideModal isOpen={isGuideOpen} onClose={() => setIsGuideOpen(false)} />
    </div>
  );
}
