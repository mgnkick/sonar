import React, { useState } from 'react';
import { WifiNetwork, WifiBand, WifiNetworkGroup, BssIdNode } from '../types/wifi';
import { buildNetworkGroups } from '../utils/mockWifiData';
import {
  Search,
  RotateCw,
  Radio,
  ArrowUpDown,
  ChevronDown,
  ChevronUp,
  Shield,
  Layers,
  Sparkles,
  AlertTriangle,
  Network,
  Check,
  ChevronRight,
  Share2
} from 'lucide-react';

interface AndroidDashboardProps {
  networks: WifiNetwork[];
  isScanning: boolean;
  cooldownSeconds: number;
  onScan: () => void;
  selectedBssid: string;
  onSelectNetwork: (bssid: string) => void;
  onNavigateToAnalysis: () => void;
  isDark: boolean;
}

export const AndroidDashboard: React.FC<AndroidDashboardProps> = ({
  networks,
  isScanning,
  cooldownSeconds,
  onScan,
  selectedBssid,
  onSelectNetwork,
  onNavigateToAnalysis,
  isDark
}) => {
  const [search, setSearch] = useState('');
  const [selectedBand, setSelectedBand] = useState<WifiBand | 'All'>('All');
  const [sortBy, setSortBy] = useState<'rssi' | 'ssid' | 'nodes'>('rssi');
  const [showSortMenu, setShowSortMenu] = useState(false);
  const [expandedSsids, setExpandedSsids] = useState<Set<string>>(
    new Set(['Keenetic-Ultra-Mesh', 'Home_ASUS_WiFi'])
  );

  const toggleGroup = (ssid: string) => {
    setExpandedSsids((prev) => {
      const next = new Set(prev);
      if (next.has(ssid)) next.delete(ssid);
      else next.add(ssid);
      return next;
    });
  };

  const groups = buildNetworkGroups(networks, expandedSsids);

  const filteredGroups = groups
    .filter((g) => (selectedBand === 'All' ? true : g.nodes.some((n) => n.band === selectedBand)))
    .filter((g) => {
      if (search === '') return true;
      const q = search.toLowerCase();
      return (
        g.ssid.toLowerCase().includes(q) ||
        g.primaryVendor.toLowerCase().includes(q) ||
        g.nodes.some((n) => n.bssid.toLowerCase().includes(q) || n.vendor.toLowerCase().includes(q))
      );
    })
    .sort((a, b) => {
      if (sortBy === 'rssi') return b.bestRssi - a.bestRssi;
      if (sortBy === 'ssid') return a.ssid.localeCompare(b.ssid);
      return b.totalNodesCount - a.totalNodesCount;
    });

  const getRssiColor = (rssi: number) => {
    if (rssi >= -55) return 'text-emerald-400 border-emerald-500/40 bg-emerald-500/10';
    if (rssi >= -67) return 'text-sky-400 border-sky-500/40 bg-sky-500/10';
    if (rssi >= -78) return 'text-amber-400 border-amber-500/40 bg-amber-500/10';
    return 'text-rose-400 border-rose-500/40 bg-rose-500/10';
  };

  const getRssiBarColor = (rssi: number) => {
    if (rssi >= -55) return 'bg-emerald-400';
    if (rssi >= -67) return 'bg-sky-400';
    if (rssi >= -78) return 'bg-amber-400';
    return 'bg-rose-400';
  };

  const getTopologyBadge = (type: WifiNetworkGroup['topologyType'], nodeCount: number) => {
    switch (type) {
      case 'MESH_NETWORK':
        return {
          label: `Mesh-сеть (${nodeCount} узла)`,
          color: 'text-emerald-400 bg-emerald-500/15 border-emerald-500/30',
          icon: Network
        };
      case 'REPEATER_DETECTED':
        return {
          label: `Обнаружен репитер (${nodeCount} AP)`,
          color: 'text-amber-400 bg-amber-500/15 border-amber-500/30',
          icon: AlertTriangle
        };
      case 'ENTERPRISE_ROAMING':
        return {
          label: `Enterprise роуминг (${nodeCount} AP)`,
          color: 'text-purple-400 bg-purple-500/15 border-purple-500/30',
          icon: Share2
        };
      default:
        return {
          label: 'Одиночная точка',
          color: 'text-slate-400 bg-slate-800 border-slate-700',
          icon: Radio
        };
    }
  };

  const getNodeRoleBadge = (role: BssIdNode['nodeRole']) => {
    switch (role) {
      case 'MASTER_ROUTER':
        return { label: 'Основной роутер', color: 'text-cyan-400 bg-cyan-500/15 border-cyan-500/30' };
      case 'MESH_NODE':
        return { label: 'Mesh-узел', color: 'text-emerald-400 bg-emerald-500/15 border-emerald-500/30' };
      case 'REPEATER_SUSPECT':
        return { label: 'Репитер / Экстендер', color: 'text-amber-400 bg-amber-500/15 border-amber-500/30' };
      default:
        return { label: 'Точка доступа', color: 'text-slate-400 bg-slate-800 border-slate-700' };
    }
  };

  return (
    <div className="flex flex-col h-full select-none">
      {/* Top Header */}
      <div
        className={`p-4 border-b ${
          isDark ? 'border-slate-800 bg-slate-900/90' : 'border-slate-200 bg-white/90'
        } backdrop-blur-md sticky top-0 z-10`}
      >
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-bold tracking-tight">WiFi Pulse</h1>
            <p className="text-xs text-slate-400">
              Сетей: {filteredGroups.length} • BSSID узлов: {networks.length}
            </p>
          </div>
          <div className="relative">
            <button
              onClick={() => setShowSortMenu(!showSortMenu)}
              className={`p-2 rounded-xl transition ${
                isDark ? 'hover:bg-slate-800 text-slate-300' : 'hover:bg-slate-100 text-slate-700'
              }`}
              title="Сортировка"
            >
              <ArrowUpDown className="w-5 h-5" />
            </button>
            {showSortMenu && (
              <div
                className={`absolute right-0 mt-2 w-52 rounded-xl shadow-2xl border py-1 z-30 ${
                  isDark
                    ? 'bg-slate-900 border-slate-700 text-slate-200'
                    : 'bg-white border-slate-200 text-slate-800'
                }`}
              >
                {[
                  { id: 'rssi', label: 'По уровню сигнала' },
                  { id: 'nodes', label: 'По числу узлов (Mesh)' },
                  { id: 'ssid', label: 'По имени (A-Z)' }
                ].map((opt) => (
                  <button
                    key={opt.id}
                    onClick={() => {
                      setSortBy(opt.id as any);
                      setShowSortMenu(false);
                    }}
                    className={`w-full px-3 py-2 text-left text-xs flex items-center justify-between ${
                      isDark ? 'hover:bg-slate-800' : 'hover:bg-slate-100'
                    }`}
                  >
                    <span>{opt.label}</span>
                    {sortBy === opt.id && <Check className="w-4 h-4 text-cyan-400" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Search */}
        <div className="mt-3 relative">
          <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Поиск по SSID, BSSID, вендору (Keenetic, Deco)..."
            className={`w-full pl-9 pr-3 py-1.5 text-xs rounded-xl border focus:outline-none focus:ring-1 focus:ring-cyan-500 transition ${
              isDark
                ? 'bg-slate-800/80 border-slate-700 text-slate-200 placeholder-slate-500'
                : 'bg-slate-100 border-slate-300 text-slate-900 placeholder-slate-400'
            }`}
          />
        </div>

        {/* Bands Filter */}
        <div className="flex gap-1.5 mt-2.5 overflow-x-auto no-scrollbar">
          {(['All', '2.4 GHz', '5 GHz', '6 GHz'] as const).map((band) => (
            <button
              key={band}
              onClick={() => setSelectedBand(band)}
              className={`px-2.5 py-1 text-xs rounded-lg font-medium whitespace-nowrap transition ${
                selectedBand === band
                  ? 'bg-cyan-500 text-slate-950 font-semibold shadow-sm shadow-cyan-500/20'
                  : isDark
                  ? 'bg-slate-800/90 text-slate-300 hover:bg-slate-700'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              {band === 'All' ? 'Все диапазоны' : band}
            </button>
          ))}
        </div>
      </div>

      {/* Grouped Networks List */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3 pb-24">
        {filteredGroups.map((group) => {
          const isExpanded = expandedSsids.has(group.ssid);
          const topBadge = getTopologyBadge(group.topologyType, group.totalNodesCount);
          const TopIcon = topBadge.icon;
          const bestNode = group.nodes[0];
          const hasRoaming = group.nodes.some(
            (n) => n.roamingSupport.has80211k || n.roamingSupport.has80211r || n.roamingSupport.has80211v
          );

          return (
            <div
              key={group.ssid}
              className={`rounded-2xl border transition-all overflow-hidden ${
                isDark
                  ? 'border-slate-800/80 bg-slate-900/70 shadow-md'
                  : 'border-slate-200 bg-white shadow-sm'
              }`}
            >
              {/* Parent SSID Header Card */}
              <div
                onClick={() => toggleGroup(group.ssid)}
                className={`p-3.5 cursor-pointer transition ${
                  isDark ? 'hover:bg-slate-800/40' : 'hover:bg-slate-50'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <Radio
                        className="w-4 h-4 flex-shrink-0"
                        style={{ color: bestNode.color }}
                      />
                      <h3 className="font-bold text-sm truncate tracking-tight text-slate-100">
                        {group.ssid}
                      </h3>
                    </div>

                    {/* Vendor and Physical Node Count */}
                    <div className="flex items-center gap-2 mt-1 ml-5 text-[11px] text-slate-400">
                      <span className="font-semibold text-cyan-400">{group.primaryVendor}</span>
                      <span>•</span>
                      <span>
                        {group.totalNodesCount} BSSID (
                        {Array.from(new Set(group.nodes.map((n) => n.band))).join(', ')})
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <span
                      className={`px-2 py-0.5 rounded-lg text-xs font-bold font-mono border ${getRssiColor(
                        group.bestRssi
                      )}`}
                    >
                      {group.bestRssi} dBm
                    </span>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleGroup(group.ssid);
                      }}
                      className="p-1 text-slate-400 hover:text-slate-200 transition"
                    >
                      {isExpanded ? (
                        <ChevronUp className="w-4 h-4" />
                      ) : (
                        <ChevronDown className="w-4 h-4" />
                      )}
                    </button>
                  </div>
                </div>

                {/* Badges on Parent Level */}
                <div className="flex flex-wrap gap-1.5 mt-2.5 items-center">
                  {/* Topology Badge */}
                  <span
                    className={`px-2 py-0.5 rounded-md text-[10px] font-bold border flex items-center gap-1 ${topBadge.color}`}
                  >
                    <TopIcon className="w-3 h-3" />
                    {topBadge.label}
                  </span>

                  {/* Roaming Standards badge */}
                  {hasRoaming && (
                    <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-purple-500/15 text-purple-300 border border-purple-500/30">
                      802.11k/r/v
                    </span>
                  )}

                  <span
                    className={`px-2 py-0.5 rounded-md text-[10px] font-medium flex items-center gap-1 ${
                      isDark ? 'bg-slate-800 text-slate-400' : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    <Shield className="w-2.5 h-2.5" />
                    {group.bestSecurity}
                  </span>

                  <span className="ml-auto text-[11px] text-cyan-400 font-medium flex items-center gap-0.5">
                    {isExpanded ? 'Скрыть узлы' : `${group.totalNodesCount} узла`}
                  </span>
                </div>
              </div>

              {/* Expandable Children: Sub-nodes (BSSIDs) */}
              {isExpanded && (
                <div
                  className={`border-t p-2.5 space-y-2 ${
                    isDark ? 'border-slate-800 bg-slate-950/60' : 'border-slate-100 bg-slate-50'
                  }`}
                >
                  <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-1">
                    Физические точки доступа ({group.nodes.length} BSSID):
                  </div>

                  {group.nodes.map((node) => {
                    const isSelected = node.bssid === selectedBssid;
                    const roleBadge = getNodeRoleBadge(node.nodeRole);
                    const percent = Math.min(100, Math.max(0, (node.rssi + 100) * 2));

                    return (
                      <div
                        key={node.bssid}
                        onClick={() => {
                          onSelectNetwork(node.bssid);
                        }}
                        className={`p-2.5 rounded-xl border transition cursor-pointer ${
                          isSelected
                            ? isDark
                              ? 'border-cyan-500/60 bg-cyan-950/20 ring-1 ring-cyan-500/30'
                              : 'border-cyan-500/60 bg-cyan-50 ring-1 ring-cyan-500/30'
                            : isDark
                            ? 'border-slate-800/80 bg-slate-900/60 hover:bg-slate-800/50'
                            : 'border-slate-200 bg-white hover:bg-slate-100'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span
                              className={`px-1.5 py-0.5 rounded text-[10px] font-bold border ${roleBadge.color}`}
                            >
                              {roleBadge.label}
                            </span>
                            <span className="font-mono text-[11px] text-slate-300 font-semibold">
                              {node.bssid}
                            </span>
                          </div>

                          <span
                            className={`px-1.5 py-0.5 rounded font-mono text-[11px] font-bold ${getRssiColor(
                              node.rssi
                            )}`}
                          >
                            {node.rssi} dBm
                          </span>
                        </div>

                        {/* Node details */}
                        <div className="flex flex-wrap items-center justify-between gap-1.5 mt-2 text-[10px]">
                          <div className="flex items-center gap-1.5 text-slate-400">
                            <span className="font-semibold text-slate-300">{node.vendor}</span>
                            <span>•</span>
                            <span>
                              {node.band} (CH {node.channel}, {node.frequency} МГц)
                            </span>
                            <span>•</span>
                            <span>{node.channelWidth} МГц</span>
                          </div>

                          <div className="flex items-center gap-2">
                            <span className="text-slate-400">~{node.distanceMeters} м</span>
                            {isSelected && (
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onNavigateToAnalysis();
                                }}
                                className="text-cyan-400 hover:text-cyan-300 font-bold flex items-center gap-0.5"
                              >
                                Анализ <ChevronRight className="w-3 h-3" />
                              </button>
                            )}
                          </div>
                        </div>

                        {/* Signal bar */}
                        <div className="w-full bg-slate-700/20 rounded-full h-1 mt-2 overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all duration-300 ${getRssiBarColor(
                              node.rssi
                            )}`}
                            style={{ width: `${percent}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Floating Scan Button */}
      <div className="absolute bottom-5 right-5 z-20">
        <button
          onClick={onScan}
          disabled={cooldownSeconds > 0 || isScanning}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-2xl shadow-xl font-semibold text-xs transition active:scale-95 ${
            cooldownSeconds > 0
              ? 'bg-slate-700 text-slate-400 cursor-not-allowed'
              : 'bg-gradient-to-r from-cyan-500 to-blue-500 text-slate-950 hover:brightness-110 shadow-cyan-500/25'
          }`}
        >
          <RotateCw className={`w-4 h-4 ${isScanning ? 'animate-spin' : ''}`} />
          {cooldownSeconds > 0
            ? `Кулдаун: ${cooldownSeconds}с`
            : isScanning
            ? 'Сканирование...'
            : 'Сканировать'}
        </button>
      </div>
    </div>
  );
};
