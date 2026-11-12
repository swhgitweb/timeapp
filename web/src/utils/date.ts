import dayjs from 'dayjs'

/** 毫秒时间戳 → yyyy-MM-dd HH:mm:ss（文档统一时间格式） */
export function fmtDateTime(ms: number): string {
  return dayjs(ms).format('YYYY-MM-DD HH:mm:ss')
}

/** 今天 yyyy-MM-dd */
export function todayStr(): string {
  return dayjs().format('YYYY-MM-DD')
}

/** 本周一 yyyy-MM-dd（周一起始，对应柱状图 一~日） */
export function weekStartStr(): string {
  const d = dayjs()
  const daysFromMonday = (d.day() + 6) % 7
  return d.subtract(daysFromMonday, 'day').format('YYYY-MM-DD')
}

/** 7 天前 yyyy-MM-dd（含今天共 7 天） */
export function sevenDaysAgoStr(): string {
  return dayjs().subtract(6, 'day').format('YYYY-MM-DD')
}

/** yyyy-MM-dd → "11月11日" */
export function fmtDateCN(dateStr: string): string {
  return dayjs(dateStr).format('M月D日')
}

/** 秒 → "1h20m" / "12m" / "45s" */
export function fmtDuration(sec: number): string {
  if (sec >= 3600) {
    const h = Math.floor(sec / 3600)
    const m = Math.round((sec % 3600) / 60)
    return m > 0 ? `${h}h${m}m` : `${h}h`
  }
  if (sec >= 60) {
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return s > 0 ? `${m}m${s}s` : `${m}m`
  }
  return `${sec}s`
}

/** 毫秒 → "25:00" / "1:05:30"（计时显示） */
export function fmtClock(ms: number): string {
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}
