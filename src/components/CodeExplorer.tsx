import React, { useState } from 'react';
import { ANDROID_PROJECT_FILES, downloadAndroidProjectZip } from '../utils/projectZipGenerator';
import { FileCode, Folder, Copy, Check, Download, ExternalLink, Terminal } from 'lucide-react';

export const CodeExplorer: React.FC = () => {
  const [selectedFilePath, setSelectedFilePath] = useState<string>(ANDROID_PROJECT_FILES[0].path);
  const [copied, setCopied] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);

  const currentFile =
    ANDROID_PROJECT_FILES.find((f) => f.path === selectedFilePath) ||
    ANDROID_PROJECT_FILES[0];

  const handleCopy = () => {
    navigator.clipboard.writeText(currentFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
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
    <div className="flex flex-col h-[740px] rounded-3xl border border-slate-800 bg-slate-900/80 shadow-2xl overflow-hidden backdrop-blur-md">
      {/* Top Action Bar */}
      <div className="p-4 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3 bg-slate-950/60">
        <div className="flex items-center gap-2">
          <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400">
            <FileCode className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-100">Исходный код Android-проекта</h2>
            <p className="text-xs text-slate-400">Kotlin, Gradle DSL, Jetpack Compose, CI/CD</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleCopy}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-700 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copied ? 'Скопировано!' : 'Копировать файл'}</span>
          </button>

          <button
            onClick={handleDownload}
            disabled={isDownloading}
            className="flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-500 text-slate-950 text-xs font-bold shadow-md shadow-cyan-500/20 hover:brightness-110 active:scale-95 transition"
          >
            <Download className={`w-3.5 h-3.5 ${isDownloading ? 'animate-bounce' : ''}`} />
            <span>{isDownloading ? 'Создание ZIP...' : 'Скачать проект (.ZIP)'}</span>
          </button>
        </div>
      </div>

      {/* Main Split Body */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Sidebar: File Tree */}
        <div className="w-64 border-r border-slate-800/80 bg-slate-950/40 p-2 overflow-y-auto space-y-1">
          <div className="px-3 py-1.5 text-[11px] font-bold text-slate-400 uppercase tracking-wider">
            Файлы репозитория
          </div>
          {ANDROID_PROJECT_FILES.map((file) => {
            const isSelected = file.path === selectedFilePath;
            return (
              <button
                key={file.path}
                onClick={() => setSelectedFilePath(file.path)}
                className={`w-full px-3 py-2 rounded-xl text-left text-xs font-mono flex items-center gap-2 transition ${
                  isSelected
                    ? 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/30 font-semibold'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                }`}
              >
                <FileCode className="w-3.5 h-3.5 flex-shrink-0" />
                <span className="truncate">{file.path}</span>
              </button>
            );
          })}
        </div>

        {/* Right Editor View */}
        <div className="flex-1 flex flex-col bg-slate-950/70 overflow-hidden">
          {/* File Header */}
          <div className="px-4 py-2 border-b border-slate-800 flex items-center justify-between text-xs text-slate-400 font-mono">
            <span>{currentFile.path}</span>
            <span className="uppercase text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-300">
              {currentFile.language}
            </span>
          </div>

          {/* Code Content */}
          <div className="flex-1 overflow-auto p-4 font-mono text-xs text-slate-300 leading-relaxed select-text">
            <pre className="whitespace-pre">
              <code>{currentFile.content}</code>
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
};
