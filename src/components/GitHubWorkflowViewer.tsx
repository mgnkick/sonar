import React, { useState } from 'react';
import { Play, CheckCircle2, Clock, Terminal, Package, GitBranch, ArrowRight, ShieldCheck, Download } from 'lucide-react';
import { downloadAndroidProjectZip } from '../utils/projectZipGenerator';

export const GitHubWorkflowViewer: React.FC = () => {
  const [isSimulating, setIsSimulating] = useState(false);
  const [currentStepIndex, setCurrentStepIndex] = useState<number>(4); // Completed by default
  const [logs, setLogs] = useState<string[]>([
    'Run actions/checkout@v4',
    'Syncing repository com.wifipulse.analyzer...',
    'Run actions/setup-java@v4 (JDK 17 Eclipse Temurin)',
    'Java home: /opt/hostedtoolcache/Java_Temurin-jdk/17.0.10-7/x64',
    'Run gradle/actions/setup-gradle@v3',
    'Gradle cache hit: 84% speedup',
    'Run ./gradlew assembleDebug --stacktrace',
    '> Task :app:compileDebugKotlin UP-TO-DATE',
    '> Task :app:mergeDebugResources',
    '> Task :app:processDebugManifest',
    '> Task :app:packageDebug',
    'BUILD SUCCESSFUL in 18s',
    'Run actions/upload-artifact@v4',
    'Artifact "wifipulse-debug-apk" uploaded successfully! (14.2 MB)'
  ]);

  const steps = [
    { name: 'actions/checkout@v4', desc: 'Клонирование репозитория' },
    { name: 'setup-java@v4 (JDK 17)', desc: 'Установка Eclipse Temurin 17' },
    { name: 'setup-gradle@v3', desc: 'Кэширование зависимостей Gradle' },
    { name: './gradlew assembleDebug', desc: 'Компиляция Kotlin и сборка APK' },
    { name: 'upload-artifact@v4', desc: 'Публикация wifipulse-debug-apk' }
  ];

  const handleSimulate = () => {
    setIsSimulating(true);
    setCurrentStepIndex(0);
    setLogs(['[Workflow] Инициализация виртуальной машины ubuntu-latest...']);

    const logMessages = [
      'Cloning git repository: branch main, commit 4f9b8c2...',
      'Setting up Java Development Kit 17 (Temurin)...',
      'Configuring Gradle 8.7 daemon and build cache...',
      'Executing: ./gradlew assembleDebug --stacktrace',
      ':app:preBuild UP-TO-DATE\n:app:compileDebugKotlin\n:app:packageDebug\nBUILD SUCCESSFUL in 22s',
      'Uploading artifact app/build/outputs/apk/debug/app-debug.apk as "wifipulse-debug-apk"... Done!'
    ];

    let step = 0;
    const interval = setInterval(() => {
      step++;
      setCurrentStepIndex(step);
      setLogs((prev) => [...prev, logMessages[step] || 'Готово']);
      if (step >= steps.length) {
        clearInterval(interval);
        setIsSimulating(false);
      }
    }, 1200);
  };

  return (
    <div className="flex flex-col h-[740px] rounded-3xl border border-slate-800 bg-slate-900/80 shadow-2xl overflow-hidden backdrop-blur-md">
      {/* Header */}
      <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/60">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-purple-500/10 text-purple-400">
            <GitBranch className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">
              CI/CD Пайплайн (GitHub Actions)
            </h2>
            <p className="text-xs text-slate-400">.github/workflows/build.yml</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleSimulate}
            disabled={isSimulating}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition ${
              isSimulating
                ? 'bg-slate-800 text-slate-400 cursor-wait'
                : 'bg-purple-600 hover:bg-purple-500 text-white shadow-md shadow-purple-600/30 active:scale-95'
            }`}
          >
            <Play className={`w-3.5 h-3.5 ${isSimulating ? 'animate-spin' : ''}`} />
            <span>{isSimulating ? 'Сборка...' : 'Симулировать сборку CI/CD'}</span>
          </button>

          <button
            onClick={downloadAndroidProjectZip}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-700 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition"
          >
            <Download className="w-3.5 h-3.5 text-cyan-400" />
            <span>Скачать проект</span>
          </button>
        </div>
      </div>

      {/* Pipeline Steps Tracker */}
      <div className="p-4 border-b border-slate-800 bg-slate-950/30">
        <div className="grid grid-cols-5 gap-2">
          {steps.map((s, idx) => {
            const isCompleted = currentStepIndex > idx;
            const isCurrent = currentStepIndex === idx;

            return (
              <div
                key={s.name}
                className={`p-3 rounded-2xl border text-left transition ${
                  isCompleted
                    ? 'border-emerald-500/30 bg-emerald-500/5'
                    : isCurrent
                    ? 'border-cyan-500/50 bg-cyan-500/10 shadow-sm'
                    : 'border-slate-800 bg-slate-900/40 text-slate-500'
                }`}
              >
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-[10px] font-mono font-bold text-slate-400">
                    ШАГ 0{idx + 1}
                  </span>
                  {isCompleted ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  ) : isCurrent ? (
                    <Clock className="w-4 h-4 text-cyan-400 animate-spin" />
                  ) : (
                    <span className="w-3.5 h-3.5 rounded-full border border-slate-700" />
                  )}
                </div>
                <h4 className="text-xs font-bold truncate text-slate-200">{s.name}</h4>
                <p className="text-[10px] text-slate-400 truncate mt-0.5">{s.desc}</p>
              </div>
            );
          })}
        </div>
      </div>

      {/* Terminal Output Viewer */}
      <div className="flex-1 flex flex-col bg-black/80 font-mono text-xs overflow-hidden">
        <div className="px-4 py-2 bg-slate-950 border-b border-slate-800 flex items-center justify-between text-slate-400 text-[11px]">
          <div className="flex items-center gap-2">
            <Terminal className="w-3.5 h-3.5 text-cyan-400" />
            <span>build-apk (ubuntu-latest, JDK 17, Gradle 8.7)</span>
          </div>
          <span className="text-emerald-400 font-semibold">Статус: УСПЕШНО (0 errors)</span>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-1 text-slate-300">
          {logs.map((log, i) => (
            <div key={i} className="flex gap-2 leading-relaxed">
              <span className="text-slate-600 select-none">$</span>
              <span
                className={
                  log.includes('SUCCESSFUL') || log.includes('uploaded successfully')
                    ? 'text-emerald-400 font-bold'
                    : log.includes('Task')
                    ? 'text-cyan-400'
                    : 'text-slate-300'
                }
              >
                {log}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Artifact Preview Card */}
      <div className="p-4 border-t border-slate-800 bg-slate-950/80 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 text-cyan-400">
            <Package className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h4 className="text-xs font-bold text-slate-100">
                wifipulse-debug-apk (Готовый артефакт сборки)
              </h4>
              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                14.2 MB • .apk
              </span>
            </div>
            <p className="text-[11px] text-slate-400">
              Где скачать в GitHub: <strong>Репозиторий → Actions → Последний запуск → Секция Artifacts</strong>
            </p>
          </div>
        </div>

        <button
          onClick={downloadAndroidProjectZip}
          className="px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-500 text-slate-950 font-bold text-xs shadow-md shadow-cyan-500/20 hover:brightness-110 active:scale-95 transition"
        >
          Скачать репозиторий
        </button>
      </div>
    </div>
  );
};
