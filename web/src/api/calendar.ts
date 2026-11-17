import http from './http'

export type CalendarView = 'year' | 'month' | 'week' | 'agenda' | 'multi-day' | 'multi-week'
export type CalendarKind = 'task' | 'habit' | 'anniversary' | 'course' | 'ics' | 'focus'
export type EditableCalendarKind = Exclude<CalendarKind, 'ics' | 'focus'>

export interface CalendarEvent {
  id: number
  eventId?: number | null
  occurrenceId: string
  source: 'local' | 'ics' | 'focus'
  kind: CalendarKind
  taskId?: number | null
  habitId?: number | null
  listId?: number | null
  subscriptionId?: number | null
  title: string
  note?: string
  startAt: string
  endAt: string
  allDay: boolean
  timezone?: string
  rrule?: string | null
  color?: string | null
  completed?: boolean
  version: number
  readOnly: boolean
  sessionId?: number
  durationSec?: number
  status?: 'completed' | 'aborted'
  mode?: 'countup' | 'countdown'
  taskTitle?: string
}

export interface CalendarEventPayload {
  kind: EditableCalendarKind
  taskId?: number | null
  habitId?: number | null
  title?: string
  note?: string
  startAt: string
  endAt: string
  allDay: boolean
  timezone: string
  rrule?: string | null
  color?: string
  version?: number
}

export interface CalendarQuery {
  from: string
  to: string
  kinds: CalendarKind[]
  listIds: number[]
  includeFocus: boolean
}

export interface CalendarDaily {
  date: string
  count: number
  focusSec: number
}

export interface CalendarDayInfo {
  date: string
  lunar?: string
  lunarFestivals?: string[]
  holiday?: string | null
  dayType?: 'holiday' | 'workday' | null
  holidaySource?: string | null
}

export interface CalendarList {
  id: number
  name: string
  color: string
  sortOrder: number
  archived: boolean
}

export interface CalendarTask {
  id: number
  listId: number | null
  title: string
  note?: string
  completed: boolean
  version?: number
}

export interface CalendarHabit {
  id: number
  name: string
  color: string
  archived: boolean
}

export interface CalendarSettings {
  defaultView: CalendarView
  weekStart: 0 | 1
  visibleListIds: number[]
  visibleKinds: CalendarKind[]
  showFocus: boolean
  showLunar: boolean
  showHolidays: boolean
  showHabits: boolean
  showAnniversaries: boolean
  showCourses: boolean
  auxiliaryTimezone: string
}

export interface CalendarBatchPayload {
  action: 'complete' | 'delete' | 'move-list' | 'reschedule'
  items: { id: number; version: number }[]
  listId?: number
  date?: string
}

export const CALENDAR_KIND_LABELS: Record<CalendarKind, string> = {
  task: '任务',
  habit: '习惯',
  anniversary: '纪念日',
  course: '课程',
  ics: '订阅',
  focus: '专注',
}

export const COMMON_TIMEZONES = [
  'Asia/Shanghai',
  'Asia/Hong_Kong',
  'Asia/Tokyo',
  'Asia/Singapore',
  'Europe/London',
  'Europe/Paris',
  'America/New_York',
  'America/Los_Angeles',
  'Australia/Sydney',
  'UTC',
]

export function defaultCalendarSettings(): CalendarSettings {
  return {
    defaultView: 'month',
    weekStart: 1,
    visibleListIds: [],
    visibleKinds: ['task', 'habit', 'anniversary', 'course', 'ics'],
    showFocus: true,
    showLunar: true,
    showHolidays: true,
    showHabits: true,
    showAnniversaries: true,
    showCourses: true,
    auxiliaryTimezone: '',
  }
}

export function calendarEventKey(event: CalendarEvent): string {
  return event.occurrenceId || `${event.kind}-${event.id}-${event.startAt}`
}

function queryParams(query: CalendarQuery) {
  return {
    from: query.from,
    to: query.to,
    kinds: query.kinds.join(','),
    listIds: query.listIds.length ? query.listIds.join(',') : undefined,
    includeFocus: query.includeFocus,
  }
}

export function listCalendarEvents(query: CalendarQuery): Promise<CalendarEvent[]> {
  return http.get('/calendar/events', { params: queryParams(query) })
}

export function getCalendarDaily(query: CalendarQuery): Promise<CalendarDaily[]> {
  return http.get('/calendar/daily', { params: queryParams(query) })
}

export function getCalendarDayInfo(from: string, to: string): Promise<CalendarDayInfo[]> {
  return http.get('/calendar/day-info', { params: { from, to } })
}

export function createCalendarEvent(payload: CalendarEventPayload): Promise<CalendarEvent> {
  return http.post('/calendar/events', payload)
}

export function updateCalendarEvent(id: number, payload: CalendarEventPayload): Promise<CalendarEvent> {
  return http.put(`/calendar/events/${id}`, payload)
}

export function deleteCalendarEvent(id: number, version: number): Promise<null> {
  return http.delete(`/calendar/events/${id}`, { params: { version } })
}

export function batchCalendarEvents(payload: CalendarBatchPayload): Promise<{ updated: number }> {
  return http.post('/calendar/events/batch', payload)
}

export function listCalendarLists(): Promise<CalendarList[]> {
  return http.get('/lists')
}

export function listCalendarTasks(): Promise<CalendarTask[]> {
  return http.get('/tasks')
}

export function listCalendarHabits(): Promise<CalendarHabit[]> {
  return http.get('/habits')
}

export function createCalendarList(payload: Pick<CalendarList, 'name' | 'color' | 'sortOrder' | 'archived'>): Promise<CalendarList> {
  return http.post('/lists', payload)
}

export function updateCalendarList(id: number, payload: Pick<CalendarList, 'name' | 'color' | 'sortOrder' | 'archived'>): Promise<CalendarList> {
  return http.put(`/lists/${id}`, payload)
}

export function createCalendarTask(payload: { listId: number; title: string; note: string; completed: boolean }): Promise<CalendarTask> {
  return http.post('/tasks', payload)
}

export function createCalendarHabit(payload: Pick<CalendarHabit, 'name' | 'color' | 'archived'>): Promise<CalendarHabit> {
  return http.post('/habits', payload)
}

export function getCalendarSettings(): Promise<CalendarSettings> {
  return http.get('/settings')
}

export function updateCalendarSettings(payload: Partial<CalendarSettings>): Promise<CalendarSettings> {
  return http.put('/settings', payload)
}
