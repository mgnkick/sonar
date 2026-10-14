import React, { useState } from 'react';
import { X, Copy, Check, Terminal, ExternalLink, Download, Smartphone, ShieldCheck, GitBranch } from 'lucide-react';

interface GitHubGuideModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const GitHubGuideModal: React.FC<GitHubGuideModalProps> = ({ isOpen, onClose }) => {
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);

  if (!isOpen) return null;

  const copyCommand = (cmd: string, index: number) => {
    navigator.clipboard.writeText(cmd);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex(null), 2000);
  };

  const steps = [
    {
      num: '01',
      title: 'Инициализация Git и первый коммит',
      desc: 'Создайте локальный репозиторий в папке проекта. Все правила исключений .gitignore уже настроены.',
      commands: [
        'git init -b main',
        'git add .',
        'git commit -m "feat: Initial commit with WiFi Pulse Android & GitHub Actions CI/CD"'
      ]
    },
    {
      num: '02',
      title: 'Создание репозитория на GitHub и Push',
      desc: 'Создайте пустой репозиторий на github.com (не добавляйте README или .gitignore, они уже есть), затем привяжите remote и отправьте код:',
      commands: [
        'git remote add origin https://github.com/ВАШ_ЛОГИН/wifi-pulse.git',
        'git push -u origin main'
      ]
    },
    {
      num: '03',
      title: 'Автоматическая сборка в GitHub Actions',
      desc: 'После пуша в ветку main GitHub Actions автоматически запустит воркфлоу сборки: развернет JDK 17, скачает зависимости через Gradle и соберет APK.',
      commands: [
        '# Перейдите в браузере: https://github.com/ВАШ_ЛОГИН/wifi-pulse/actions'
      ]
    },
    {
      num: '04',
      title: 'Скачивание собранного APK из вкладки Actions',
      desc: 'Откройте последний запуск воркфлоу в Actions, прокрутите страницу в самый низ до секции «Artifacts» и нажмите на wifipulse-debug-apk.',
      commands: []
    },
    {
      num: '05',
      title: 'Установка APK на смартфон',
      desc: 'Распакуйте скачанный zip-архив и передайте app-debug.apk на Android-устройство. Разрешите установку из неизвестных источников в настройках безопасности.',
      commands: []
    }
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
      <div className="relative w-full max-w-2xl max-h-[90vh] bg-slate-900 border border-slate-700/80 rounded-3xl shadow-2xl overflow-hidden flex flex-col">
        {/* Modal Header */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between bg-slate-950/60">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/30">
              <GitBranch className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-100">
                Пошаговая инструкция: Публикация и получение APK
              </h3>
              <p className="text-xs text-slate-400">
                От чистого Git-репозитория до готового файла на смартфоне
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-slate-100 hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 text-xs text-slate-300">
          {steps.map((step, idx) => (
            <div
              key={step.num}
              className="p-4 rounded-2xl border border-slate-800 bg-slate-950/50 space-y-2.5"
            >
              <div className="flex items-center gap-2.5">
                <span className="w-7 h-7 rounded-lg bg-cyan-500/20 text-cyan-400 font-mono font-bold flex items-center justify-center text-xs">
                  {step.num}
                </span>
                <h4 className="font-bold text-sm text-slate-100">{step.title}</h4>
              </div>

              <p className="text-slate-400 leading-relaxed">{step.desc}</p>

              {step.commands.length > 0 && (
                <div className="space-y-1.5 pt-1">
                  {step.commands.map((cmd, cIdx) => {
                    const uniqueId = idx * 10 + cIdx;
                    return (
                      <div
                        key={cIdx}
                        className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900 border border-slate-800 font-mono text-[11px]"
                      >
                        <span className="text-cyan-300 truncate pr-2">{cmd}</span>
                        <button
                          onClick={() => copyCommand(cmd, uniqueId)}
                          className="p-1 text-slate-400 hover:text-cyan-400 transition flex-shrink-0"
                          title="Скопировать"
                        >
                          {copiedIndex === uniqueId ? (
                            <Check className="w-3.5 h-3.5 text-emerald-400" />
                          ) : (
                            <Copy className="w-3.5 h-3.5" />
                          )}
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Modal Footer */}
        <div className="p-4 border-t border-slate-800 bg-slate-950/80 flex items-center justify-end gap-3">
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-cyan-500 text-slate-950 font-bold text-xs hover:brightness-110 active:scale-95 transition"
          >
            Понятно, закрыть
          </button>
        </div>
      </div>
    </div>
  );
};
