class AudioBeeperService {
  private audioCtx: AudioContext | null = null;
  private isRunning: boolean = false;
  private timerId: any = null;
  private currentRssi: number = -70;

  start() {
    if (this.isRunning) return;
    try {
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtxClass) return;
      this.audioCtx = new AudioCtxClass();
      this.isRunning = true;
      this.scheduleNextBeep();
    } catch (e) {
      console.warn('AudioContext failed to start', e);
    }
  }

  stop() {
    this.isRunning = false;
    if (this.timerId) {
      clearTimeout(this.timerId);
      this.timerId = null;
    }
    if (this.audioCtx && this.audioCtx.state !== 'closed') {
      try {
        this.audioCtx.close();
      } catch (e) {}
      this.audioCtx = null;
    }
  }

  updateRssi(rssi: number) {
    this.currentRssi = rssi;
  }

  get active(): boolean {
    return this.isRunning;
  }

  private scheduleNextBeep() {
    if (!this.isRunning || !this.audioCtx) return;

    // Map -95 dBm (poor) to -40 dBm (excellent)
    const normalized = Math.max(0, Math.min(1, (this.currentRssi + 95) / 55));
    // Interval between 1200ms (slow beeps) down to 100ms (rapid beeps)
    const interval = Math.max(120, 1200 - normalized * 1050);
    // Pitch frequency between 440Hz and 1760Hz
    const pitch = 440 + normalized * 1200;

    this.playTone(pitch, 0.05);

    this.timerId = setTimeout(() => {
      this.scheduleNextBeep();
    }, interval);
  }

  private playTone(freq: number, durationSeconds: number) {
    if (!this.audioCtx) return;
    try {
      if (this.audioCtx.state === 'suspended') {
        this.audioCtx.resume();
      }
      const osc = this.audioCtx.createOscillator();
      const gain = this.audioCtx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, this.audioCtx.currentTime);

      gain.gain.setValueAtTime(0.08, this.audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, this.audioCtx.currentTime + durationSeconds);

      osc.connect(gain);
      gain.connect(this.audioCtx.destination);

      osc.start();
      osc.stop(this.audioCtx.currentTime + durationSeconds);
    } catch (e) {}
  }
}

export const audioBeeper = new AudioBeeperService();
