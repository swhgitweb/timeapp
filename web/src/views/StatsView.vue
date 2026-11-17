<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import {
  getByTask,
  getDaily,
  getSummary,
  getYearly,
  type DailyVO,
  type SummaryVO,
  type TaskStatVO,
} from '../api/session'
import { fmtDuration, todayStr, weekStartStr } from '../utils/date'

type RangeKey = 'today' | 'week' | 'month' | 'year'

const RANGE_OPTIONS: { value: RangeKey; label: string }[] = [
  { value: 'today', label: '今日' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
  { value: 'year', label: '本年度' },
]

const CHART_COLORS = [
  'var(--chart-1)',
  'var(--chart-2)',
  'var(--chart-3)',
  'var(--chart-4)',
  'var(--chart-5)',
  'var(--chart-more)',
]

const today = todayStr()
const range = ref<RangeKey>('week')

const summary = ref<SummaryVO | null>(null)
const daily = ref<DailyVO[]>([])
const byTask = ref<TaskStatVO[]>([])
const loading = ref(false)
const loadError = ref('')

function rangeBounds(key: RangeKey): { from: string; to: string } {
  const now = dayjs()
  if (key === 'today') {
    return { from: now.format('YYYY-MM-DD'), to: now.format('YYYY-MM-DD') }
  }
  if (key === 'week') {
    return { from: weekStartStr(), to: now.format('YYYY-MM-DD') }
  }
  if (key === 'year') {
    return { from: now.startOf('year').format('YYYY-MM-DD'), to: now.format('YYYY-MM-DD') }
  }
  return { from: now.startOf('month').format('YYYY-MM-DD'), to: now.format('YYYY-MM-DD') }
}

// 自增序号丢弃过期响应，避免快速切范围时数据错位
let seq = 0
async function load() {
  const mine = ++seq
  const key = range.value
  const { from, to } = rangeBounds(key)
  loading.value = true
  loadError.value = ''
  try {
    const trendRequest = key === 'year' ? getYearly(dayjs().year()) : getDaily(from, to)
    const [s, d, t] = await Promise.all([getSummary(from, to), trendRequest, getByTask(from, to, 20)])
    if (mine !== seq) return
    summary.value = s
    daily.value = d
    byTask.value = t
  } catch (e) {
    if (mine !== seq) return
    loadError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    if (mine === seq) loading.value = false
  }
}

onMounted(load)
watch(range, load)

// ---------- 指标卡 ----------
const metrics = computed(() => [
  {
    label: '总时长',
    value: fmtDuration(summary.value?.totalSec ?? 0),
    hint: '专注累计',
    tone: 'var(--chart-1)',
  },
  {
    label: '番茄数',
    value: String(summary.value?.pomodoroCount ?? 0),
    hint: '完成倒计时',
    tone: 'var(--chart-4)',
  },
  {
    label: '中断',
    value: String(summary.value?.abortedCount ?? 0),
    hint: '提前结束',
    tone: 'var(--chart-5)',
  },
  {
    label: '活跃天数',
    value: String(summary.value?.activeDays ?? 0),
    hint: '有记录的天',
    tone: 'var(--chart-3)',
  },
])

// ---------- 柱状图（纯 CSS） ----------
const peakSec = computed(() => Math.max(0, ...daily.value.map((d) => d.totalSec)))
const maxSec = computed(() => Math.max(1, peakSec.value))
const dense = computed(() => daily.value.length > 10)

function barHeight(totalSec: number): string {
  if (totalSec <= 0) return '3px'
  return `${Math.max(6, Math.round((totalSec / maxSec.value) * 100))}%`
}

function weekdayLabel(dateStr: string): string {
  return ['日', '一', '二', '三', '四', '五', '六'][dayjs(dateStr).day()]
}

function barLabel(d: DailyVO): string {
  if (range.value === 'year') return `${dayjs(d.date).month() + 1}月`
  return dense.value ? String(dayjs(d.date).date()) : weekdayLabel(d.date)
}

function showLabel(index: number, d: DailyVO): boolean {
  if (range.value === 'year' || !dense.value) return true
  const day = dayjs(d.date).date()
  return index === 0 || index === daily.value.length - 1 || day % 5 === 0
}

function isCurrentBar(d: DailyVO): boolean {
  return range.value === 'year'
    ? dayjs(d.date).format('YYYY-MM') === dayjs().format('YYYY-MM')
    : d.date === today
}

// ---------- 任务分布 ----------
interface Slice {
  label: string
  totalSec: number
  color: string
  percent: number
}

const slices = computed<Slice[]>(() => {
  const rows = byTask.value
  const total = rows.reduce((sum, r) => sum + r.totalSec, 0)
  if (total <= 0) return []
  const list: Slice[] = rows.slice(0, 5).map((r, i) => ({
    label: r.taskTitle || '未填写任务',
    totalSec: r.totalSec,
    color: CHART_COLORS[i],
    percent: (r.totalSec / total) * 100,
  }))
  const restSec = rows.slice(5).reduce((sum, r) => sum + r.totalSec, 0)
  if (restSec > 0) {
    list.push({
      label: '其他',
      totalSec: restSec,
      color: CHART_COLORS[5],
      percent: (restSec / total) * 100,
    })
  }
  return list
})

const donutTotal = computed(() => slices.value.reduce((sum, s) => sum + s.totalSec, 0))

const donutStyle = computed(() => {
  if (!slices.value.length) return {}
  let acc = 0
  const parts = slices.value.map((s) => {
    const start = acc
    acc += s.percent
    return `${s.color} ${start}% ${acc}%`
  })
  return { background: `conic-gradient(${parts.join(', ')})` }
})
</script>

<template>
  <main class="page stats-page">
    <header class="page-head">
      <div>
        <h1>统计</h1>
        <p class="head-sub">回顾你的专注节奏，找到最适合自己的状态</p>
      </div>
      <div class="segmented">
        <button
          v-for="opt in RANGE_OPTIONS"
          :key="opt.value"
          :class="{ active: range === opt.value }"
          @click="range = opt.value"
        >
          {{ opt.label }}
        </button>
      </div>
    </header>

    <div class="page-body stats-body">
    <div v-if="loadError" class="card error-card">
      <p>{{ loadError }}</p>
      <button class="btn" @click="load">重试</button>
    </div>

    <template v-else>
      <div class="card stats-overview">
        <div v-for="m in metrics" :key="m.label" class="overview-cell">
          <span>{{ m.label }}</span>
          <strong>{{ m.value }}</strong>
          <span class="hint">{{ m.hint }}</span>
        </div>
      </div>

      <section class="card panel">
        <div class="panel-head">
          <h3>{{ range === 'year' ? '每月专注' : '每日专注' }}</h3>
          <span class="muted">峰值 {{ fmtDuration(peakSec) }}</span>
        </div>
        <div class="bars-wrap">
          <div class="grid-lines" aria-hidden="true">
            <span></span><span></span><span></span>
          </div>
          <div class="bars" :class="{ dense }">
            <div v-for="(d, i) in daily" :key="d.date" class="bar-col">
              <div class="bar-tip">
                {{ fmtDuration(d.totalSec) }}
                <template v-if="d.pomodoroCount"> · {{ d.pomodoroCount }} 番茄</template>
              </div>
              <div class="bar-track">
                <div
                  class="bar"
                  :class="{ 'is-today': isCurrentBar(d), 'is-zero': d.totalSec <= 0 }"
                  :style="{ height: barHeight(d.totalSec) }"
                  :title="`${d.date} ${fmtDuration(d.totalSec)}`"
                ></div>
              </div>
              <span class="bar-label" :class="{ 'is-today': isCurrentBar(d) }">
                {{ showLabel(i, d) ? barLabel(d) : '' }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <div class="split">
        <section class="card panel">
          <div class="panel-head">
            <h3>任务分布</h3>
            <span class="muted">按专注时长</span>
          </div>
          <div v-if="!slices.length" class="empty-chart muted">
            {{ loading ? '加载中…' : '这段时间还没有记录' }}
          </div>
          <div v-else class="donut-wrap">
            <div class="donut" :style="donutStyle">
              <div class="donut-hole">
                <span class="donut-total">{{ fmtDuration(donutTotal) }}</span>
                <span class="muted">总时长</span>
              </div>
            </div>
            <ul class="legend">
              <li v-for="s in slices" :key="s.label">
                <span class="dot" :style="{ background: s.color }"></span>
                <span class="legend-name">{{ s.label }}</span>
                <span class="legend-pct muted">{{ s.percent.toFixed(0) }}%</span>
              </li>
            </ul>
          </div>
        </section>

        <section class="card panel">
          <div class="panel-head">
            <h3>任务排行</h3>
            <span class="muted">Top 5</span>
          </div>
          <div v-if="!slices.length" class="empty-chart muted">
            {{ loading ? '加载中…' : '暂无数据' }}
          </div>
          <ol v-else class="rank">
            <li v-for="(s, i) in slices" :key="s.label">
              <span class="rank-no">{{ i + 1 }}</span>
              <div class="rank-main">
                <div class="rank-row">
                  <span class="rank-name">{{ s.label }}</span>
                  <span class="rank-val">{{ fmtDuration(s.totalSec) }}</span>
                </div>
                <div class="rank-bar">
                  <div class="rank-fill" :style="{ width: `${s.percent}%`, background: s.color }"></div>
                </div>
              </div>
            </li>
          </ol>
        </section>
      </div>
    </template>
    </div>
  </main>
</template>

<style scoped>
.stats-page {
  min-height: 100%;
}

.stats-body {
  gap: 16px;
  padding: 0 24px 28px;
  background: var(--bg-soft);
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 0;
  overflow: hidden;
}

.overview-cell {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
  padding: 18px 20px;
  border-right: 1px solid var(--line);
}

.overview-cell:last-child {
  border-right: 0;
}

.overview-cell > span {
  color: var(--muted);
  font-size: 12px;
}

.overview-cell strong {
  color: var(--ink);
  font-size: 24px;
  font-weight: 650;
  line-height: 1.1;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.overview-cell .hint {
  color: var(--muted);
  font-size: 11px;
}

.card {
  padding: 20px 22px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}


/* ---------- 柱状图 ---------- */
.bars-wrap {
  position: relative;
}

.grid-lines {
  position: absolute;
  inset: 10px 0 22px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.grid-lines span {
  display: block;
  border-top: 1px dashed var(--border);
  opacity: 0.65;
}

.bars {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  height: 172px;
  padding-top: 10px;
}

.bars.dense {
  gap: 4px;
}

.bar-col {
  position: relative;
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.bar-track {
  width: 100%;
  max-width: 36px;
  flex: 1;
  display: flex;
  align-items: flex-end;
  border-radius: 8px;
  overflow: hidden;
}

.bars.dense .bar-track {
  max-width: 100%;
}

.bar {
  width: 100%;
  background: var(--accent);
  border-radius: 5px 5px 0 0;
  transition: height 0.3s ease;
}

.bar.is-today {
  background: #2f5be0;
}

.bar.is-zero {
  background: var(--line);
}

.bar-label {
  height: 16px;
  font-size: 11px;
  color: var(--muted);
  line-height: 16px;
  white-space: nowrap;
}

.bar-label.is-today {
  color: var(--accent);
  font-weight: 600;
}

.bar-tip {
  position: absolute;
  top: -4px;
  left: 50%;
  transform: translate(-50%, -100%);
  background: var(--text);
  color: var(--surface);
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 6px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
  z-index: 2;
}

.bar-col:hover .bar-tip {
  opacity: 1;
}

/* ---------- 双栏 ---------- */
.split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.donut-wrap {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.donut {
  position: relative;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  flex-shrink: 0;
}

.donut-hole {
  position: absolute;
  inset: 26px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.donut-total {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.legend {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  min-width: 150px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.legend li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-pct {
  font-variant-numeric: tabular-nums;
}

.rank {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rank li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.rank-no {
  width: 18px;
  font-size: 13px;
  color: var(--text-2);
  line-height: 18px;
  font-variant-numeric: tabular-nums;
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 6px;
}

.rank-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-val {
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.rank-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--bg-soft);
  overflow: hidden;
}

.rank-fill {
  height: 100%;
  border-radius: 999px;
}

.empty-chart {
  padding: 48px 0;
  text-align: center;
}

.error-card {
  text-align: center;
}

.error-card .btn {
  margin-top: 12px;
}

@media (max-width: 760px) {
  .stats-body {
    padding: 0 16px 24px;
  }

  .stats-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-cell:nth-child(2n) {
    border-right: 0;
  }

  .overview-cell:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }

  .split {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .bars.dense {
    gap: 3px;
  }

  .bar-tip {
    display: none;
  }

  .card {
    padding: 16px;
  }

  .overview-cell {
    padding: 15px 16px;
  }

  .overview-cell strong {
    font-size: 20px;
  }
}
</style>
