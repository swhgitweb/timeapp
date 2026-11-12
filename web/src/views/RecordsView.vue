<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import {
  deleteSession,
  getDaily,
  getSummary,
  listSessions,
  type DailyVO,
  type SessionVO,
  type SummaryVO,
} from '../api/session'
import { fmtDateCN, fmtDuration, sevenDaysAgoStr, todayStr, weekStartStr } from '../utils/date'
import SessionItem from '../components/SessionItem.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'

// ---------- 数据 ----------
const todaySummary = ref<SummaryVO | null>(null)
const weekSummary = ref<SummaryVO | null>(null)
const daily = ref<DailyVO[]>([])
const sessions = ref<SessionVO[]>([])
const loading = ref(true)
const loadError = ref('')

async function loadAll() {
  loading.value = true
  loadError.value = ''
  try {
    const today = todayStr()
    const [t, w, d, list] = await Promise.all([
      getSummary(today, today),
      getSummary(weekStartStr(), today),
      getDaily(sevenDaysAgoStr(), today),
      listSessions({ limit: 100 }),
    ])
    todaySummary.value = t
    weekSummary.value = w
    daily.value = d
    sessions.value = list
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)

// ---------- 近 7 天柱状图（纯 CSS，不引图表库） ----------
const maxDailySec = computed(() => Math.max(1, ...daily.value.map((d) => d.totalSec)))

function barHeight(totalSec: number): string {
  if (totalSec <= 0) return '4px'
  const pct = Math.max(6, Math.round((totalSec / maxDailySec.value) * 100))
  return `${pct}%`
}

function weekdayLabel(dateStr: string): string {
  const names = ['日', '一', '二', '三', '四', '五', '六']
  return names[dayjs(dateStr).day()]
}

// ---------- 列表按日分组 ----------
const groups = computed(() => {
  const today = todayStr()
  const yesterday = dayjs().subtract(1, 'day').format('YYYY-MM-DD')
  const map = new Map<string, SessionVO[]>()
  for (const s of sessions.value) {
    const arr = map.get(s.sessionDate) ?? []
    arr.push(s)
    map.set(s.sessionDate, arr)
  }
  return [...map.entries()].map(([date, items]) => ({
    date,
    label: date === today ? '今天' : date === yesterday ? '昨天' : fmtDateCN(date),
    items,
  }))
})

const isEmpty = computed(() => !loading.value && !loadError.value && sessions.value.length === 0)

// ---------- 删除（软删除，二次确认） ----------
const pendingRemove = ref<SessionVO | null>(null)
const removing = ref(false)

function askRemove(session: SessionVO) {
  pendingRemove.value = session
}

async function confirmRemove() {
  if (!pendingRemove.value) return
  removing.value = true
  try {
    await deleteSession(pendingRemove.value.id)
    pendingRemove.value = null
    await loadAll()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    removing.value = false
  }
}
</script>

<template>
  <main class="records-page">
    <div v-if="loadError" class="card error-card">
      <p>{{ loadError }}</p>
      <button class="btn" @click="loadAll">重试</button>
    </div>

    <template v-else>
      <!-- 顶部三个数字 -->
      <div class="stat-row">
        <div class="stat card">
          <div class="stat-num">{{ fmtDuration(todaySummary?.totalSec ?? 0) }}</div>
          <div class="muted">今日</div>
        </div>
        <div class="stat card">
          <div class="stat-num">{{ fmtDuration(weekSummary?.totalSec ?? 0) }}</div>
          <div class="muted">本周</div>
        </div>
        <div class="stat card">
          <div class="stat-num">{{ todaySummary?.pomodoroCount ?? 0 }}</div>
          <div class="muted">今日番茄</div>
        </div>
      </div>

      <!-- 近 7 天 -->
      <div class="card chart-card">
        <div class="chart-title">近 7 天</div>
        <div class="bars">
          <div v-for="d in daily" :key="d.date" class="bar-col">
            <div class="bar-track">
              <div class="bar" :style="{ height: barHeight(d.totalSec) }" :title="fmtDuration(d.totalSec)"></div>
            </div>
            <span class="bar-label muted">{{ weekdayLabel(d.date) }}</span>
          </div>
        </div>
      </div>

      <!-- 记录列表 -->
      <div v-if="loading" class="muted empty-hint">加载中…</div>

      <div v-else-if="isEmpty" class="card empty-card">
        <p class="muted">还没有记录，去专注一次吧</p>
        <RouterLink to="/" class="btn btn-primary">去专注</RouterLink>
      </div>

      <div v-else class="card list-card">
        <div v-for="g in groups" :key="g.date" class="group">
          <div class="group-label muted">{{ g.label }}</div>
          <SessionItem
            v-for="s in g.items"
            :key="s.id"
            :session="s"
            @remove="askRemove"
          />
        </div>
      </div>
    </template>

    <!-- 删除确认 -->
    <ConfirmDialog
      :open="!!pendingRemove"
      title="删除这条记录？"
      :message="pendingRemove ? `${pendingRemove.taskTitle || '未填写任务'} · ${fmtDuration(pendingRemove.durationSec)}` : ''"
      confirm-text="删除"
      danger
      :loading="removing"
      @confirm="confirmRemove"
      @cancel="pendingRemove = null"
    />
  </main>
</template>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat {
  padding: 16px;
  text-align: center;
}

.stat-num {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  margin-bottom: 2px;
}

.chart-card {
  margin-bottom: 16px;
}

.chart-title {
  font-weight: 600;
  margin-bottom: 16px;
}

.bars {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.bar-track {
  height: 96px;
  width: 100%;
  max-width: 40px;
  display: flex;
  align-items: flex-end;
  background: var(--bg);
  border-radius: 6px;
  overflow: hidden;
}

.bar {
  width: 100%;
  background: var(--primary);
  border-radius: 6px 6px 0 0;
  transition: height 0.3s ease;
}

.bar-label {
  font-size: 12px;
}

.group-label {
  padding: 8px 4px 4px;
  font-weight: 500;
}

.empty-card {
  text-align: center;
  padding: 40px 24px;
}

.empty-card .btn {
  margin-top: 12px;
}

.error-card {
  text-align: center;
}

.error-card .btn {
  margin-top: 12px;
}
</style>
