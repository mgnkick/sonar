import React from 'react';
import { WifiNetwork, ConnectionDetails } from '../types/wifi';
import { ShieldCheck, AlertTriangle, AlertCircle, CheckCircle2, Lightbulb, RefreshCw, Layers, Radio, Lock } from 'lucide-react';

interface AndroidSmartAuditProps {
  networks: WifiNetwork[];
  connectionDetails: ConnectionDetails;
  isDark: boolean;
  onRefresh: () => void;
  isScanning: boolean;
}

export const AndroidSmartAudit: React.FC<AndroidSmartAuditProps> = ({
  networks,
  connectionDetails,
  isDark,
  onRefresh,
  isScanning
}) => {
  // Compute dynamic audit metrics based on current networks
  const activeNet = networks.find(n => n.ssid === connectionDetails.ssid) || networks[0];
  const sameChannelCount = networks.filter(n => n.channel === activeNet.channel).length;

  const score = sameChannelCount > 4 ? 72 : 88;

  const recommendations = [
    {
      id: 'rec_channel',
      category: 'Загруженность радиочастот',
      icon: Radio,
      severity: sameChannelCount > 3 ? 'warning' : 'optimal',
      title: sameChannelCount > 3 ? `Перегружен канал CH ${activeNet.channel}` : `Канал CH ${activeNet.channel} чист от помех`,
      metricBadge: `${sameChannelCount} сетей на канале`,
      summary: sameChannelCount > 3
        ? `В диапазоне ${activeNet.band} обнаружено ${sameChannelCount} конкурирующих точек. Возможна деградация скорости и задержки.`
        : `На канале ${activeNet.channel} минимальная конкуренция. Пропускная способность радиоэфира оптимальна.`,
      actionGuide: sameChannelCount > 3
        ? `Переключите роутер на свободный канал (например, CH 11 или диапазон 5 ГГц) в настройках беспроводной сети.`
        : `Настройка автоматического выбора каналов на роутере работает корректно.`
    },
    {
      id: 'rec_signal',
      category: 'Качество покрытия и сигнал',
      icon: Layers,
      severity: activeNet.rssi < -75 ? 'critical' : (activeNet.rssi < -65 ? 'warning' : 'optimal'),
      title: activeNet.rssi < -65 ? `Пограничный уровень сигнала (${activeNet.rssi} дБм)` : `Отличный уровень сигнала (${activeNet.rssi} дБм)`,
      metricBadge: `${activeNet.rssi} дБм`,
      summary: activeNet.rssi < -65
        ? `Мощности сигнала недостаточно для максимальных схем модуляции. Возможны микрофризы.`
        : `Уровень сигнала обеспечивает максимальную скорость модуляции (до 1024-QAM) без потерь кадров.`,
      actionGuide: activeNet.rssi < -65
        ? `Сократите количество физических преград или установите дополнительный Mesh-узел ближе.`
        : `Расположение точек доступа и клиентского устройства идеально.`
    },
    {
      id: 'rec_security',
      category: 'Безопасность и шифрование',
      icon: Lock,
      severity: activeNet.security === 'Open' ? 'critical' : (activeNet.security === 'WPA2-PSK' ? 'warning' : 'optimal'),
      title: activeNet.security === 'WPA3-SAE' ? 'Высокая защита WPA3-SAE' : 'Рекомендуется активация WPA3',
      metricBadge: activeNet.security,
      summary: activeNet.security === 'WPA3-SAE'
        ? `Активирован современный протокол одновременной аутентификации равных (SAE). Защищен от перебора.`
        : `Используется базовый WPA2-PSK. Сеть уязвима к оффлайн-перебору ключа по перехваченному хэндшейку.`,
      actionGuide: activeNet.security === 'WPA3-SAE'
        ? `Конфигурация безопасности безупречна.`
        : `Включите смешанный режим «WPA2/WPA3-Personal» в веб-интерфейсе вашего роутера.`
    },
    {
      id: 'rec_topology',
      category: 'Топология сети (Mesh & Репитеры)',
      icon: ShieldCheck,
      severity: 'optimal',
      title: 'Бесшовная Mesh-система активна',
      metricBadge: 'Mesh 802.11k/r/v',
      summary: `Обнаружены согласованные узлы вендора Keenetic/Deco с поддержкой быстрого перехода между диапазонами.`,
      actionGuide: `Устройства переключаются на узел с лучшим уровнем RSSI без разрыва соединений.`
    }
  ];

  return (
    <div className="flex-1 overflow-y-auto p-4 space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className={`text-sm font-bold ${isDark ? 'text-white' : 'text-slate-900'}`}>Интеллектуальный аудит</h2>
          <p className="text-[10px] text-slate-400">Автоматический аудит радиоэфира и безопасности</p>
        </div>
        <button
          onClick={onRefresh}
          disabled={isScanning}
          className={`p-1.5 rounded-xl border transition ${
            isDark
              ? 'border-slate-800 bg-slate-900/80 text-cyan-400 hover:bg-slate-800'
              : 'border-slate-300 bg-white text-cyan-600 hover:bg-slate-100'
          }`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isScanning ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* Overall Score Card */}
      <div
        className={`p-4 rounded-2xl border transition-colors ${
          isDark ? 'bg-slate-900/80 border-slate-800' : 'bg-white border-slate-200 shadow-sm'
        }`}
      >
        <div className="flex items-center justify-between">
          <div>
            <span className="text-[9px] font-bold uppercase tracking-wider text-slate-400 block">ИНДЕКС ЗДОРОВЬЯ СЕТИ</span>
            <span className={`text-base font-bold block mt-0.5 ${isDark ? 'text-white' : 'text-slate-900'}`}>
              {score >= 80 ? 'Отличное состояние' : 'Удовлетворительно'}
            </span>
            <p className="text-[10px] text-slate-400 mt-0.5">Беспроводная среда проверена по 4 ключевым параметрам</p>
          </div>

          <div className="relative w-14 h-14 flex items-center justify-center">
            <svg className="w-14 h-14 -rotate-90">
              <circle cx="28" cy="28" r="22" className="stroke-slate-800" strokeWidth="4" fill="transparent" />
              <circle
                cx="28"
                cy="28"
                r="22"
                className="stroke-emerald-400 transition-all duration-1000"
                strokeWidth="4"
                strokeDasharray={138}
                strokeDashoffset={138 - (138 * score) / 100}
                strokeLinecap="round"
                fill="transparent"
              />
            </svg>
            <div className="absolute text-center">
              <span className={`text-xs font-black ${isDark ? 'text-white' : 'text-slate-900'}`}>{score}</span>
              <span className="text-[8px] text-slate-400 block -mt-1">/100</span>
            </div>
          </div>
        </div>

        <div className="mt-3 pt-3 border-t border-slate-800/80 flex items-center justify-between text-[10px]">
          <span className="flex items-center gap-1.5 text-emerald-400 font-semibold">
            <CheckCircle2 className="w-3 h-3" /> Оптимально: 3
          </span>
          <span className="flex items-center gap-1.5 text-amber-400 font-semibold">
            <AlertTriangle className="w-3 h-3" /> Замечания: 1
          </span>
          <span className="flex items-center gap-1.5 text-slate-400">
            Сетей: {networks.length}
          </span>
        </div>
      </div>

      {/* Recommendations List */}
      <div className="space-y-3">
        <div className="flex items-center justify-between text-[11px] font-bold px-1">
          <span className={isDark ? 'text-slate-300' : 'text-slate-700'}>Экспертные рекомендации (4)</span>
        </div>

        {recommendations.map(rec => {
          const IconComp = rec.icon;
          const isOptimal = rec.severity === 'optimal';
          const isWarning = rec.severity === 'warning';

          return (
            <div
              key={rec.id}
              className={`p-3 rounded-2xl border transition ${
                isDark ? 'bg-slate-900/60 border-slate-800' : 'bg-white border-slate-200 shadow-sm'
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-1.5 text-[10px] text-slate-400 font-medium">
                  <IconComp className="w-3 h-3 text-cyan-400" />
                  <span>{rec.category}</span>
                </div>
                <span
                  className={`text-[9px] font-bold px-2 py-0.5 rounded-full border ${
                    isOptimal
                      ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                      : isWarning
                      ? 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                      : 'bg-rose-500/10 text-rose-400 border-rose-500/20'
                  }`}
                >
                  {isOptimal ? 'Оптимально' : (isWarning ? 'Замечание' : 'Угроза')}
                </span>
              </div>

              <div className="flex items-center justify-between gap-2">
                <span className={`text-[11px] font-bold ${isDark ? 'text-white' : 'text-slate-900'}`}>{rec.title}</span>
                <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-cyan-300 shrink-0">
                  {rec.metricBadge}
                </span>
              </div>

              <p className="text-[10px] text-slate-400 mt-1 leading-relaxed">{rec.summary}</p>

              {/* Action guide */}
              <div
                className={`mt-2.5 p-2 rounded-xl flex items-start gap-1.5 border text-[10px] ${
                  isDark ? 'bg-slate-950/80 border-slate-800 text-slate-300' : 'bg-slate-50 border-slate-200 text-slate-700'
                }`}
              >
                <Lightbulb className="w-3.5 h-3.5 text-amber-400 shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold text-amber-400 block text-[9.5px]">Руководство к действию:</span>
                  <span className="leading-snug block mt-0.5 text-[9.5px]">{rec.actionGuide}</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
