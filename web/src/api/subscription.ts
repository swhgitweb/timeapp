import http from './http'

export interface CalendarSubscription {
  id: number
  url: string
  name: string
  color: string
  enabled: boolean
  lastRefreshedAt: string | null
  lastError: string | null
}

export interface SubscriptionPayload {
  url: string
  name: string
  color: string
  enabled: boolean
}

export interface RefreshResult {
  ok: boolean
  notModified?: boolean
  eventCount?: number | null
  lastError: string | null
}

export interface ExportStatus {
  enabled: boolean
  url: string | null
}

const slowRequest = { timeout: 30000 }

export function listSubscriptions() {
  return http.get<CalendarSubscription[], CalendarSubscription[]>('/calendar/subscriptions')
}

export function createSubscription(data: SubscriptionPayload) {
  return http.post<CalendarSubscription, CalendarSubscription>('/calendar/subscriptions', data, slowRequest)
}

export function updateSubscription(id: number, data: SubscriptionPayload) {
  return http.put<CalendarSubscription, CalendarSubscription>(`/calendar/subscriptions/${id}`, data, slowRequest)
}

export function deleteSubscription(id: number) {
  return http.delete<void, void>(`/calendar/subscriptions/${id}`)
}

export function refreshSubscription(id: number) {
  return http.post<RefreshResult, RefreshResult>(`/calendar/subscriptions/${id}/refresh`, undefined, slowRequest)
}

export function getCalendarExport() {
  return http.get<ExportStatus, ExportStatus>('/calendar/export')
}

export function enableCalendarExport() {
  return http.post<ExportStatus, ExportStatus>('/calendar/export')
}

export function revokeCalendarExport() {
  return http.delete<void, void>('/calendar/export')
}
