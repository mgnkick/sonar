import React, { useState, useEffect, useRef } from 'react';
import { WifiNetwork, SignalHistorySample, WifiBand } from '../types/wifi';
import { Volume2, VolumeX, Sparkles, TrendingUp, BarChart2, Star, CheckCircle, Info } from 'lucide-react';
import { audioBeeper } from '../utils/audioBeeper';

interface AndroidSignalAnalysisProps {
  networks: WifiNetwork[];
  selectedBssid: string;
  onSelectNetwork: (bssid: string) => void;
  signalHistory: SignalHistorySample[];
  isDark: boolean;
}

export const AndroidSignalAnalysis: React.FC<AndroidSignalAnalysisProps> = ({
  networks,
  selectedBssid,
  onSelectNetwork,
  signalHistory,
  isDark
}) => {
  const [subTab, setSubTab] = useState<'timeline' | 'spectrum' | 'ratings'>('timeline');
  const [spectrumBand, setSpectrumBand] = useState<WifiBand>('2.4 GHz');
  const [ratingsBand, setRatingsBand] = useState<WifiBand>('2.4 GHz');
  const [beeperActive, setBeeperActive] = useState(false);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const spectrumCanvasRef = useRef<HTMLCanvasElement>(null);

  const selectedNet =
    networks.find((n) => n.bssid === selectedBssid) || networks[0];

  // Update audio beeper with current RSSI
  useEffect(() => {
    if (selectedNet) {
      audioBeeper.updateRssi(selectedNet.rssi);
    }
  }, [selectedNet?.rssi]);

  const toggleBeeper = () => {
    if (beeperActive) {
      audioBeeper.stop();
      setBeeperActive(false);
    } else {
      audioBeeper.start();
      if (selectedNet) audioBeeper.updateRssi(selectedNet.rssi);
      setBeeperActive(true);
    }
  };

  useEffect(() => {
    return () => {
      audioBeeper.stop();
    };
  }, []);

  // Timeline canvas render
  useEffect(() => {
    if (subTab !== 'timeline') return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = canvas.width;
    const height = canvas.height;

    ctx.clearRect(0, 0, width, height);

    // Draw background grid lines (-30, -50, -70, -90 dBm)
    const levels = [-30, -50, -70, -90];
    levels.forEach((dbm) => {
      const y = ((-dbm - 30) / 70) * (height - 30) + 10;
      ctx.strokeStyle = isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(35, y);
      ctx.lineTo(width, y);
      ctx.stroke();

      ctx.fillStyle = isDark ? 'rgba(255, 255, 255, 0.4)' : 'rgba(0, 0, 0, 0.4)';
      ctx.font = '10px monospace';
      ctx.fillText(`${dbm}`, 5, y + 3);
    });

    if (signalHistory.length < 2) return;

    const points = signalHistory.slice(-25);
    const stepX = (width - 45) / Math.max(1, points.length - 1);

    // Draw smooth gradient filled area
    ctx.beginPath();
    points.forEach((pt, idx) => {
      const normY = Math.max(0, Math.min(1, (-pt.rssi - 30) / 70));
      const x = 40 + idx * stepX;
      const y = normY * (height - 30) + 10;
      if (idx === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });

    const linePath = new Path2D();
    points.forEach((pt, idx) => {
      const normY = Math.max(0, Math.min(1, (-pt.rssi - 30) / 70));
      const x = 40 + idx * stepX;
      const y = normY * (height - 30) + 10;
      if (idx === 0) linePath.moveTo(x, y);
      else linePath.lineTo(x, y);
    });

    // Fill under curve
    ctx.lineTo(40 + (points.length - 1) * stepX, height - 10);
    ctx.lineTo(40, height - 10);
    ctx.closePath();

    const grad = ctx.createLinearGradient(0, 0, 0, height);
    grad.addColorStop(0, 'rgba(0, 229, 255, 0.35)');
    grad.addColorStop(1, 'rgba(0, 229, 255, 0.0)');
    ctx.fillStyle = grad;
    ctx.fill();

    // Draw line
    ctx.strokeStyle = '#00E5FF';
    ctx.lineWidth = 2.5;
    ctx.stroke(linePath);

    // Draw last point pulsing circle
    const lastPt = points[points.length - 1];
    const lastNormY = Math.max(0, Math.min(1, (-lastPt.rssi - 30) / 70));
    const lastX = 40 + (points.length - 1) * stepX;
    const lastY = lastNormY * (height - 30) + 10;

    ctx.beginPath();
    ctx.arc(lastX, lastY, 4.5, 0, Math.PI * 2);
    ctx.fillStyle = '#00E5FF';
    ctx.fill();
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 1.5;
    ctx.stroke();
  }, [signalHistory, subTab, isDark]);

  // Spectrum canvas render (parabolic curves)
  useEffect(() => {
    if (subTab !== 'spectrum') return;
    const canvas = spectrumCanvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = canvas.width;
    const height = canvas.height;

    ctx.clearRect(0, 0, width, height);

    const is24 = spectrumBand === '2.4 GHz';
    const minCh = is24 ? 1 : 36;
    const maxCh = is24 ? 14 : 165;
    const span = maxCh - minCh;

    // Draw Baseline
    const baseY = height - 26;
    ctx.strokeStyle = isDark ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.2)';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(10, baseY);
    ctx.lineTo(width - 10, baseY);
    ctx.stroke();

    // Draw Channel markers on axis
    const markChannels = is24
      ? [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14]
      : [36, 40, 44, 48, 52, 56, 60, 64, 100, 112, 128, 149, 157, 165];

    markChannels.forEach((ch) => {
      const x = 20 + ((ch - minCh) / span) * (width - 40);
      ctx.fillStyle = isDark ? 'rgba(255, 255, 255, 0.5)' : 'rgba(0, 0, 0, 0.5)';
      ctx.font = '9px monospace';
      ctx.textAlign = 'center';
      ctx.fillText(`${ch}`, x, height - 10);

      ctx.beginPath();
      ctx.moveTo(x, baseY);
      ctx.lineTo(x, baseY + 4);
      ctx.stroke();
    });

    // Draw Parabolic curves for each network in band
    const bandNets = networks.filter((n) => n.band === spectrumBand);

    bandNets.forEach((net) => {
      const centerX = 20 + ((net.channel - minCh) / span) * (width - 40);
      // Normalized peak from RSSI (-100 to -35)
      const normStrength = Math.max(0.15, Math.min(1, (net.rssi + 100) / 65));
      const peakY = baseY - normStrength * (height - 50);

      const curveSpread = is24 ? (width / 14) * 2.1 : (width / span) * 12;

      ctx.beginPath();
      ctx.moveTo(centerX - curveSpread, baseY);
      ctx.quadraticCurveTo(centerX, peakY, centerX + curveSpread, baseY);

      ctx.fillStyle = `${net.color}33`; // 20% alpha
      ctx.fill();

      ctx.strokeStyle = net.color;
      ctx.lineWidth = net.bssid === selectedBssid ? 3 : 1.8;
      ctx.stroke();

      // Label at peak
      ctx.fillStyle = isDark ? '#FFFFFF' : '#0F172A';
      ctx.font = 'bold 9px sans-serif';
      ctx.textAlign = 'center';
      const label = net.ssid.length > 10 ? net.ssid.slice(0, 9) + '…' : net.ssid;
      ctx.fillText(label, centerX, peakY - 4);
    });
  }, [networks, spectrumBand, subTab, selectedBssid, isDark]);

  // Channel Ratings calculation
  const getChannelRatings = (band: WifiBand) => {
    const channels =
      band === '2.4 GHz'
        ? [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]
        : [36, 40, 44, 48, 52, 56, 60, 64, 149, 153, 157, 161, 165];

    const bandNets = networks.filter((n) => n.band === band);

    return channels.map((ch) => {
      const interferers = bandNets.filter((n) =>
        band === '2.4 GHz' ? Math.abs(n.channel - ch) < 3 : n.channel === ch
      );

      const penalty = interferers.reduce((acc, curr) => {
        return acc + (curr.rssi > -65 ? 3 : curr.rssi > -78 ? 2 : 1);
      }, 0);

      const score = Math.max(1, 10 - penalty);
      const isRecommended =
        band === '2.4 GHz'
          ? (ch === 1 || ch === 6 || ch === 11) && score >= 7
          : score >= 8;

      return {
        channel: ch,
        score,
        interferersCount: interferers.length,
        isRecommended
      };
    });
  };

  const ratings = getChannelRatings(ratingsBand);
  const bestChannels = ratings.filter((r) => r.isRecommended).map((r) => r.channel);

  return (
    <div className="flex flex-col h-full overflow-y-auto select-none pb-20">
      {/* Top Header */}
      <div className={`p-4 border-b ${isDark ? 'border-slate-800 bg-slate-900/90' : 'border-slate-200 bg-white/90'} sticky top-0 z-10 backdrop-blur-md`}>
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-bold tracking-tight">Анализ сигнала</h1>
            <p className="text-xs text-slate-400">Спектр, интерференция и рейтинг</p>
          </div>
          <button
            onClick={toggleBeeper}
            className={`p-2 rounded-xl text-xs flex items-center gap-1.5 font-medium transition ${
              beeperActive
                ? 'bg-cyan-500 text-slate-950 font-semibold shadow-md shadow-cyan-500/30 animate-pulse'
                : isDark
                ? 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
            title="Звуковой индикатор уровня сигнала"
          >
            {beeperActive ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
            <span>Бипер</span>
          </button>
        </div>

        {/* Selected Network Selector Bar */}
        {selectedNet && (
          <div
            className={`mt-3 p-2.5 rounded-xl border flex items-center justify-between ${
              isDark ? 'bg-slate-800/80 border-slate-700' : 'bg-slate-100 border-slate-200'
            }`}
          >
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: selectedNet.color }} />
                <span className="text-xs font-bold truncate">{selectedNet.ssid}</span>
              </div>
              <p className="text-[10px] text-slate-400 font-mono mt-0.5">
                {selectedNet.bssid} • CH {selectedNet.channel} • {selectedNet.band}
              </p>
            </div>
            <div className="text-right">
              <span className="text-xs font-bold font-mono text-cyan-400">
                {selectedNet.rssi} dBm
              </span>
              <p className="text-[10px] text-slate-400">~{selectedNet.distanceMeters} м</p>
            </div>
          </div>
        )}

        {/* Sub-tabs */}
        <div className="grid grid-cols-3 gap-1 mt-3 p-1 rounded-xl bg-slate-800/40 border border-slate-700/50">
          {[
            { id: 'timeline', label: 'График' },
            { id: 'spectrum', label: 'Спектр' },
            { id: 'ratings', label: 'Рейтинг' }
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setSubTab(tab.id as any)}
              className={`py-1.5 text-xs font-semibold rounded-lg transition ${
                subTab === tab.id
                  ? 'bg-cyan-500 text-slate-950 shadow-sm'
                  : isDark
                  ? 'text-slate-400 hover:text-slate-200'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Tab 1: Timeline */}
      {subTab === 'timeline' && (
        <div className="p-4 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Динамика RSSI (дБм во времени)
            </span>
            <span className="text-[11px] text-cyan-400 font-mono">
              Частота обновления: 1 с
            </span>
          </div>

          <div
            className={`p-3 rounded-2xl border ${
              isDark ? 'bg-slate-900 border-slate-800' : 'bg-white border-slate-200 shadow-sm'
            }`}
          >
            <canvas
              ref={canvasRef}
              width={340}
              height={200}
              className="w-full h-[200px]"
            />
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div
              className={`p-3 rounded-xl border text-center ${
                isDark ? 'bg-slate-900/60 border-slate-800' : 'bg-slate-50 border-slate-200'
              }`}
            >
              <span className="text-[10px] text-slate-400 block">Текущий</span>
              <span className="text-sm font-bold font-mono text-cyan-400">
                {selectedNet?.rssi} dBm
              </span>
            </div>
            <div
              className={`p-3 rounded-xl border text-center ${
                isDark ? 'bg-slate-900/60 border-slate-800' : 'bg-slate-50 border-slate-200'
              }`}
            >
              <span className="text-[10px] text-slate-400 block">Дистанция</span>
              <span className="text-sm font-bold text-slate-200">
                ~{selectedNet?.distanceMeters} м
              </span>
            </div>
            <div
              className={`p-3 rounded-xl border text-center ${
                isDark ? 'bg-slate-900/60 border-slate-800' : 'bg-slate-50 border-slate-200'
              }`}
            >
              <span className="text-[10px] text-slate-400 block">Полоса</span>
              <span className="text-sm font-bold text-slate-200">
                {selectedNet?.channelWidth} МГц
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Spectrum */}
      {subTab === 'spectrum' && (
        <div className="p-4 space-y-4">
          <div className="flex gap-2">
            {(['2.4 GHz', '5 GHz'] as const).map((b) => (
              <button
                key={b}
                onClick={() => setSpectrumBand(b)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  spectrumBand === b
                    ? 'bg-cyan-500 text-slate-950 font-bold'
                    : isDark
                    ? 'bg-slate-800 text-slate-300'
                    : 'bg-slate-100 text-slate-700'
                }`}
              >
                {b}
              </button>
            ))}
          </div>

          <div
            className={`p-3 rounded-2xl border ${
              isDark ? 'bg-slate-900 border-slate-800' : 'bg-white border-slate-200 shadow-sm'
            }`}
          >
            <canvas
              ref={spectrumCanvasRef}
              width={340}
              height={220}
              className="w-full h-[220px]"
            />
          </div>

          <div
            className={`p-3 rounded-xl border text-xs leading-relaxed ${
              isDark
                ? 'bg-cyan-500/5 border-cyan-500/20 text-slate-300'
                : 'bg-cyan-50 border-cyan-200 text-slate-700'
            }`}
          >
            <div className="flex items-center gap-1.5 font-bold text-cyan-400 mb-1">
              <Sparkles className="w-4 h-4" />
              Совет по радиочастотам:
            </div>
            {spectrumBand === '2.4 GHz' ? (
              <p>
                В диапазоне 2.4 ГГц только каналы <strong>1, 6 и 11</strong> не перекрывают друг друга.
                Пересекающиеся каналы создают коллизии пакетов и снижают реальную скорость передачи.
              </p>
            ) : (
              <p>
                В диапазоне 5 ГГц полоса шире (до 80/160 МГц). Каналы <strong>36, 44, 149</strong>{' '}
                обеспечивают максимальную пропускную способность без взаимного подавления.
              </p>
            )}
          </div>
        </div>
      )}

      {/* Tab 3: Ratings */}
      {subTab === 'ratings' && (
        <div className="p-4 space-y-4">
          <div className="flex gap-2">
            {(['2.4 GHz', '5 GHz'] as const).map((b) => (
              <button
                key={b}
                onClick={() => setRatingsBand(b)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  ratingsBand === b
                    ? 'bg-cyan-500 text-slate-950 font-bold'
                    : isDark
                    ? 'bg-slate-800 text-slate-300'
                    : 'bg-slate-100 text-slate-700'
                }`}
              >
                {b}
              </button>
            ))}
          </div>

          {bestChannels.length > 0 && (
            <div
              className={`p-3 rounded-xl border flex items-center gap-2.5 ${
                isDark
                  ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                  : 'bg-emerald-50 border-emerald-200 text-emerald-800'
              }`}
            >
              <CheckCircle className="w-5 h-5 flex-shrink-0 text-emerald-400" />
              <div className="text-xs">
                <span className="font-bold block">Рекомендуемые каналы для роутера:</span>
                Канал {bestChannels.join(', ')} — минимум шумов и коллизий
              </div>
            </div>
          )}

          <div className="space-y-2">
            {ratings.map((r) => (
              <div
                key={r.channel}
                className={`p-2.5 rounded-xl border flex items-center justify-between text-xs transition ${
                  r.isRecommended
                    ? isDark
                      ? 'bg-cyan-500/10 border-cyan-500/30'
                      : 'bg-cyan-50 border-cyan-300'
                    : isDark
                    ? 'bg-slate-900 border-slate-800'
                    : 'bg-white border-slate-200'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <span
                    className={`w-7 h-7 rounded-lg flex items-center justify-center font-bold font-mono text-xs ${
                      r.isRecommended
                        ? 'bg-cyan-500 text-slate-950'
                        : isDark
                        ? 'bg-slate-800 text-slate-300'
                        : 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    {r.channel}
                  </span>
                  <div>
                    <span className="font-semibold block">Канал {r.channel}</span>
                    <span className="text-[10px] text-slate-400">
                      {r.interferersCount === 0
                        ? 'Чистый эфир (0 помех)'
                        : `Точек на канале / рядом: ${r.interferersCount}`}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <div className="flex items-center gap-0.5">
                    {[...Array(5)].map((_, i) => (
                      <Star
                        key={i}
                        className={`w-3.5 h-3.5 ${
                          i < Math.round(r.score / 2)
                            ? 'text-amber-400 fill-amber-400'
                            : 'text-slate-600'
                        }`}
                      />
                    ))}
                  </div>
                  <span
                    className={`font-mono font-bold text-xs ${
                      r.score >= 8
                        ? 'text-emerald-400'
                        : r.score >= 5
                        ? 'text-amber-400'
                        : 'text-rose-400'
                    }`}
                  >
                    {r.score}/10
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
