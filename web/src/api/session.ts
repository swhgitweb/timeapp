import http from './http'

/** 会话状态：completed 完成 / aborted 中断 */
export type SessionStatus = 'completed' | 'aborted'
export type SessionMode = 'countup' | 'countdown'

/** POST /api/sessions 请求体（文档 6.2） */
export interface SessionCreatePayload {
  clientId: string
  taskTitle: string
  note: string
  mode: SessionMode
  plannedSec: number | null
  startedAt: string // yyyy-MM-dd HH:mm:ss
  endedAt: string
  durationSec: number
  pausedSec: number
  status: SessionStatus
}

export interface SessionVO extends SessionCreatePayload {
  id: number
  sessionDate: string
}

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

/** 创建会话（幂等，重复 clientId 后端返回已有记录） */
export function createSession(payload: SessionCreatePayload): Promise<SessionVO> {
  return http.post('/sessions', payload)
}

/** 记录列表，按 started_at 倒序，最多 100 条 */
export function listSessions(params?: { from?: string; to?: string; limit?: number }): Promise<SessionVO[]> {
  return http.get('/sessions', { params })
}

/** 任务名候选（历史下拉） */
export function listTaskTitles(limit = 10): Promise<string[]> {
  return http.get('/sessions/task-titles', { params: { limit } })
}

/** 删除记录（软删除） */
export function deleteSession(id: number): Promise<null> {
  return http.delete(`/sessions/${id}`)
}

/** 汇总统计 */
export function getSummary(from: string, to: string): Promise<SummaryVO> {
  return http.get('/stats/summary', { params: { from, to } })
}

/** 每日趋势（后端补零保证连续 7 条） */
export function getDaily(from: string, to: string): Promise<DailyVO[]> {
  return http.get('/stats/daily', { params: { from, to } })
}
