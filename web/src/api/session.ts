import http from './http'

/** 会话状态：completed 完成 / aborted 中断 */
export type SessionStatus = 'completed' | 'aborted'
export type SessionMode = 'countup' | 'countdown'

export type SessionSource = 'timer' | 'manual'

/** POST /api/sessions 请求体，时间使用 ISO Instant。 */
export interface SessionCreatePayload {
  clientId: string
  taskTitle: string
  taskId: number | null
  habitId: number | null
  note: string
  mode: SessionMode
  plannedSec: number | null
  startedAt: string
  endedAt: string
  durationSec: number
  pausedSec: number
  status: SessionStatus
  source: SessionSource
}

export interface SessionVO extends SessionCreatePayload {
  id: number
  sessionDate: string
  version: number
}

export interface SessionUpdatePayload {
  taskId: number | null
  habitId: number | null
  note: string
  startedAt: string
  endedAt: string
  durationSec: number
  pausedSec: number
  version: number
}

export interface FocusTask {
  id: number
  title: string
  listId: number | null
  note: string
  completed: boolean
  version: number
}

export interface FocusHabit {
  id: number
  name: string
  color: string
  archived: boolean
}

export interface FocusPreset {
  id: number
  name: string
  mode: SessionMode
  plannedSec: number | null
  taskId: number | null
  habitId: number | null
  noteTemplate: string
  sortOrder: number
}

export type FocusPresetPayload = Omit<FocusPreset, 'id'>

export interface SummaryVO {
  totalSec: number
  pomodoroCount: number
  abortedCount: number
  activeDays: number
}

export interface DailyVO {
  date: string
  totalSec: number
  pomodoroCount: number
}

export type YearlyVO = DailyVO

/** 按任务聚合统计 */
export interface TaskStatVO {
  taskTitle: string
  totalSec: number
  pomodoroCount: number
  sessionCount: number
}

/** 创建会话（幂等，重复 clientId 后端返回已有记录） */
export function createSession(payload: SessionCreatePayload): Promise<SessionVO> {
  return http.post('/sessions', payload)
}

/** 记录列表，按 started_at 倒序，最多 100 条 */
export function listSessions(params?: { from?: string; to?: string; limit?: number }): Promise<SessionVO[]> {
  return http.get('/sessions', { params })
}

export function getSession(id: number): Promise<SessionVO> {
  return http.get(`/sessions/${id}`)
}

export function updateSession(id: number, payload: SessionUpdatePayload): Promise<SessionVO> {
  return http.patch(`/sessions/${id}`, payload)
}

export function listTasks(): Promise<FocusTask[]> {
  return http.get('/tasks')
}

export function listHabits(): Promise<FocusHabit[]> {
  return http.get('/habits')
}

export function listPresets(): Promise<FocusPreset[]> {
  return http.get('/presets')
}

export function createPreset(payload: FocusPresetPayload): Promise<FocusPreset> {
  return http.post('/presets', payload)
}

export function updatePreset(id: number, payload: FocusPresetPayload): Promise<FocusPreset> {
  return http.put(`/presets/${id}`, payload)
}

export function deletePreset(id: number): Promise<null> {
  return http.delete(`/presets/${id}`)
}

/** 删除记录（软删除） */
export function deleteSession(id: number): Promise<null> {
  return http.delete(`/sessions/${id}`)
}

/** 汇总统计；不传时间范围时统计全部数据 */
export function getSummary(from?: string, to?: string): Promise<SummaryVO> {
  return http.get('/stats/summary', { params: { from, to } })
}

/** 每日趋势（后端补零保证区间连续） */
export function getDaily(from: string, to: string): Promise<DailyVO[]> {
  return http.get('/stats/daily', { params: { from, to } })
}

/** 按任务聚合，按时长倒序（后端最多返回 20 条） */
export function getByTask(from: string, to: string, limit = 20): Promise<TaskStatVO[]> {
  return http.get('/stats/by-task', { params: { from, to, limit } })
}

/** 年度趋势，固定返回 12 个月。 */
export function getYearly(year: number): Promise<YearlyVO[]> {
  return http.get('/stats/yearly', { params: { year } })
}
