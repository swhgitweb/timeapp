import { defineStore } from 'pinia'
import dayjs, { type Dayjs } from 'dayjs'
import {
  batchCalendarEvents,
  calendarEventKey,
  createCalendarHabit,
  createCalendarList,
  createCalendarTask,
  defaultCalendarSettings,
  getCalendarDaily,
  getCalendarDayInfo,
  getCalendarSettings,
  listCalendarEvents,
  listCalendarHabits,
  listCalendarLists,
  listCalendarTasks,
  updateCalendarList,
  updateCalendarSettings,
  type CalendarBatchPayload,
  type CalendarDaily,
  type CalendarDayInfo,
  type CalendarEvent,
  type CalendarHabit,
  type CalendarKind,
  type CalendarList,
  type CalendarQuery,
  type CalendarSettings,
  type CalendarTask,
  type CalendarView,
} from '../api/calendar'

interface CalendarRange {
  start: Dayjs
  end: Dayjs
  days: number
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

export const useCalendarStore = defineStore('calendar', {
  state: () => ({
    anchor: dayjs().format('YYYY-MM-DD'),
    view: 'month' as CalendarView,
    events: [] as CalendarEvent[],
    daily: [] as CalendarDaily[],
    dayInfo: [] as CalendarDayInfo[],
    lists: [] as CalendarList[],
    tasks: [] as CalendarTask[],
    habits: [] as CalendarHabit[],
    settings: defaultCalendarSettings(),
    settingsLoaded: false,
    settingsSaving: false,
    loading: false,
    loadError: '',
    actionError: '',
    actionMessage: '',
    selectionMode: false,
    selectedIds: [] as string[],
    batchLoading: false,
    loadSeq: 0,
    settingsSeq: 0,
  }),

  getters: {
    range(state): CalendarRange {
      const anchor = dayjs(state.anchor).startOf('day')
      if (state.view === 'year') {
        const start = anchor.startOf('year')
        return { start, end: start.add(1, 'year'), days: 0 }
      }
      if (state.view === 'month') {
        const month = anchor.startOf('month')
        const start = month.subtract((month.day() - state.settings.weekStart + 7) % 7, 'day')
        return { start, end: start.add(42, 'day'), days: 42 }
      }
      if (state.view === 'week') {
        const start = anchor.subtract((anchor.day() - state.settings.weekStart + 7) % 7, 'day')
        return { start, end: start.add(7, 'day'), days: 7 }
      }
      if (state.view === 'multi-week') {
        const start = anchor.subtract((anchor.day() - state.settings.weekStart + 7) % 7, 'day')
        return { start, end: start.add(14, 'day'), days: 14 }
      }
      if (state.view === 'multi-day') {
        return { start: anchor, end: anchor.add(3, 'day'), days: 3 }
      }
      return { start: anchor, end: anchor.add(30, 'day'), days: 30 }
    },

    queryKinds(state): CalendarKind[] {
      const kinds = state.settings.visibleKinds.filter((kind) => kind !== 'focus')
      if (state.settings.showFocus) return [...kinds, 'focus']
      // 后端空 kinds 表示全部；仅传 focus 且不含专注时可表达“全部类型关闭”。
      return kinds.length ? kinds : ['focus']
    },

    selectedEvents(state): CalendarEvent[] {
      const selected = new Set(state.selectedIds)
      return state.events.filter((event) => selected.has(calendarEventKey(event)))
    },
  },

  actions: {
    /** 仅加载清单、任务、习惯与设置，供全局侧边栏复用 */
    async ensureMeta() {
      if (this.settingsLoaded || this.loading) return
      this.loading = true
      this.loadError = ''
      try {
        const [settings, lists, tasks, habits] = await Promise.all([
          getCalendarSettings(),
          listCalendarLists(),
          listCalendarTasks(),
          listCalendarHabits(),
        ])
        this.settings = { ...defaultCalendarSettings(), ...settings }
        this.lists = lists
        this.tasks = tasks
        this.habits = habits
        this.view = this.settings.defaultView
        this.settingsLoaded = true
      } catch (error) {
        this.loadError = errorMessage(error, '日历初始化失败')
      } finally {
        this.loading = false
      }
    },

    async bootstrap() {
      await this.ensureMeta()
      if (!this.settingsLoaded) return
      await this.load()
    },

    async load() {
      const mine = ++this.loadSeq
      const range = this.range
      const view = this.view
      const query: CalendarQuery = {
        from: range.start.toISOString(),
        to: range.end.toISOString(),
        kinds: [...this.queryKinds],
        listIds: [...this.settings.visibleListIds],
        includeFocus: this.settings.showFocus,
      }
      const loadDayInfo = view !== 'year' && (this.settings.showLunar || this.settings.showHolidays)
      this.loading = true
      this.loadError = ''
      this.actionMessage = ''
      try {
        if (view === 'year') {
          const daily = await getCalendarDaily(query)
          if (mine !== this.loadSeq) return
          this.daily = daily
          this.events = []
          this.dayInfo = []
          this.selectedIds = []
        } else {
          const [events, dayInfo] = await Promise.all([
            listCalendarEvents(query),
            loadDayInfo
              ? getCalendarDayInfo(range.start.format('YYYY-MM-DD'), range.end.subtract(1, 'day').format('YYYY-MM-DD'))
              : Promise.resolve([]),
          ])
          if (mine !== this.loadSeq) return
          this.events = events
          this.dayInfo = dayInfo
          this.daily = []
          const keys = new Set(events.map(calendarEventKey))
          this.selectedIds = this.selectedIds.filter((id) => keys.has(id))
        }
      } catch (error) {
        if (mine !== this.loadSeq) return
        this.loadError = errorMessage(error, '日历加载失败')
      } finally {
        if (mine === this.loadSeq) this.loading = false
      }
    },

    async changeView(view: CalendarView) {
      if (this.view === view) return
      this.view = view
      this.clearSelection()
      await Promise.all([this.load(), this.saveSettings({ defaultView: view })])
    },

    async shift(delta: number) {
      const current = dayjs(this.anchor)
      this.anchor = (this.view === 'year'
        ? current.add(delta, 'year')
        : this.view === 'month'
          ? current.startOf('month').add(delta, 'month')
          : this.view === 'week'
            ? current.add(delta * 7, 'day')
            : this.view === 'multi-week'
              ? current.add(delta * 14, 'day')
              : this.view === 'multi-day'
                ? current.add(delta * 3, 'day')
                : current.add(delta * 30, 'day')
      ).format('YYYY-MM-DD')
      this.clearSelection()
      await this.load()
    },

    async goToday() {
      this.anchor = dayjs().format('YYYY-MM-DD')
      this.clearSelection()
      await this.load()
    },

    openMonth(date: string) {
      this.anchor = dayjs(date).format('YYYY-MM-DD')
      this.view = 'month'
      this.clearSelection()
      void this.saveSettings({ defaultView: 'month' })
      void this.load()
    },

    async saveSettings(patch: Partial<CalendarSettings>, reload = false) {
      const mine = ++this.settingsSeq
      const previous = this.settings
      const normalized = { ...patch }
      const kindFlags = [
        ['showHabits', 'habit'],
        ['showAnniversaries', 'anniversary'],
        ['showCourses', 'course'],
      ] as const
      if (kindFlags.some(([key]) => patch[key] !== undefined)) {
        let visibleKinds = [...(patch.visibleKinds ?? this.settings.visibleKinds)].filter((kind) => kind !== 'focus')
        for (const [key, kind] of kindFlags) {
          const visible = patch[key]
          if (visible === undefined) continue
          visibleKinds = visibleKinds.filter((item) => item !== kind)
          if (visible) visibleKinds.push(kind)
        }
        normalized.visibleKinds = [...new Set(visibleKinds)]
      }
      this.settings = { ...this.settings, ...normalized }
      this.settingsSaving = true
      this.actionError = ''
      try {
        const saved = await updateCalendarSettings(normalized)
        if (mine !== this.settingsSeq) return
        this.settings = { ...defaultCalendarSettings(), ...saved }
        if (reload) await this.load()
      } catch (error) {
        if (mine === this.settingsSeq) {
          this.settings = previous
          this.actionError = errorMessage(error, '设置保存失败')
        }
      } finally {
        if (mine === this.settingsSeq) this.settingsSaving = false
      }
    },

    async setVisibleKinds(kinds: CalendarKind[]) {
      const visibleKinds = [...new Set(kinds.filter((kind) => kind !== 'focus'))]
      this.clearSelection()
      await this.saveSettings({
        visibleKinds,
        showHabits: visibleKinds.includes('habit'),
        showAnniversaries: visibleKinds.includes('anniversary'),
        showCourses: visibleKinds.includes('course'),
      }, true)
    },

    async toggleList(id: number) {
      const allIds = this.lists.filter((list) => !list.archived).map((list) => list.id)
      const current = this.settings.visibleListIds
      let next: number[]
      if (current.includes(-1)) {
        next = [id]
      } else if (!current.length) {
        next = allIds.filter((listId) => listId !== id)
      } else {
        next = current.includes(id) ? current.filter((listId) => listId !== id) : [...current, id]
      }
      if (next.length === allIds.length) next = []
      // 空数组在 API 中表示全部，-1 表示用户主动取消全部清单。
      else if (!next.length) next = [-1]
      this.clearSelection()
      await this.saveSettings({ visibleListIds: next }, true)
    },

    toggleSelectionMode() {
      this.selectionMode = !this.selectionMode
      if (!this.selectionMode) this.selectedIds = []
      this.actionError = ''
    },

    toggleSelection(event: CalendarEvent) {
      const key = calendarEventKey(event)
      this.selectedIds = this.selectedIds.includes(key)
        ? this.selectedIds.filter((id) => id !== key)
        : [...this.selectedIds, key]
    },

    clearSelection() {
      this.selectedIds = []
    },

    async runBatch(action: CalendarBatchPayload['action'], options?: { listId?: number; date?: string }) {
      const items = new Map<number, { id: number; version: number }>()
      for (const event of this.selectedEvents) {
        const id = event.eventId ?? event.id
        if (!items.has(id)) items.set(id, { id, version: event.version })
      }
      if (!items.size) return false
      if (items.size > 100) {
        this.actionError = '一次最多批量处理 100 项日程'
        return false
      }
      this.batchLoading = true
      this.actionError = ''
      try {
        const result = await batchCalendarEvents({ action, items: [...items.values()], ...options })
        this.clearSelection()
        await this.load()
        this.actionMessage = `已处理 ${result.updated} 项日程`
        return true
      } catch (error) {
        this.actionError = errorMessage(error, '批量操作失败')
        return false
      } finally {
        this.batchLoading = false
      }
    },

    async addList(payload: { name: string; color: string }) {
      const sortOrder = this.lists.reduce((max, list) => Math.max(max, list.sortOrder ?? 0), 0) + 1
      const list = await createCalendarList({ ...payload, sortOrder, archived: false })
      this.lists = [...this.lists, list].sort((a, b) => a.sortOrder - b.sortOrder)
      return list
    },

    async setListColor(list: CalendarList, color: string) {
      const saved = await updateCalendarList(list.id, {
        name: list.name, color, sortOrder: list.sortOrder, archived: list.archived,
      })
      this.lists = this.lists.map((item) => item.id === list.id ? saved : item)
      await this.load()
      return saved
    },

    async addTask(payload: { listId: number; title: string; note: string }) {
      const task = await createCalendarTask({ ...payload, completed: false })
      this.tasks = [...this.tasks, task]
      return task
    },

    async addHabit(payload: { name: string; color: string }) {
      const habit = await createCalendarHabit({ ...payload, archived: false })
      this.habits = [...this.habits, habit]
      return habit
    },
  },
})
