import { defineStore } from 'pinia'
import dayjs from 'dayjs'
import { createSession, type SessionCreatePayload, type SessionStatus } from '../api/session'
import { playChime } from '../utils/sound'
import { useAuthStore } from './auth'

export type Phase = 'idle' | 'running' | 'paused'
export type Mode = 'countup' | 'countdown'

/** 恢复结果类型（R9）：App.vue 挂载时 restore，专注页据此弹窗 */
export type RestoreKind =
  | 'none' // 无未结束计时
  | 'save-failed' // 有未保存成功的会话（含刷新前保存失败），弹「重试 / 放弃」
  | 'expired-saved' // 倒计时已到期，自动补记完成
  | 'restored' // 未结束，刷新后接着计时（手动暂停过的保持暂停）

export interface SettleInfo {
  durationSec: number
  status: SessionStatus
  taskTitle: string
  note: string
}

interface PersistData {
  v: 1 | 2 | 3
  userId?: number
  phase?: Phase
  mode: Mode
  presetSec: number
  startedAt: number
  pausedAt: number | null
  pausedTotalMs: number
  taskTitle: string
  taskId?: number | null
  habitId?: number | null
  note: string
  clientId: string
  pending?: SessionCreatePayload
}

const LEGACY_STORAGE_KEY = 'focus_active_timer_v1'
const V2_STORAGE_PREFIX = 'focus_active_timer_v2_'
const STORAGE_PREFIX = 'focus_active_timer_v3_'

function currentStorageKey(userId?: number) {
  return `${STORAGE_PREFIX}${userId ?? useAuthStore().user?.id ?? 'anonymous'}`
}

function v2StorageKey(userId: number) {
  return `${V2_STORAGE_PREFIX}${userId}`
}

function toIsoInstant(value: string): string {
  return (value.includes('T') ? new Date(value) : dayjs(value).toDate()).toISOString()
}

function normalizePending(payload: SessionCreatePayload): SessionCreatePayload {
  return {
    ...payload,
    taskTitle: payload.taskTitle || '',
    taskId: payload.taskId ?? null,
    habitId: payload.habitId ?? null,
    note: payload.note || '',
    startedAt: toIsoInstant(payload.startedAt),
    endedAt: toIsoInstant(payload.endedAt),
    source: payload.source ?? 'timer',
  }
}

export const useTimerStore = defineStore('timer', {
  state: () => ({
    // 核心计时状态（文档第 8 节）
    phase: 'idle' as Phase,
    mode: 'countdown' as Mode,
    presetSec: 1500, // 默认 25 分钟
    startedAt: 0,
    pausedAt: null as number | null,
    pausedTotalMs: 0,
    taskTitle: '',
    taskId: null as number | null,
    habitId: null as number | null,
    note: '',
    clientId: '',
    // 内部状态（不持久化）
    nowMs: Date.now(), // setInterval(250) 只负责更新它触发重绘
    finishing: false, // 归零自动结束防重入
    saving: false, // 保存请求进行中
    _interval: undefined as number | undefined, // 重绘定时器句柄
    pendingPayload: null as SessionCreatePayload | null, // 保存失败待重试
    lastSettle: null as SettleInfo | null, // 结算弹窗数据
    restoreResult: 'none' as RestoreKind,
  }),

  getters: {
    /** R4：时长 = 当前时刻 − 开始 − 累计暂停（只用时间戳，不用累加） */
    elapsedMs(state): number {
      if (state.phase === 'idle') return 0
      const end = state.pausedAt ?? state.nowMs
      return Math.max(0, end - state.startedAt - state.pausedTotalMs)
    },
    /** 倒计时剩余毫秒 */
    remainingMs(): number {
      if (this.mode !== 'countdown') return 0
      return Math.max(0, this.presetSec * 1000 - this.elapsedMs)
    },
    /** 界面显示的毫秒：倒计时显示剩余，正计时显示已过 */
    displayMs(state): number {
      if (state.phase === 'idle') {
        return state.mode === 'countdown' ? state.presetSec * 1000 : 0
      }
      return state.mode === 'countdown' ? this.remainingMs : this.elapsedMs
    },
    isRunning(state): boolean {
      return state.phase === 'running'
    },
  },

  actions: {
    // ---------- 生命周期 ----------

    /** R7：每次开始生成 clientId */
    start() {
      if (this.phase !== 'idle' || this.saving || this.finishing) return
      this.clientId = crypto.randomUUID()
      this.startedAt = Date.now()
      this.pausedAt = null
      this.pausedTotalMs = 0
      this.nowMs = this.startedAt
      this.phase = 'running'
      this.persist()
      this.startTick()
    },

    /** R5：暂停期间不计时 */
    pause() {
      if (this.phase !== 'running') return
      this.pausedAt = Date.now()
      this.phase = 'paused'
      this.persist()
    },

    resume() {
      if (this.phase !== 'paused') return
      this.pausedTotalMs += Date.now() - (this.pausedAt ?? Date.now())
      this.pausedAt = null
      this.nowMs = Date.now()
      this.phase = 'running'
      this.persist()
      this.startTick()
    },

    /** 手动结束：status 由 UI 决定（countup 完成 / countdown 提前=中断） */
    async finish(status: SessionStatus): Promise<{ ok: boolean; skipped?: boolean }> {
      if (this.phase === 'idle' || this.finishing) return { ok: false }
      this.finishing = true
      this.stopTick()
      // R4：结束 − 开始 − 累计暂停，向下取整到秒；暂停中结束则以暂停点为结束时刻
      const endMs = this.pausedAt ?? Date.now()
      const durationSec = Math.floor((endMs - this.startedAt - this.pausedTotalMs) / 1000)
      if (durationSec < 1) {
        this.clearPersist()
        this.resetKeepInput()
        this.finishing = false
        return { ok: true, skipped: true }
      }
      const payload = this.buildPayload(endMs, durationSec, status)
      this.phase = 'idle'
      const result = await this.submit(payload)
      this.finishing = false
      return result
    },

    /** 重置计时字段，保留任务/事项/模式/预设，便于「再来一次」 */
    resetKeepInput() {
      this.phase = 'idle'
      this.startedAt = 0
      this.pausedAt = null
      this.pausedTotalMs = 0
      this.clientId = ''
      this.nowMs = Date.now()
      this.stopTick()
    },

    clearSettle() {
      this.lastSettle = null
    },

    // ---------- 保存 ----------

    buildPayload(endMs: number, durationSec: number, status: SessionStatus): SessionCreatePayload {
      return {
        clientId: this.clientId,
        taskTitle: this.taskTitle.trim().slice(0, 100),
        taskId: this.taskId,
        habitId: this.habitId,
        note: this.note.trim().slice(0, 2000),
        mode: this.mode,
        plannedSec: this.mode === 'countdown' ? this.presetSec : null,
        startedAt: new Date(this.startedAt).toISOString(),
        endedAt: new Date(endMs).toISOString(),
        durationSec,
        pausedSec: Math.round(this.pausedTotalMs / 1000),
        status,
        source: 'timer',
      }
    },

    /** 提交保存；成功弹结算并清存储，失败保留数据（内存 + localStorage）供重试 */
    async submit(payload: SessionCreatePayload): Promise<{ ok: boolean }> {
      this.saving = true
      try {
        await createSession(payload)
        this.lastSettle = {
          durationSec: payload.durationSec,
          status: payload.status,
          taskTitle: payload.taskTitle,
          note: payload.note,
        }
        this.pendingPayload = null
        this.clearPersist()
        this.resetKeepInput()
        return { ok: true }
      } catch {
        this.pendingPayload = payload
        this.persistPending(payload)
        return { ok: false }
      } finally {
        this.saving = false
      }
    },

    async retrySave(): Promise<{ ok: boolean }> {
      if (!this.pendingPayload) return { ok: false }
      return await this.submit(this.pendingPayload)
    },

    discardPendingSave() {
      this.pendingPayload = null
      this.clearPersist()
      this.resetKeepInput()
    },

    /** 退出只清内存；各账号快照分别保留，重新登录后仍可恢复。 */
    resetForLogout() {
      this.stopTick()
      this.phase = 'idle'
      this.startedAt = 0
      this.pausedAt = null
      this.pausedTotalMs = 0
      this.clientId = ''
      this.taskTitle = ''
      this.taskId = null
      this.habitId = null
      this.note = ''
      this.pendingPayload = null
      this.lastSettle = null
      this.restoreResult = 'none'
      this.nowMs = Date.now()
    },

    // ---------- 刷新恢复（R9） ----------

    async restore(): Promise<RestoreKind> {
      let raw: string | null = null
      const userId = useAuthStore().user?.id
      if (!userId) return 'none'
      const newKey = currentStorageKey(userId)
      let storedKey = newKey
      try {
        raw = localStorage.getItem(newKey)
        if (!raw) {
          storedKey = v2StorageKey(userId)
          raw = localStorage.getItem(storedKey)
        }
        if (!raw) {
          storedKey = LEGACY_STORAGE_KEY
          raw = localStorage.getItem(storedKey)
        }
      } catch {
        return 'none'
      }
      if (!raw) return 'none'
      let data: PersistData
      try {
        data = JSON.parse(raw)
      } catch {
        try { localStorage.removeItem(storedKey) } catch { /* 忽略 */ }
        this.clearPersist()
        return 'none'
      }
      if (!data || ![1, 2, 3].includes(data.v) || (!data.startedAt && !data.pending)) {
        try { localStorage.removeItem(storedKey) } catch { /* 忽略 */ }
        this.clearPersist()
        return 'none'
      }

      if (data.v >= 2 && data.userId !== userId) return 'none'

      // 上次保存失败未处理：直接进入「重试 / 放弃」
      if (data.pending) {
        this.pendingPayload = normalizePending(data.pending)
        this.persistPending(this.pendingPayload)
        if (storedKey !== newKey) {
          try { localStorage.removeItem(storedKey) } catch { /* 忽略 */ }
        }
        this.restoreResult = 'save-failed'
        return 'save-failed'
      }

      this.mode = data.mode
      this.presetSec = data.presetSec
      this.startedAt = data.startedAt
      this.pausedTotalMs = data.pausedTotalMs
      this.taskTitle = data.taskTitle || ''
      this.taskId = data.taskId ?? null
      this.habitId = this.taskId == null ? (data.habitId ?? null) : null
      this.note = data.note || ''
      this.clientId = data.clientId
      const refMs = Date.now()
      this.nowMs = refMs

      // 只有手动暂停过的，才把离开页面这段空白补进累计暂停，保持暂停时的读数
      if (data.pausedAt != null) {
        this.pausedTotalMs += Math.max(0, refMs - data.pausedAt)
      }

      // R9：倒计时已到期 → 自动按完成补记（R6 公式，避免依赖 tick 误差）
      if (data.mode === 'countdown' && refMs - data.startedAt - data.pausedTotalMs >= data.presetSec * 1000) {
        const endedMs = data.startedAt + data.presetSec * 1000 + Math.round(data.pausedTotalMs / 1000) * 1000
        const payload: SessionCreatePayload = {
          clientId: data.clientId,
          taskTitle: this.taskTitle.trim().slice(0, 100),
          taskId: this.taskId,
          habitId: this.habitId,
          note: this.note.trim().slice(0, 2000),
          mode: 'countdown',
          plannedSec: data.presetSec,
          startedAt: new Date(data.startedAt).toISOString(),
          endedAt: new Date(endedMs).toISOString(),
          durationSec: data.presetSec,
          pausedSec: Math.round(data.pausedTotalMs / 1000),
          status: 'completed',
          source: 'timer',
        }
        if (storedKey !== newKey) {
          try { localStorage.removeItem(storedKey) } catch { /* 忽略 */ }
        }
        const r = await this.submit(payload)
        this.restoreResult = r.ok ? 'expired-saved' : 'save-failed'
        return this.restoreResult
      }

      // 刷新不停表：手动按过暂停的保持暂停，其余接着计时
      if (data.phase === 'paused') {
        this.phase = 'paused'
        this.pausedAt = refMs
      } else {
        this.phase = 'running'
        this.pausedAt = null
        this.startTick()
      }
      this.persist()
      if (storedKey !== newKey) {
        try { localStorage.removeItem(storedKey) } catch { /* 忽略 */ }
      }
      this.restoreResult = 'restored'
      return 'restored'
    },

    // ---------- tick：只触发重绘 + 归零检测 ----------

    startTick() {
      if (this._interval) return
      this._interval = window.setInterval(() => this.tick(), 250)
    },

    stopTick() {
      if (this._interval) {
        window.clearInterval(this._interval)
        this._interval = undefined
      }
    },

    tick() {
      if (this.phase !== 'running') return
      this.nowMs = Date.now()
      // R2：倒计时归零自动结束
      if (this.mode === 'countdown' && this.remainingMs <= 0) {
        void this.autoFinishExpired()
      }
    },

    /** R2/R6：归零自动完成，时长恒等于预设秒数，endedAt 反推避免 tick 误差 */
    async autoFinishExpired() {
      if (this.finishing) return
      this.finishing = true
      this.stopTick()
      playChime()
      const pausedSec = Math.round(this.pausedTotalMs / 1000)
      const endedMs = this.startedAt + this.presetSec * 1000 + pausedSec * 1000
      const payload = this.buildPayload(endedMs, this.presetSec, 'completed')
      this.phase = 'idle'
      await this.submit(payload)
      this.finishing = false
    },

    // ---------- localStorage ----------

    /** R8：只存计时必要字段 */
    persist() {
      const userId = useAuthStore().user?.id
      if (!userId) return
      const data: PersistData = {
        v: 3,
        userId,
        phase: this.phase,
        mode: this.mode,
        presetSec: this.presetSec,
        startedAt: this.startedAt,
        pausedAt: this.pausedAt,
        pausedTotalMs: this.pausedTotalMs,
        taskTitle: this.taskTitle,
        taskId: this.taskId,
        habitId: this.habitId,
        note: this.note,
        clientId: this.clientId,
      }
      try {
        localStorage.setItem(currentStorageKey(userId), JSON.stringify(data))
      } catch {
        // 存储不可用时忽略，仅影响刷新恢复
      }
    },

    persistPending(payload: SessionCreatePayload) {
      const userId = useAuthStore().user?.id
      if (!userId) return
      try {
        localStorage.setItem(currentStorageKey(userId), JSON.stringify({ v: 3, userId, pending: payload }))
      } catch {
        // 忽略
      }
    },

    clearPersist() {
      const userId = useAuthStore().user?.id
      try {
        localStorage.removeItem(currentStorageKey())
        if (userId) localStorage.removeItem(v2StorageKey(userId))
      } catch {
        // 忽略
      }
    },
  },
})
