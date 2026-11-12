let ctx: AudioContext | null = null

/** 倒计时归零提示音：Web Audio 合成两声短音（文档第 8 节，约 15 行） */
export function playChime(): void {
  try {
    ctx = ctx ?? new AudioContext()
    const t0 = ctx.currentTime
    const freqs = [880, 1318]
    freqs.forEach((freq, i) => {
      const osc = ctx!.createOscillator()
      const gain = ctx!.createGain()
      const start = t0 + i * 0.18
      osc.type = 'sine'
      osc.frequency.value = freq
      gain.gain.setValueAtTime(0.0001, start)
      gain.gain.exponentialRampToValueAtTime(0.25, start + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.22)
      osc.connect(gain)
      gain.connect(ctx!.destination)
      osc.start(start)
      osc.stop(start + 0.24)
    })
  } catch {
    // 浏览器不支持或被策略拦截时静默忽略
  }
}
