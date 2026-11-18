<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { useRouter } from 'vue-router'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import CalendarAgendaView from '../components/calendar/CalendarAgendaView.vue'
import CalendarDataDialog from '../components/calendar/CalendarDataDialog.vue'
import CalendarEventDialog from '../components/calendar/CalendarEventDialog.vue'
import CalendarMonthView from '../components/calendar/CalendarMonthView.vue'
import CalendarTimeGrid from '../components/calendar/CalendarTimeGrid.vue'
import CalendarYearView from '../components/calendar/CalendarYearView.vue'
import SubscriptionManager from '../components/calendar/SubscriptionManager.vue'
import {
  CALENDAR_KIND_LABELS,
  type CalendarEvent,
  type CalendarKind,
  type CalendarView,
} from '../api/calendar'
import { useCalendarStore } from '../stores/calendar'

const store = useCalendarStore()
const router = useRouter()
const showSettings = ref(false)
const quickOpen = ref(false)
const dataOpen = ref(false)
const editorOpen = ref(false)
const editingEvent = ref<CalendarEvent | null>(null)
const editorDate = ref(dayjs().format('YYYY-MM-DD'))
const confirmBatchDelete = ref(false)
const moveListId = ref('')
const rescheduleDate = ref(dayjs().format('YYYY-MM-DD'))

const views: { value: CalendarView; label: string }[] = [
  { value: 'year', label: '年' },
  { value: 'month', label: '月' },
  { value: 'week', label: '周' },
  { value: 'agenda', label: '日程' },
  { value: 'multi-day', label: '多日' },
  { value: 'multi-week', label: '多周' },
]
const kindOptions = (['task', 'habit', 'anniversary', 'course', 'ics'] as CalendarKind[])
  .map((value) => ({ value, label: CALENDAR_KIND_LABELS[value] }))
const activeLists = computed(() => store.lists.filter((list) => !list.archived))

const periodTitle = computed(() => {
  if (store.view === 'year') return dayjs(store.anchor).format('YYYY年')
  if (store.view === 'month') return dayjs(store.anchor).format('YYYY年M月')
  const start = store.range.start
  const end = store.range.end.subtract(1, 'day')
  if (store.view === 'agenda') return `${start.format('M月D日')} 起 30 天`
  return `${start.format('YYYY年M月D日')} – ${end.format(start.isSame(end, 'year') ? 'M月D日' : 'YYYY年M月D日')}`
})

const subtitle = computed(() => {
  const info = store.dayInfo.find((item) => item.date === store.anchor)
  if (!info) return '安排任务、习惯、纪念日与课程'
  const parts: string[] = []
  if (store.settings.showLunar && info.lunar) parts.push(info.lunar)
  if (store.settings.showHolidays && info.holiday) parts.push(info.holiday)
  if (store.settings.showHolidays && info.lunarFestivals?.length) parts.push(info.lunarFestivals.join('、'))
  return parts.length ? parts.join(' · ') : '安排任务、习惯、纪念日与课程'
})

const isEmpty = computed(() => !store.loading && !store.loadError && store.view === 'agenda' && store.events.length === 0)
const yearIsEmpty = computed(() => !store.loading && !store.loadError && store.view === 'year' && store.daily.every((day) => day.count === 0))

const selected = computed(() => store.selectedEvents)
const canComplete = computed(() => selected.value.length > 0 && selected.value.every(isEditableTask))
const canMove = canComplete
const canDelete = computed(() => selected.value.length > 0 && selected.value.every(isLocalEvent))
const canReschedule = canDelete
const batchReason = computed(() => {
  if (!selected.value.length) return '点击日程进行选择；专注记录可在记录页编辑。'
  const reasons: string[] = []
  if (selected.value.some((event) => event.kind !== 'task')) reasons.push('完成和移动清单仅支持任务')
  if (selected.value.some((event) => event.source === 'ics' || event.readOnly)) reasons.push('ICS 订阅为只读')
  if (selected.value.some((event) => event.kind === 'focus')) reasons.push('专注记录不能参与批量操作')
  return reasons.length ? `${reasons.join('；')}。` : '改期会保留原开始时刻和时长。'
})

function isEditableTask(event: CalendarEvent): boolean {
  return event.kind === 'task' && !!event.taskId && event.source === 'local' && !event.readOnly
}

function isLocalEvent(event: CalendarEvent): boolean {
  return event.source === 'local' && !event.readOnly && event.kind !== 'ics' && event.kind !== 'focus'
}

function openEvent(event: CalendarEvent) {
  if (event.kind === 'focus') {
    const sessionId = event.sessionId ?? event.id
    void router.push({ path: '/records', query: { edit: String(sessionId) } })
    return
  }
  editingEvent.value = event
  editorDate.value = dayjs(event.startAt).format('YYYY-MM-DD')
  editorOpen.value = true
}

function createEvent(date?: string) {
  editingEvent.value = null
  editorDate.value = date || store.anchor
  editorOpen.value = true
}

function onSaved() {
  void store.load()
}

function manageData() {
  editorOpen.value = false
  dataOpen.value = true
}

function isListVisible(id: number): boolean {
  const visible = store.settings.visibleListIds
  return visible.length === 0 || visible.includes(id)
}

function toggleListFilter(id: number) {
  if (store.settingsSaving) return
  void store.toggleList(id)
}

function showAllLists() {
  if (store.settingsSaving) return
  store.clearSelection()
  void store.saveSettings({ visibleListIds: [] }, true)
}

function onKindChange(kind: CalendarKind, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  const current = store.settings.visibleKinds.filter((item) => item !== 'focus')
  const next = checked ? [...current, kind] : current.filter((item) => item !== kind)
  void store.setVisibleKinds(next)
}

function onBooleanSetting(key: 'showFocus' | 'showLunar' | 'showHolidays', event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  store.clearSelection()
  void store.saveSettings({ [key]: checked }, true)
}

function onWeekStart(event: Event) {
  const weekStart = Number((event.target as HTMLSelectElement).value) as 0 | 1
  void store.saveSettings({ weekStart }, true)
}

async function runBatch(action: 'complete' | 'move-list' | 'reschedule') {
  if (action === 'move-list' && !moveListId.value) return
  if (action === 'reschedule' && !rescheduleDate.value) return
  await store.runBatch(action, action === 'move-list'
    ? { listId: Number(moveListId.value) }
    : action === 'reschedule'
      ? { date: rescheduleDate.value }
      : undefined)
}

onMounted(() => { void store.bootstrap() })
</script>

<template>
  <main class="page calendar-page">
    <header class="page-head">
      <div>
        <h1>{{ periodTitle }}</h1>
        <p class="head-sub">{{ subtitle }}</p>
      </div>
      <div class="toolbar">
        <SubscriptionManager @changed="store.load()" />
        <button class="ghost-btn small" @click="dataOpen = true">清单与数据</button>
        <button class="ghost-btn small" :class="{ active: store.selectionMode }" @click="store.toggleSelectionMode()">
          {{ store.selectionMode ? '退出选择' : '批量选择' }}
        </button>
        <button class="primary-btn" @click="createEvent()">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
          新增日程
        </button>
      </div>
    </header>

    <div class="calendar-controls">
      <div class="toolbar">
        <button class="ghost-btn small" @click="store.goToday()">今天</button>
        <div class="nav-group">
          <button class="icon-btn" aria-label="上一页" @click="store.shift(-1)">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7" /></svg>
          </button>
          <button class="icon-btn" aria-label="下一页" @click="store.shift(1)">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7" /></svg>
          </button>
        </div>
        <div class="segmented" aria-label="日历视图">
          <button
            v-for="item in views"
            :key="item.value"
            :class="{ active: store.view === item.value }"
            @click="store.changeView(item.value)"
          >{{ item.label }}</button>
        </div>
      </div>

      <div class="toolbar">
        <button class="ghost-btn small" :class="{ active: showSettings }" @click="showSettings = !showSettings">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 6h16M7 12h10M10 18h4" /></svg>
          筛选与显示
        </button>
        <div class="settings-wrap">
          <button class="icon-btn" :class="{ active: quickOpen }" title="显示设置" aria-label="显示设置" @click="quickOpen = !quickOpen">
            <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" /></svg>
          </button>
          <div v-if="quickOpen" class="popover">
            <p class="popover-title">显示内容</p>
            <label class="check-row"><input type="checkbox" :checked="store.settings.showLunar" :disabled="store.settingsSaving" @change="onBooleanSetting('showLunar', $event)" />农历</label>
            <label class="check-row"><input type="checkbox" :checked="store.settings.showHolidays" :disabled="store.settingsSaving" @change="onBooleanSetting('showHolidays', $event)" />节假日</label>
            <label class="check-row"><input type="checkbox" :checked="store.settings.showFocus" :disabled="store.settingsSaving" @change="onBooleanSetting('showFocus', $event)" />专注记录</label>
            <button class="popover-link" @click="showSettings = true; quickOpen = false">更多筛选与显示</button>
          </div>
        </div>
      </div>
    </div>

    <div class="filter-row">
      <span class="filter-label">清单</span>
      <button
        v-for="list in activeLists"
        :key="list.id"
        class="filter-chip"
        :class="{ active: isListVisible(list.id) }"
        :disabled="store.settingsSaving"
        :title="isListVisible(list.id) ? '点击在日历中隐藏' : '点击在日历中显示'"
        @click="toggleListFilter(list.id)"
      >
        <span class="dot" :style="{ background: list.color }"></span>{{ list.name }}
      </button>
      <button v-if="activeLists.length" class="text-btn" @click="showAllLists">全部显示</button>
      <span class="filter-hint">点击清单可控制其是否显示在日历中</span>
    </div>

    <section v-if="showSettings" class="card settings-panel">
      <div class="setting-group lists-group">
        <div class="setting-title"><span>清单筛选</span><button :disabled="store.settingsSaving" @click="showAllLists">全部清单</button></div>
        <div class="check-list">
          <label v-for="list in activeLists" :key="list.id" class="check-item">
            <input type="checkbox" :checked="isListVisible(list.id)" :disabled="store.settingsSaving" @change="toggleListFilter(list.id)" />
            <span class="list-color" :style="{ background: list.color }"></span>{{ list.name }}
          </label>
          <span v-if="!activeLists.length" class="muted">暂无清单，可先在“清单与数据”中创建。</span>
        </div>
      </div>
      <div class="setting-group">
        <div class="setting-title">事件类型</div>
        <div class="check-list">
          <label v-for="kind in kindOptions" :key="kind.value" class="check-item">
            <input type="checkbox" :checked="store.settings.visibleKinds.includes(kind.value)" :disabled="store.settingsSaving" @change="onKindChange(kind.value, $event)" />{{ kind.label }}
          </label>
        </div>
      </div>
      <div class="setting-group">
        <div class="setting-title">显示内容</div>
        <div class="check-list">
          <label class="check-item"><input type="checkbox" :checked="store.settings.showFocus" :disabled="store.settingsSaving" @change="onBooleanSetting('showFocus', $event)" />专注记录</label>
          <label class="check-item"><input type="checkbox" :checked="store.settings.showLunar" :disabled="store.settingsSaving" @change="onBooleanSetting('showLunar', $event)" />农历</label>
          <label class="check-item"><input type="checkbox" :checked="store.settings.showHolidays" :disabled="store.settingsSaving" @change="onBooleanSetting('showHolidays', $event)" />节假日</label>
        </div>
      </div>
      <div class="setting-group compact-settings">
        <label>每周起始日<select class="input" :value="store.settings.weekStart" :disabled="store.settingsSaving" @change="onWeekStart"><option :value="1">周一</option><option :value="0">周日</option></select></label>
      </div>
      <span v-if="store.settingsSaving" class="muted saving-text">正在保存设置…</span>
    </section>

    <section v-if="store.selectionMode" class="card batch-panel">
      <div class="batch-head"><strong>已选 {{ selected.length }} 项</strong><span>{{ batchReason }}</span><button class="text-btn" @click="store.clearSelection()">清空</button></div>
      <div class="batch-actions">
        <button class="ghost-btn small" :disabled="!canComplete || store.batchLoading" title="仅任务可完成" @click="runBatch('complete')">完成任务</button>
        <button class="ghost-btn small danger-text" :disabled="!canDelete || store.batchLoading" title="本站日程可删除，ICS 与专注只读" @click="confirmBatchDelete = true">删除</button>
        <div class="batch-field"><select v-model="moveListId" class="input" aria-label="目标清单"><option value="">移动到清单…</option><option v-for="list in activeLists" :key="list.id" :value="String(list.id)">{{ list.name }}</option></select><button class="ghost-btn small" :disabled="!canMove || !moveListId || store.batchLoading" @click="runBatch('move-list')">移动</button></div>
        <div class="batch-field"><input v-model="rescheduleDate" class="input" type="date" aria-label="改期日期" /><button class="ghost-btn small" :disabled="!canReschedule || !rescheduleDate || store.batchLoading" @click="runBatch('reschedule')">改期</button></div>
        <span v-if="store.batchLoading" class="muted">处理中…</span>
      </div>
    </section>

    <div v-if="store.actionError" class="notice error-notice"><span>{{ store.actionError }}</span><button @click="store.actionError = ''">关闭</button></div>
    <div v-if="store.actionMessage" class="notice success-notice">{{ store.actionMessage }}</div>
    <div v-if="store.loading" class="loading-bar" role="status"><span class="spinner"></span>正在加载日历…</div>

    <section v-if="store.loadError" class="card error-card">
      <p>{{ store.loadError }}</p>
      <button class="ghost-btn" @click="store.bootstrap()">重试</button>
    </section>

    <div v-else class="calendar-body">
      <div v-if="isEmpty" class="empty-state"><strong>当前范围暂无日程</strong><span>可点击“新增日程”，或点击日期数字创建当天日程。</span></div>
      <div v-if="yearIsEmpty" class="empty-state"><strong>这一年还没有日程</strong><span>选择月份或直接新增一项日程。</span></div>

      <section v-if="store.view === 'year'" class="calendar-panel year-panel">
        <CalendarYearView :year="dayjs(store.anchor).year()" :daily="store.daily" :week-start="store.settings.weekStart" @select="store.openMonth" />
      </section>
      <section v-else-if="store.view === 'month'" class="calendar-panel month-panel">
        <CalendarMonthView :start-date="store.range.start.format('YYYY-MM-DD')" :days="42" :reference-date="store.anchor" :week-start="store.settings.weekStart" :events="store.events" :day-info="store.dayInfo" :show-lunar="store.settings.showLunar" :show-holidays="store.settings.showHolidays" :selection-mode="store.selectionMode" :selected-ids="store.selectedIds" @open="openEvent" @select="store.toggleSelection" @create="createEvent" />
      </section>
      <section v-else-if="store.view === 'week' || store.view === 'multi-day'" class="calendar-panel time-panel">
        <CalendarTimeGrid :key="`${store.view}-${store.range.start.format('YYYY-MM-DD')}`" :start-date="store.range.start.format('YYYY-MM-DD')" :days="store.view === 'week' ? 7 : 3" :events="store.events" :selection-mode="store.selectionMode" :selected-ids="store.selectedIds" @open="openEvent" @select="store.toggleSelection" @create="createEvent" />
      </section>
      <section v-else-if="store.view === 'multi-week'" class="calendar-panel month-panel">
        <CalendarMonthView :start-date="store.range.start.format('YYYY-MM-DD')" :days="14" :reference-date="store.anchor" :week-start="store.settings.weekStart" :events="store.events" :day-info="store.dayInfo" :show-lunar="store.settings.showLunar" :show-holidays="store.settings.showHolidays" compact :selection-mode="store.selectionMode" :selected-ids="store.selectedIds" @open="openEvent" @select="store.toggleSelection" @create="createEvent" />
      </section>
      <section v-else class="card calendar-panel agenda-panel">
        <CalendarAgendaView :events="store.events" :day-info="store.dayInfo" :show-lunar="store.settings.showLunar" :show-holidays="store.settings.showHolidays" :selection-mode="store.selectionMode" :selected-ids="store.selectedIds" @open="openEvent" @select="store.toggleSelection" />
      </section>
    </div>

    <CalendarEventDialog :open="editorOpen" :event="editingEvent" :initial-date="editorDate" :tasks="store.tasks" :habits="store.habits" @close="editorOpen = false" @saved="onSaved" @manage-data="manageData" />
    <CalendarDataDialog :open="dataOpen" @close="dataOpen = false" />
    <ConfirmDialog :open="confirmBatchDelete" title="删除选中的日程？" :message="`将删除 ${selected.length} 项选中日程；重复日程会删除整个系列。`" confirm-text="删除" danger :loading="store.batchLoading" @confirm="confirmBatchDelete = false; store.runBatch('delete')" @cancel="confirmBatchDelete = false" />
  </main>
</template>

<style scoped>
.calendar-page {
  min-height: 100%;
}

.calendar-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 4px 24px 10px;
}

.calendar-controls .ghost-btn.active,
.calendar-controls .toolbar > .ghost-btn.active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
}

.calendar-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 16px 16px;
  min-width: 0;
}

.settings-panel {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr;
  gap: 18px;
  margin: 0 24px 12px;
  padding: 18px;
}

.setting-group {
  min-width: 0;
}

.setting-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 9px;
  font-size: 13px;
  font-weight: 600;
}

.setting-title button {
  color: var(--accent);
  padding: 0;
  font-size: 12px;
}

.check-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 13px;
}

.check-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--ink-2);
  font-size: 12px;
  cursor: pointer;
}

.list-color {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.compact-settings {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 10px;
}

.compact-settings label {
  color: var(--muted);
  font-size: 12px;
}

.compact-settings .input {
  margin-top: 7px;
  padding: 7px 9px;
  font-size: 12px;
}

.saving-text {
  grid-column: 1 / -1;
}

.batch-panel {
  margin: 0 24px 12px;
  padding: 14px 16px;
  border-color: #c9d6ff;
}

.batch-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 11px;
  font-size: 13px;
}

.batch-head > span {
  flex: 1;
  color: var(--ink-2);
  font-size: 12px;
}

.batch-actions,
.batch-field {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.batch-field .input {
  width: auto;
  min-width: 126px;
  padding: 7px 9px;
  font-size: 12px;
}

.danger-text {
  color: var(--danger);
}

.notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 24px 10px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.error-notice {
  background: var(--danger-weak);
  color: var(--danger);
}

.error-notice button {
  color: inherit;
}

.success-notice {
  background: var(--success-weak);
  color: var(--success);
}

.loading-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 30px;
  color: var(--muted);
  font-size: 12px;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--line);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-card {
  margin: 0 24px;
  padding: 28px;
  text-align: center;
}

.error-card p {
  margin: 0 0 14px;
  color: var(--danger);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin: 0 8px;
  padding: 18px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  color: var(--muted);
  font-size: 13px;
}

.empty-state strong {
  color: var(--ink);
  font-size: 14px;
}

.calendar-panel {
  min-width: 0;
}

.month-panel {
  flex: 1;
  min-height: 0;
}

.year-panel {
  padding: 0;
}

.time-panel {
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}

.agenda-panel {
  padding: 18px;
}

@media (max-width: 900px) {
  .calendar-controls {
    padding: 4px 16px 10px;
  }

  .settings-panel {
    grid-template-columns: 1fr 1fr;
    margin: 0 16px 12px;
  }

  .lists-group {
    grid-column: 1 / -1;
  }

  .batch-panel,
  .notice,
  .error-card {
    margin-left: 16px;
    margin-right: 16px;
  }

  .calendar-body {
    padding: 0 8px 12px;
  }
}

@media (max-width: 620px) {
  .calendar-controls .segmented {
    width: 100%;
    overflow-x: auto;
  }

  .calendar-controls .segmented button {
    flex: 1;
  }

  .settings-panel {
    grid-template-columns: 1fr;
    padding: 15px;
  }

  .lists-group {
    grid-column: auto;
  }

  .batch-head {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .batch-head > span {
    flex-basis: calc(100% - 60px);
  }

  .batch-actions {
    align-items: stretch;
  }

  .batch-field {
    width: 100%;
  }

  .batch-field .input {
    flex: 1;
  }

  .agenda-panel {
    padding: 13px;
  }
}
</style>
