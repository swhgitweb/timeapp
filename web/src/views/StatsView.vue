<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { getDaily, getSummary, listSessions, type DailyVO, type SessionVO } from '../api/session'
import {
  listCalendarLists,
  listCalendarTasks,
  type CalendarList,
  type CalendarTask,
} from '../api/calendar'
import { todayStr } from '../utils/date'

type Period = 'day' | 'week' | 'month'

const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']
const FALLBACK_COLOR = '#a5adba'
const today = dayjs().startOf('day')
const todayKey = todayStr()

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 分钟 → "1h20m" / "45m" */
function fmtMin(minutes: number): string {
  const value = Math.max(0, Math.round(minutes || 0))
  const h = Math.floor(value / 60)
  const m = value % 60
  return h ? `${h}h${m ? `${m}m` : ''}` : `${m}m`
}

function comparison(delta: number, unit = ''): { text: string; direction: 'up' | 'down' | 'flat' } {
  if (!delta) return { text: '与前一天持平', direction: 'flat' }
  const amount = unit === '个' ? Math.abs(delta) : fmtMin(Math.abs(delta))
  return {
    text: `比前一天${delta > 0 ? '多' : '少'}${amount}${unit}`,
    direction: delta > 0 ? 'up' : 'down',
  }
}

// ---------- 周期计算 ----------
function periodStart(anchor: Dayjs, period: Period): Dayjs {
  if (period === 'week') return anchor.subtract((anchor.day() + 6) % 7, 'day').startOf('day')
  if (period === 'month') return anchor.startOf('month')
  return anchor.startOf('day')
}

function periodEnd(anchor: Dayjs, period: Period): Dayjs {
  if (period === 'week') return periodStart(anchor, 'week').add(7, 'day')
  if (period === 'month') return periodStart(anchor, 'month').add(1, 'month')
  return periodStart(anchor, 'day').add(1, 'day')
}

function isCurrentPeriod(anchor: Dayjs, period: Period): boolean {
  const start = periodStart(anchor, period)
  const end = periodEnd(anchor, period)
  return !today.isBefore(start) && today.isBefore(end)
}

/** 周期内最后一个自然日；当前周期截止到今天，避免把未来日期算进柱状图 */
function lastDay(start: Dayjs, endExclusive: Dayjs): Dayjs {
  const last = endExclusive.subtract(1, 'day').startOf('day')
  if (last.isBefore(start)) return start
  return last.isAfter(today) ? today : last
}

function periodLabel(anchor: Dayjs, period: Period): string {
  if (isCurrentPeriod(anchor, period)) {
    return period === 'day' ? '今天' : period === 'week' ? '本周' : '本月'
  }
  if (period === 'day') return anchor.format('M月D日')
  if (period === 'month') return anchor.format('YYYY年M月')
  const start = periodStart(anchor, 'week')
  const end = start.add(6, 'day')
  return `${start.format('M/D')} - ${end.format('M/D')}`
}

// ---------- 公共状态 ----------
const loading = ref(false)
const loadError = ref('')
const lists = ref<CalendarList[]>([])
const tasks = ref<CalendarTask[]>([])

const listMap = computed(() => new Map(lists.value.map((list) => [list.id, list])))
const taskMap = computed(() => new Map(tasks.value.map((task) => [task.id, task])))

function listOf(session: SessionVO): CalendarList | undefined {
  const task = session.taskId != null ? taskMap.value.get(session.taskId) : undefined
  return task?.listId != null ? listMap.value.get(task.listId) : undefined
}

function sessionColor(session: SessionVO): string {
  return listOf(session)?.color || FALLBACK_COLOR
}

async function run(task: () => Promise<void>) {
  try {
    await task()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败'
  }
}

// ---------- 概览 ----------
const overview = ref({
  todayPomodoro: 0,
  totalPomodoro: 0,
  todayMinutes: 0,
  totalMinutes: 0,
  countDelta: 0,
  minuteDelta: 0,
})

const hints = computed(() => ({
  count: comparison(overview.value.countDelta, '个'),
  minutes: comparison(overview.value.minuteDelta),
}))

async function loadOverview() {
  const yesterdayKey = today.subtract(1, 'day').format('YYYY-MM-DD')
  const [all, todaySummary, yesterdaySummary] = await Promise.all([
    getSummary(),
    getSummary(todayKey, todayKey),
    getSummary(yesterdayKey, yesterdayKey),
  ])
  overview.value = {
    todayPomodoro: todaySummary.pomodoroCount,
    totalPomodoro: all.pomodoroCount,
    todayMinutes: Math.round(todaySummary.totalSec / 60),
    totalMinutes: Math.round(all.totalSec / 60),
    countDelta: todaySummary.pomodoroCount - yesterdaySummary.pomodoroCount,
    minuteDelta: Math.round((todaySummary.totalSec - yesterdaySummary.totalSec) / 60),
  }
}

async function loadMeta() {
  const [listRows, taskRows] = await Promise.all([listCalendarLists(), listCalendarTasks()])
  lists.value = listRows
  tasks.value = taskRows
}

// ---------- 专注详情 ----------
const groupMode = ref<'list' | 'task'>('list')
const detailPeriod = ref<Period>('week')
const detailAnchor = ref(dayjs())
const detailRecords = ref<SessionVO[]>([])
const detailLoading = ref(false)

const detailCurrent = computed(() => isCurrentPeriod(detailAnchor.value, detailPeriod.value))
const detailLabel = computed(() => periodLabel(detailAnchor.value, detailPeriod.value))

async function loadDetail() {
  const start = periodStart(detailAnchor.value, detailPeriod.value)
  const end = periodEnd(detailAnchor.value, detailPeriod.value)
  detailLoading.value = true
  try {
    detailRecords.value = await listSessions({
      from: start.format('YYYY-MM-DD'),
      to: lastDay(start, end).format('YYYY-MM-DD'),
      limit: 500,
    })
  } finally {
    detailLoading.value = false
  }
}

interface DistItem {
  key: string
  name: string
  color: string
  sec: number
  minutes: number
  count: number
  percent: number
}

const detailItems = computed<DistItem[]>(() => {
  const groups = new Map<string, { key: string; name: string; color: string; sec: number; count: number }>()
  for (const record of detailRecords.value) {
    const list = listOf(record)
    const byList = groupMode.value === 'list'
    const key = byList ? (list ? `list-${list.id}` : 'unlinked') : `task-${record.taskTitle || '未关联任务'}`
    const name = byList ? (list?.name || '未关联任务') : (record.taskTitle || '未关联任务')
    const color = byList ? (list?.color || FALLBACK_COLOR) : sessionColor(record)
    const item = groups.get(key) ?? { key, name, color, sec: 0, count: 0 }
    item.sec += Math.max(0, record.durationSec)
    item.count += 1
    groups.set(key, item)
  }
  const totalSec = [...groups.values()].reduce((sum, item) => sum + item.sec, 0)
  return [...groups.values()]
    .sort((a, b) => b.sec - a.sec)
    .map((item) => ({
      ...item,
      minutes: Math.round(item.sec / 60),
      percent: totalSec ? Math.round((item.sec / totalSec) * 100) : 0,
    }))
})

const detailTotal = computed(() => detailItems.value.reduce((sum, item) => sum + item.minutes, 0))

const donutStyle = computed(() => {
  const total = detailItems.value.reduce((sum, item) => sum + item.sec, 0)
  if (!total) return { background: '#edf0f5' }
  let cursor = 0
  const stops = detailItems.value.map((item) => {
    const start = cursor
    cursor += (item.sec / total) * 100
    return `${item.color} ${start}% ${cursor}%`
  })
  return { background: `conic-gradient(${stops.join(',')})` }
})

// ---------- 专注记录 ----------
const recentRecords = ref<SessionVO[]>([])

async function loadRecent() {
  recentRecords.value = await listSessions({ limit: 16 })
}

interface RecordItem {
  record: SessionVO
  range: string
  color: string
}

const recordGroups = computed<{ key: string; label: string; items: RecordItem[] }[]>(() => {
  const groups: { key: string; label: string; items: RecordItem[] }[] = []
  for (const record of recentRecords.value) {
    const key = record.sessionDate
    let group = groups[groups.length - 1]
    if (!group || group.key !== key) {
      group = { key, label: dayjs(key).format('M月D日'), items: [] }
      groups.push(group)
    }
    const started = dayjs(record.startedAt)
    const ended = started.add(record.durationSec, 'second')
    group.items.push({
      record,
      range: `${started.format('HH:mm')} - ${ended.format('HH:mm')}`,
      color: sessionColor(record),
    })
  }
  return groups
})

// ---------- 专注趋势 ----------
const trendPeriod = ref<'week' | 'month'>('week')
const trendAnchor = ref(dayjs())
const trendDaily = ref<DailyVO[]>([])

const trendCurrent = computed(() => isCurrentPeriod(trendAnchor.value, trendPeriod.value))
const trendLabel = computed(() => periodLabel(trendAnchor.value, trendPeriod.value))

async function loadTrend() {
  const start = periodStart(trendAnchor.value, trendPeriod.value)
  const end = periodEnd(trendAnchor.value, trendPeriod.value)
  trendDaily.value = await getDaily(start.format('YYYY-MM-DD'), lastDay(start, end).format('YYYY-MM-DD'))
}

const trend = computed(() => {
  const width = 620
  const height = 230
  const left = 46
  const right = 12
  const top = 18
  const bottom = 34
  const innerW = width - left - right
  const innerH = height - top - bottom
  const days = trendDaily.value.map((day) => ({
    key: day.date,
    minutes: Math.round(day.totalSec / 60),
  }))
  const max = Math.max(60, Math.ceil(Math.max(0, ...days.map((day) => day.minutes)) / 60) * 60)
  const x = (index: number) =>
    left + (days.length === 1 ? innerW / 2 : (index / (days.length - 1)) * innerW)
  const y = (value: number) => top + (1 - value / max) * innerH
  const points = days.map((day, index) => ({ ...day, x: x(index), y: y(day.minutes) }))
  const polyline = points.map((point) => `${point.x},${point.y}`).join(' ')
  const area = points.length
    ? `M ${points[0].x} ${top + innerH} L ${points.map((point) => `${point.x} ${point.y}`).join(' L ')} L ${points[points.length - 1].x} ${top + innerH} Z`
    : ''
  const grids = Array.from({ length: 5 }, (_, index) => {
    const value = (max / 4) * index
    return { value, y: y(value), label: fmtMin(value) }
  })
  const labels = points.map((point, index) => ({
    x: point.x,
    text:
      trendPeriod.value === 'week'
        ? WEEK_LABELS[dayjs(point.key).day()]
        : index === 0 || dayjs(point.key).date() % 5 === 0 || index === points.length - 1
          ? `${dayjs(point.key).date()}日`
          : '',
  }))
  const total = days.reduce((sum, day) => sum + day.minutes, 0)
  const average = days.length ? Math.round(total / days.length) : 0
  return { width, height, top, bottom, left, innerH, points, polyline, area, grids, labels, average }
})

// ---------- 专注时间线 ----------
const timelineAnchor = ref(dayjs())
const timelineRecords = ref<SessionVO[]>([])

const timelineCurrent = computed(() => isCurrentPeriod(timelineAnchor.value, 'week'))
const timelineLabel = computed(() => periodLabel(timelineAnchor.value, 'week'))

async function loadTimeline() {
  const start = periodStart(timelineAnchor.value, 'week')
  timelineRecords.value = await listSessions({
    from: start.format('YYYY-MM-DD'),
    to: lastDay(start, start.add(7, 'day')).format('YYYY-MM-DD'),
    limit: 500,
  })
}

const timelineDays = computed(() => {
  const start = periodStart(timelineAnchor.value, 'week')
  return Array.from({ length: 7 }, (_, index) => {
    const date = start.add(index, 'day')
    const key = date.format('YYYY-MM-DD')
    const events = timelineRecords.value
      .filter((record) => record.sessionDate === key)
      .map((record) => {
        const started = dayjs(record.startedAt)
        const startMinutes = started.hour() * 60 + started.minute()
        const minutes = Math.min(Math.round(record.durationSec / 60), 1440 - startMinutes)
        return {
          id: record.id,
          top: (startMinutes / 1440) * 100,
          height: Math.max(1.4, (minutes / 1440) * 100),
          color: sessionColor(record),
          title: `${record.taskTitle || '未关联任务'} · ${started.format('HH:mm')} · ${fmtMin(minutes)}`,
        }
      })
    return { key, label: WEEK_LABELS[date.day()], date: date.date(), events }
  })
})

// ---------- 最佳专注时间 ----------
const bestAnchor = ref(dayjs())
const bestRecords = ref<SessionVO[]>([])

const bestCurrent = computed(() => isCurrentPeriod(bestAnchor.value, 'month'))
const bestLabel = computed(() => periodLabel(bestAnchor.value, 'month'))

async function loadBest() {
  const start = periodStart(bestAnchor.value, 'month')
  const end = periodEnd(bestAnchor.value, 'month')
  bestRecords.value = await listSessions({
    from: start.format('YYYY-MM-DD'),
    to: lastDay(start, end).format('YYYY-MM-DD'),
    limit: 500,
  })
}

const best = computed(() => {
  const width = 620
  const height = 230
  const left = 42
  const right = 10
  const top = 16
  const bottom = 32
  const innerW = width - left - right
  const innerH = height - top - bottom
  const totals = Array.from({ length: 24 }, () => 0)
  for (const record of bestRecords.value) {
    const hour = dayjs(record.startedAt).hour()
    totals[hour] += Math.round(record.durationSec / 60)
  }
  const max = Math.max(60, Math.ceil(Math.max(0, ...totals) / 60) * 60)
  const step = innerW / 24
  const bars = totals.map((minutes, hour) => {
    const barHeight = (minutes / max) * innerH
    return {
      hour,
      minutes,
      x: left + hour * step + step * 0.22,
      width: step * 0.56,
      y: top + innerH - barHeight,
      height: barHeight,
      label: hour % 3 === 0 ? `${pad(hour)}:00` : '',
    }
  })
  const grids = Array.from({ length: 5 }, (_, index) => {
    const value = (max / 4) * index
    return { value, y: top + innerH - (value / max) * innerH, label: fmtMin(value) }
  })
  return { width, height, left, top, innerW, innerH, bars, grids }
})

// ---------- 年度热力图 ----------
const heatYear = ref(dayjs().year())
const heatDaily = ref<DailyVO[]>([])

async function loadHeat() {
  heatDaily.value = await getDaily(`${heatYear.value}-01-01`, `${heatYear.value}-12-31`)
}

const heatTotals = computed(() => {
  const totals = new Map<string, number>()
  for (const day of heatDaily.value) totals.set(day.date, Math.round(day.totalSec / 60))
  return totals
})

const heatCells = computed(() => {
  const year = heatYear.value
  const first = dayjs(`${year}-01-01`)
  const gridStart = first.subtract((first.day() + 6) % 7, 'day')
  const cells: { key: string; empty: boolean; level: number; today: boolean; title: string }[] = []
  for (let week = 0; week < 53; week++) {
    for (let weekday = 0; weekday < 7; weekday++) {
      const date = gridStart.add(week * 7 + weekday, 'day')
      if (date.year() !== year) {
        cells.push({ key: `empty-${week}-${weekday}`, empty: true, level: 0, today: false, title: '' })
        continue
      }
      const key = date.format('YYYY-MM-DD')
      const minutes = heatTotals.value.get(key) || 0
      const level = minutes === 0 ? 0 : minutes <= 60 ? 1 : minutes <= 180 ? 2 : minutes <= 300 ? 3 : 4
      cells.push({
        key,
        empty: false,
        level,
        today: key === todayKey,
        title: `${date.format('M月D日')} · ${fmtMin(minutes)}`,
      })
    }
  }
  return cells
})

const heatMonths = computed(() => {
  const year = heatYear.value
  const first = dayjs(`${year}-01-01`)
  const gridStart = first.subtract((first.day() + 6) % 7, 'day')
  return [0, 3, 6, 9].map((month) => {
    const date = dayjs(`${year}-${pad(month + 1)}-01`)
    const week = Math.round(date.diff(gridStart, 'day') / 7)
    return { label: `${month + 1}月`, column: week + 1 }
  })
})

// ---------- 周期翻页 ----------
function shiftAnchor(anchor: { value: Dayjs }, period: Period, step: number) {
  if (step > 0 && isCurrentPeriod(anchor.value, period)) return
  const date = anchor.value
  if (period === 'month') {
    anchor.value = date.add(step, 'month').startOf('month')
    return
  }
  anchor.value = period === 'week' ? date.add(step * 7, 'day') : date.add(step, 'day')
}

function shiftDetail(step: number) {
  shiftAnchor(detailAnchor, detailPeriod.value, step)
}

function shiftTrend(step: number) {
  shiftAnchor(trendAnchor, trendPeriod.value, step)
}

function shiftTimeline(step: number) {
  shiftAnchor(timelineAnchor, 'week', step)
}

function shiftBest(step: number) {
  shiftAnchor(bestAnchor, 'month', step)
}

// ---------- 加载 ----------
async function loadAll() {
  loading.value = true
  loadError.value = ''
  await Promise.all([
    run(loadOverview),
    run(loadMeta),
    run(loadDetail),
    run(loadRecent),
    run(loadTrend),
    run(loadTimeline),
    run(loadBest),
    run(loadHeat),
  ])
  loading.value = false
}

onMounted(loadAll)

watch([detailPeriod, detailAnchor], () => void run(loadDetail))
watch([trendPeriod, trendAnchor], () => void run(loadTrend))
watch(timelineAnchor, () => void run(loadTimeline))
watch(bestAnchor, () => void run(loadBest))
watch(heatYear, () => void run(loadHeat))
</script>

<template>
  <main class="page stats-page">
    <header class="page-head stats-head">
      <div>
        <h1>统计</h1>
        <p class="head-sub">专注时长、节奏与年度积累</p>
      </div>
    </header>

    <div class="page-body stats-body">
      <div v-if="loadError" class="card error-card">
        <p>{{ loadError }}</p>
        <button class="primary-btn" @click="loadAll">重试</button>
      </div>

      <template v-else>
        <section class="card stats-overview">
          <h4>概览</h4>
          <div class="stats-overview-grid">
            <div class="stats-overview-item">
              <strong>{{ overview.todayPomodoro }}</strong>
              <span>今日番茄</span>
              <small :class="hints.count.direction">{{ hints.count.text }}</small>
            </div>
            <div class="stats-overview-item">
              <strong>{{ overview.totalPomodoro }}</strong>
              <span>总番茄</span>
              <small>累计完成的专注次数</small>
            </div>
            <div class="stats-overview-item">
              <strong>{{ fmtMin(overview.todayMinutes) }}</strong>
              <span>今日专注时长</span>
              <small :class="hints.minutes.direction">{{ hints.minutes.text }}</small>
            </div>
            <div class="stats-overview-item">
              <strong>{{ fmtMin(overview.totalMinutes) }}</strong>
              <span>总专注时长</span>
              <small>所有专注记录合计</small>
            </div>
          </div>
        </section>

        <div class="stats-main-grid">
          <section class="card stats-detail">
            <div class="card-head">
              <h4>专注详情</h4>
              <div class="card-controls">
                <select v-model="groupMode" class="control-select" aria-label="分布方式">
                  <option value="list">按清单</option>
                  <option value="task">按任务</option>
                </select>
                <select v-model="detailPeriod" class="control-select" aria-label="详情周期">
                  <option value="day">日</option>
                  <option value="week">周</option>
                  <option value="month">月</option>
                </select>
                <div class="period-nav">
                  <button class="icon-btn" title="上一周期" aria-label="上一周期" @click="shiftDetail(-1)">‹</button>
                  <span>{{ detailLabel }}</span>
                  <button class="icon-btn" title="下一周期" aria-label="下一周期" :disabled="detailCurrent" @click="shiftDetail(1)">›</button>
                </div>
              </div>
            </div>
            <div class="detail-content">
              <div class="donut" :style="donutStyle">
                <div class="donut-hole">
                  <strong>{{ fmtMin(detailTotal) }}</strong>
                  <span>专注时长</span>
                </div>
              </div>
              <div v-if="detailItems.length" class="stats-distribution">
                <div v-for="item in detailItems" :key="item.key" class="stats-dist-row">
                  <div class="stats-dist-head">
                    <span class="stats-dist-name"><i :style="{ background: item.color }"></i>{{ item.name }}</span>
                    <span>{{ fmtMin(item.minutes) }} · {{ item.percent }}%</span>
                  </div>
                  <div class="dist-track">
                    <div class="dist-fill" :style="{ width: `${item.percent}%`, background: item.color }"></div>
                  </div>
                </div>
              </div>
              <p v-else class="empty">{{ detailLoading ? '加载中…' : '当前周期没有专注记录' }}</p>
            </div>
          </section>

          <section class="card stats-records">
            <div class="card-head">
              <h4>专注记录</h4>
              <span class="subtle">最近 {{ recentRecords.length }} 条</span>
            </div>
            <div v-if="recordGroups.length" class="record-groups stats-record-scroll">
              <section v-for="group in recordGroups" :key="group.key" class="record-group">
                <h5>{{ group.label }}</h5>
                <div v-for="item in group.items" :key="item.record.id" class="record-item">
                  <span class="record-rail">
                    <span class="record-dot" :style="{ background: item.color }"></span>
                  </span>
                  <div class="record-content">
                    <div class="record-line">
                      <span class="record-time">{{ item.range }}</span>
                      <span class="record-duration">{{ fmtMin(item.record.durationSec / 60) }}</span>
                    </div>
                    <div class="record-title">{{ item.record.taskTitle || '未关联任务' }}</div>
                    <div v-if="item.record.note" class="record-note">{{ item.record.note }}</div>
                  </div>
                </div>
              </section>
            </div>
            <p v-else class="empty">{{ loading ? '加载中…' : '暂无专注记录' }}</p>
          </section>
        </div>

        <div class="stats-two-col">
          <section class="card chart-card">
            <div class="card-head">
              <h4>专注趋势</h4>
              <div class="card-controls">
                <select v-model="trendPeriod" class="control-select" aria-label="趋势周期">
                  <option value="week">周</option>
                  <option value="month">月</option>
                </select>
                <div class="period-nav">
                  <button class="icon-btn" title="上一周期" aria-label="上一周期" @click="shiftTrend(-1)">‹</button>
                  <span>{{ trendLabel }}</span>
                  <button class="icon-btn" title="下一周期" aria-label="下一周期" :disabled="trendCurrent" @click="shiftTrend(1)">›</button>
                </div>
              </div>
            </div>
            <p class="chart-sub">每日平均：<strong>{{ fmtMin(trend.average) }}</strong></p>
            <svg class="line-chart" :viewBox="`0 0 ${trend.width} ${trend.height}`" role="img" aria-label="专注趋势图">
              <g v-for="grid in trend.grids" :key="grid.value">
                <line class="chart-grid-line" :x1="trend.left" :x2="trend.width - 12" :y1="grid.y" :y2="grid.y" />
                <text class="chart-axis-label" :x="trend.left - 8" :y="grid.y + 3" text-anchor="end">{{ grid.label }}</text>
              </g>
              <path v-if="trend.area" class="trend-area" :d="trend.area" />
              <polyline v-if="trend.points.length" class="trend-line" :points="trend.polyline" />
              <g v-for="point in trend.points" :key="point.key">
                <circle class="trend-point" :cx="point.x" :cy="point.y" r="3.5">
                  <title>{{ point.key }} · {{ fmtMin(point.minutes) }}</title>
                </circle>
              </g>
              <text
                v-for="(label, index) in trend.labels"
                :key="`label-${index}`"
                class="chart-axis-label"
                :x="label.x"
                :y="trend.height - 9"
                text-anchor="middle"
              >{{ label.text }}</text>
            </svg>
          </section>

          <section class="card chart-card">
            <div class="card-head">
              <h4>专注时间线</h4>
              <div class="period-nav">
                <button class="icon-btn" title="上一周" aria-label="上一周" @click="shiftTimeline(-1)">‹</button>
                <span>{{ timelineLabel }}</span>
                <button class="icon-btn" title="下一周" aria-label="下一周" :disabled="timelineCurrent" @click="shiftTimeline(1)">›</button>
              </div>
            </div>
            <div class="timeline-chart">
              <div class="timeline-hours">
                <span v-for="hour in [0, 4, 8, 12, 16, 20, 24]" :key="hour" :style="{ top: `${(hour / 24) * 100}%` }">
                  {{ pad(hour) }}:00
                </span>
              </div>
              <div class="timeline-grid">
                <div v-for="day in timelineDays" :key="day.key" class="timeline-day">
                  <div
                    v-for="event in day.events"
                    :key="event.id"
                    class="timeline-event"
                    :style="{ top: `${event.top}%`, height: `${event.height}%`, background: event.color }"
                    :title="event.title"
                  ></div>
                </div>
              </div>
            </div>
            <div class="timeline-labels">
              <span v-for="day in timelineDays" :key="day.key">{{ day.label }} {{ day.date }}</span>
            </div>
          </section>
        </div>

        <div class="stats-two-col">
          <section class="card chart-card">
            <div class="card-head">
              <h4>最佳专注时间</h4>
              <div class="period-nav">
                <button class="icon-btn" title="上一月" aria-label="上一月" @click="shiftBest(-1)">‹</button>
                <span>{{ bestLabel }}</span>
                <button class="icon-btn" title="下一月" aria-label="下一月" :disabled="bestCurrent" @click="shiftBest(1)">›</button>
              </div>
            </div>
            <svg class="bar-chart" :viewBox="`0 0 ${best.width} ${best.height}`" role="img" aria-label="最佳专注时间柱状图">
              <g v-for="grid in best.grids" :key="grid.value">
                <line class="chart-grid-line" :x1="best.left" :x2="best.left + best.innerW" :y1="grid.y" :y2="grid.y" />
                <text class="chart-axis-label" :x="best.left - 8" :y="grid.y + 3" text-anchor="end">{{ grid.label }}</text>
              </g>
              <g v-for="bar in best.bars" :key="bar.hour">
                <rect class="bar-track" :x="bar.x" :y="best.top" :width="bar.width" :height="best.innerH" rx="2" />
                <rect
                  v-if="bar.minutes"
                  class="bar-value"
                  :x="bar.x"
                  :y="bar.y"
                  :width="bar.width"
                  :height="bar.height"
                  rx="2"
                >
                  <title>{{ pad(bar.hour) }}:00 · {{ fmtMin(bar.minutes) }}</title>
                </rect>
                <text
                  v-if="bar.label"
                  class="chart-axis-label"
                  :x="bar.x + bar.width / 2"
                  :y="best.height - 8"
                  text-anchor="middle"
                >{{ bar.label }}</text>
              </g>
            </svg>
          </section>

          <section class="card chart-card heatmap-card">
            <div class="card-head">
              <h4>年度热力图</h4>
              <div class="period-nav">
                <button class="icon-btn" title="上一年" aria-label="上一年" @click="heatYear--">‹</button>
                <span>{{ heatYear }}</span>
                <button
                  class="icon-btn"
                  title="下一年"
                  aria-label="下一年"
                  :disabled="heatYear >= dayjs().year()"
                  @click="heatYear++"
                >›</button>
              </div>
            </div>
            <div class="heatmap-scroll">
              <div class="heatmap-months">
                <span v-for="month in heatMonths" :key="month.label" :style="{ gridColumn: month.column }">
                  {{ month.label }}
                </span>
              </div>
              <div class="heatmap-grid">
                <span
                  v-for="cell in heatCells"
                  :key="cell.key"
                  class="heat-cell"
                  :class="[`level-${cell.level}`, { empty: cell.empty, today: cell.today }]"
                  :title="cell.title"
                ></span>
              </div>
              <div class="heatmap-legend">
                <span><i class="heat-cell level-0"></i>0m</span>
                <span><i class="heat-cell level-1"></i>0-1h</span>
                <span><i class="heat-cell level-2"></i>1-3h</span>
                <span><i class="heat-cell level-3"></i>3-5h</span>
                <span><i class="heat-cell level-4"></i>&gt;5h</span>
              </div>
            </div>
          </section>
        </div>
      </template>
    </div>
  </main>
</template>

<style scoped>
.stats-page {
  min-height: 100%;
  background: var(--bg-soft);
}

.stats-head {
  align-items: center;
  padding-bottom: 18px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.stats-body {
  gap: 16px;
  padding: 16px 24px 32px;
  background: var(--bg-soft);
}

.stats-body > * {
  flex-shrink: 0;
}

.card h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.stats-overview {
  padding: 18px;
}

.stats-overview h4 {
  margin-bottom: 14px;
}

.stats-overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.stats-overview-item {
  min-width: 0;
  padding: 7px 24px;
  border-left: 1px solid var(--line);
}

.stats-overview-item:first-child {
  padding-left: 0;
  border-left: 0;
}

.stats-overview-item strong,
.stats-overview-item span,
.stats-overview-item small {
  display: block;
}

.stats-overview-item strong {
  margin-bottom: 7px;
  font-size: 27px;
  font-weight: 650;
  line-height: 1.1;
  letter-spacing: -0.025em;
  font-variant-numeric: tabular-nums;
}

.stats-overview-item span {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--ink-2);
}

.stats-overview-item small {
  overflow: hidden;
  font-size: 10.5px;
  color: var(--muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stats-overview-item small.up {
  color: #188977;
}

.stats-overview-item small.down {
  color: var(--danger);
}

.stats-main-grid,
.stats-two-col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.stats-main-grid {
  grid-template-columns: minmax(0, 1.55fr) minmax(310px, 0.9fr);
}

.stats-detail,
.stats-records {
  min-height: 356px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.card-head h4 {
  flex: 0 0 auto;
}

.card-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 7px;
}

.control-select {
  min-width: 62px;
  padding: 5px 8px;
  border: 1px solid var(--line);
  border-radius: 7px;
  outline: none;
  background: #fff;
  color: var(--ink-2);
  font-size: 11.5px;
}

.control-select:focus {
  border-color: var(--accent);
}

.period-nav {
  display: flex;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fff;
}

.period-nav .icon-btn {
  width: 26px;
  height: 26px;
  border-radius: 5px;
  font-size: 15px;
  line-height: 1;
  color: var(--ink-2);
}

.period-nav .icon-btn:disabled {
  opacity: 0.25;
  cursor: not-allowed;
}

.period-nav span {
  min-width: 72px;
  padding: 0 4px;
  font-size: 11.5px;
  color: var(--ink-2);
  text-align: center;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.detail-content {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  align-items: center;
  gap: 28px;
  min-height: 270px;
  padding: 8px 12px;
}

.donut {
  position: relative;
  display: grid;
  place-items: center;
  width: 180px;
  height: 180px;
  border-radius: 50%;
}

.donut-hole {
  position: absolute;
  inset: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.65);
}

.donut-hole strong {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.donut-hole span {
  font-size: 10.5px;
  color: var(--muted);
}

.stats-distribution {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 250px;
  padding-right: 4px;
  overflow: auto;
}

.stats-dist-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 7px;
  font-size: 11.5px;
  color: var(--ink-2);
}

.stats-dist-head > span:last-child {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.stats-dist-name {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stats-dist-name i {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
}

.dist-track {
  height: 6px;
  border-radius: 999px;
  background: var(--bg-soft);
  overflow: hidden;
}

.dist-fill {
  height: 100%;
  border-radius: 999px;
}

.stats-records {
  padding-right: 12px;
}

.stats-record-scroll {
  max-height: 292px;
  padding-right: 6px;
  overflow: auto;
}

.record-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.record-group h5 {
  margin: 0 0 2px 22px;
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
}

.record-item {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 7px 0;
}

.record-rail {
  position: relative;
  align-self: stretch;
  display: flex;
  justify-content: center;
  padding-top: 6px;
}

.record-rail::after {
  content: '';
  position: absolute;
  top: 17px;
  bottom: -15px;
  width: 1px;
  background: var(--line);
}

.record-item:last-child .record-rail::after {
  display: none;
}

.record-dot {
  position: relative;
  z-index: 1;
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px var(--line);
}

.record-content {
  min-width: 0;
}

.record-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 20px;
}

.record-time {
  font-size: 11.5px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.record-duration {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-2);
  white-space: nowrap;
}

.record-title {
  margin-top: 2px;
  overflow: hidden;
  color: var(--ink);
  font-size: 12.5px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-note {
  margin-top: 3px;
  overflow: hidden;
  font-size: 11px;
  color: var(--muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-card {
  min-width: 0;
  overflow: hidden;
}

.chart-sub {
  margin: -5px 0 7px;
  font-size: 11px;
  color: var(--muted);
}

.chart-sub strong {
  color: var(--accent);
  font-weight: 600;
}

.line-chart,
.bar-chart {
  display: block;
  width: 100%;
  height: auto;
  min-height: 210px;
  overflow: visible;
}

.chart-grid-line {
  stroke: #edf0f4;
  stroke-width: 1;
}

.chart-axis-label {
  fill: #9aa0a6;
  font-size: 10px;
}

.trend-area {
  fill: rgba(71, 114, 250, 0.09);
}

.trend-line {
  fill: none;
  stroke: var(--accent);
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.trend-point {
  fill: #fff;
  stroke: var(--accent);
  stroke-width: 2;
}

.bar-track {
  fill: #f1f3f6;
}

.bar-value {
  fill: var(--accent);
}

.timeline-chart {
  display: flex;
  height: 230px;
  margin-top: 6px;
}

.timeline-hours {
  position: relative;
  flex: 0 0 40px;
  height: 100%;
}

.timeline-hours span {
  position: absolute;
  right: 8px;
  color: var(--muted);
  font-size: 9px;
  line-height: 1;
  transform: translateY(-50%);
  white-space: nowrap;
}

.timeline-hours span:last-child {
  transform: translateY(-100%);
}

.timeline-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  height: 100%;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background-image: linear-gradient(to bottom, #edf0f4 1px, transparent 1px);
  background-size: 100% calc(100% / 6);
}

.timeline-day {
  position: relative;
  min-width: 0;
  border-left: 1px solid var(--line);
}

.timeline-event {
  position: absolute;
  left: 17%;
  width: 66%;
  min-height: 4px;
  border-radius: 3px;
  opacity: 0.86;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.3);
}

.timeline-labels {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  margin: 8px 0 0 40px;
}

.timeline-labels span {
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.heatmap-scroll {
  padding: 12px 2px 4px;
  overflow-x: auto;
}

.heatmap-months,
.heatmap-grid {
  min-width: 480px;
  width: 100%;
}

.heatmap-months {
  display: grid;
  grid-template-columns: repeat(53, minmax(6px, 1fr));
  gap: 3px;
  margin-bottom: 8px;
}

.heatmap-months span {
  grid-row: 1;
  color: var(--muted);
  font-size: 10px;
  white-space: nowrap;
}

.heatmap-grid {
  display: grid;
  grid-template-columns: repeat(53, minmax(6px, 1fr));
  grid-template-rows: repeat(7, 10px);
  grid-auto-flow: column;
  gap: 3px;
}

.heat-cell {
  display: block;
  width: 100%;
  height: 10px;
  border-radius: 2px;
  background: #edf0f5;
}

.heat-cell.empty {
  visibility: hidden;
}

.heat-cell.today {
  outline: 1px solid var(--accent);
  outline-offset: 1px;
}

.heat-cell.level-1 {
  background: #dbe5ff;
}

.heat-cell.level-2 {
  background: #a9beff;
}

.heat-cell.level-3 {
  background: #6f92fc;
}

.heat-cell.level-4 {
  background: #4772fa;
}

.heatmap-legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
  color: var(--muted);
  font-size: 10px;
}

.heatmap-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.heatmap-legend .heat-cell {
  width: 10px;
  flex: 0 0 10px;
}

.error-card {
  text-align: center;
}

.error-card .primary-btn {
  margin-top: 12px;
}

@media (max-width: 1180px) {
  .stats-main-grid {
    grid-template-columns: 1fr;
  }

  .stats-record-scroll {
    max-height: 330px;
  }
}

@media (max-width: 1024px) {
  .stats-two-col {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .stats-overview-item {
    padding-right: 14px;
    padding-left: 14px;
  }

  .detail-content {
    grid-template-columns: 160px minmax(0, 1fr);
    gap: 18px;
  }

  .donut {
    width: 160px;
    height: 160px;
  }
}

@media (max-width: 720px) {
  .stats-body {
    padding: 14px 14px 20px;
  }

  .stats-overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 20px;
  }

  .stats-overview-item:nth-child(3) {
    padding-left: 0;
    border-left: 0;
  }

  .stats-overview-item:nth-child(n + 3) {
    padding-top: 16px;
    border-top: 1px solid var(--line);
  }

  .detail-content {
    grid-template-columns: 140px minmax(0, 1fr);
    padding-right: 0;
    padding-left: 0;
  }

  .donut {
    width: 140px;
    height: 140px;
  }

  .donut-hole {
    inset: 20px;
  }

  .donut-hole strong {
    font-size: 17px;
  }
}

@media (max-width: 520px) {
  .card {
    padding: 14px;
  }

  .card-head {
    flex-direction: column;
    align-items: stretch;
  }

  .card-controls {
    justify-content: flex-start;
  }

  .stats-records .card-head {
    flex-direction: row;
    align-items: center;
  }

  .detail-content {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .stats-distribution {
    width: 100%;
  }

  .stats-overview-item strong {
    font-size: 23px;
  }

  .stats-overview-item {
    padding-right: 8px;
    padding-left: 12px;
  }

  .record-time {
    font-size: 10.5px;
  }

  .line-chart,
  .bar-chart {
    min-height: 180px;
  }

  .timeline-labels span {
    font-size: 9px;
  }
}
</style>
