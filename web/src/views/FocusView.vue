<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { useTimerStore } from '../stores/timer'
import {
  createPreset,
  deletePreset,
  getSummary,
  listHabits,
  listPresets,
  listSessions,
  listTasks,
  type FocusHabit,
  type FocusPreset,
  type FocusTask,
  type SessionVO,
  type SummaryVO,
} from '../api/session'
import { fmtClock, fmtDuration, todayStr } from '../utils/date'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import SessionEditDialog from '../components/SessionEditDialog.vue'

const RING_R = 124
const RING_C = 2 * Math.PI * RING_R
const DURATIONS = [15, 25, 45, 60]

const timer = useTimerStore()
const router = useRouter()

// ---------- 任务 / 习惯关联 ----------
const tasks = ref<FocusTask[]>([])
const habits = ref<FocusHabit[]>([])

async function loadAssociations() {
  try {
    const [taskRows, habitRows] = await Promise.all([listTasks(), listHabits()])
    tasks.value = taskRows
    habits.value = habitRows
  } catch {
    tasks.value = []
    habits.value = []
  }
}

const associationValue = computed(() => {
  if (timer.taskId != null) return `task:${timer.taskId}`
  if (timer.habitId != null) return `habit:${timer.habitId}`
  return ''
})

function onAssociationChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  timer.taskId = null
  timer.habitId = null
  timer.taskTitle = ''
  if (value.startsWith('task:')) {
    const id = Number(value.slice(5))
    timer.taskId = id
    timer.taskTitle = tasks.value.find((task) => task.id === id)?.title ?? ''
  } else if (value.startsWith('habit:')) {
    const id = Number(value.slice(5))
    timer.habitId = id
    timer.taskTitle = habits.value.find((habit) => habit.id === id)?.name ?? ''
  }
}

// ---------- 常用专注 ----------
const presets = ref<FocusPreset[]>([])
const presetName = ref('')
const savingPreset = ref(false)
const presetError = ref('')
const pendingPresetDelete = ref<FocusPreset | null>(null)
const deletingPreset = ref(false)

async function loadPresets() {
  try {
    presets.value = await listPresets()
  } catch {
    presets.value = []
  }
}

function applyPreset(preset: FocusPreset) {
  if (timer.phase !== 'idle') return
  timer.mode = preset.mode
  timer.presetSec = preset.plannedSec ?? 1500
  timer.taskId = preset.taskId
  timer.habitId = preset.taskId == null ? preset.habitId : null
  timer.taskTitle = timer.taskId != null
    ? (tasks.value.find((task) => task.id === timer.taskId)?.title ?? '')
    : timer.habitId != null
      ? (habits.value.find((habit) => habit.id === timer.habitId)?.name ?? '')
      : ''
  timer.note = preset.noteTemplate || ''
  presetError.value = ''
}

async function createPresetFromTimer(name: string) {
  savingPreset.value = true
  presetError.value = ''
  try {
    const saved = await createPreset({
      name: name.slice(0, 50),
      mode: timer.mode,
      plannedSec: timer.mode === 'countdown' ? timer.presetSec : null,
      taskId: timer.taskId,
      habitId: timer.habitId,
      noteTemplate: timer.note.trim().slice(0, 2000),
      sortOrder: Math.max(0, ...presets.value.map((preset) => preset.sortOrder)) + 1,
    })
    presets.value = [...presets.value, saved].sort((a, b) => a.sortOrder - b.sortOrder)
    presetName.value = ''
  } catch (e) {
    presetError.value = e instanceof Error ? e.message : '保存常用专注失败'
  } finally {
    savingPreset.value = false
  }
}

function quickSavePreset() {
  const name = timer.mode === 'countdown'
    ? `${Math.round(timer.presetSec / 60)} 分钟专注`
    : '正计时专注'
  if (presets.value.some((preset) => preset.name === name)) {
    presetError.value = '该常用专注已存在'
    return
  }
  void createPresetFromTimer(name)
}

async function saveCurrentPreset() {
  const name = presetName.value.trim()
  if (!name) {
    presetError.value = '请输入常用专注名称'
    return
  }
  await createPresetFromTimer(name)
}

async function confirmDeletePreset() {
  if (!pendingPresetDelete.value) return
  deletingPreset.value = true
  presetError.value = ''
  try {
    await deletePreset(pendingPresetDelete.value.id)
    presets.value = presets.value.filter((preset) => preset.id !== pendingPresetDelete.value?.id)
    pendingPresetDelete.value = null
  } catch (e) {
    presetError.value = e instanceof Error ? e.message : '删除常用专注失败'
  } finally {
    deletingPreset.value = false
  }
}

function presetMeta(preset: FocusPreset): string {
  const mode = preset.mode === 'countup'
    ? '正计时'
    : `倒计时 ${Math.round((preset.plannedSec ?? 0) / 60)} 分钟`
  const title = preset.taskId != null
    ? tasks.value.find((task) => task.id === preset.taskId)?.title
    : preset.habitId != null
      ? habits.value.find((habit) => habit.id === preset.habitId)?.name
      : ''
  return `${mode} · ${title || '不关联'}`
}

// ---------- 概览数据 ----------
const todaySummary = ref<SummaryVO | null>(null)
const totalSummary = ref<SummaryVO | null>(null)
const recentSessions = ref<SessionVO[]>([])
const overviewLoading = ref(true)

async function loadTodaySummary() {
  try {
    const day = todayStr()
    todaySummary.value = await getSummary(day, day)
  } catch {
    todaySummary.value = null
  }
}

async function loadTotalSummary() {
  try {
    totalSummary.value = await getSummary()
  } catch {
    totalSummary.value = null
  }
}

async function loadRecentSessions() {
  try {
    recentSessions.value = await listSessions({ limit: 20 })
  } catch {
    recentSessions.value = []
  }
}

async function loadOverview() {
  overviewLoading.value = true
  await Promise.all([loadTodaySummary(), loadTotalSummary(), loadRecentSessions()])
  overviewLoading.value = false
}

const recentGroups = computed(() => {
  const groups: { date: string; label: string; items: SessionVO[] }[] = []
  const map = new Map<string, { date: string; label: string; items: SessionVO[] }>()
  for (const session of recentSessions.value) {
    const key = session.sessionDate
    let group = map.get(key)
    if (!group) {
      group = { date: key, label: dayLabel(key), items: [] }
      map.set(key, group)
      groups.push(group)
    }
    group.items.push(session)
  }
  return groups
})

function dayLabel(date: string): string {
  const day = dayjs(date)
  if (day.isSame(dayjs(), 'day')) return '今天'
  if (day.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天'
  return day.format('M月D日')
}

function timeRange(session: SessionVO): string {
  return `${dayjs(session.startedAt).format('HH:mm')} - ${dayjs(session.endedAt).format('HH:mm')}`
}

function openRecord(session: SessionVO) {
  editingSession.value = session
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
  editingSession.value = null
}

function onRecordSaved() {
  closeEditor()
  void loadOverview()
}

onMounted(() => {
  void loadAssociations()
  void loadPresets()
  void loadOverview()
})

// ---------- 计时控制 ----------
const canStart = computed(() => timer.phase === 'idle' && !timer.saving)

const customMinutes = computed({
  get: () => Math.round(timer.presetSec / 60),
  set: (value: number) => {
    if (timer.phase !== 'idle') return
    const minutes = Math.max(1, Math.min(180, Math.round(Number(value) || 25)))
    timer.presetSec = minutes * 60
  },
})

function switchMode(mode: 'countup' | 'countdown') {
  if (timer.phase !== 'idle' || timer.mode === mode) return
  timer.mode = mode
}

function pickDuration(minutes: number) {
  if (timer.phase !== 'idle') return
  timer.presetSec = minutes * 60
}

function startFocus() {
  timer.start()
}

/** 环形进度：倒计时按已完成比例，正计时按每小时循环 */
const ringProgress = computed(() => {
  if (timer.mode === 'countup') return (timer.elapsedMs % 3600000) / 3600000
  const total = timer.presetSec * 1000
  if (!total) return 0
  return Math.min(1, timer.elapsedMs / total)
})

const phaseText = computed(() => {
  if (timer.phase === 'running') return timer.mode === 'countup' ? '正计时中' : '专注中'
  if (timer.phase === 'paused') return '已暂停'
  return '准备开始'
})

const targetLabel = computed(() => {
  if (timer.mode === 'countdown') return `目标 ${Math.round(timer.presetSec / 60)} 分钟`
  return timer.phase === 'running' ? '正在记录专注时长' : '自由计时，结束时记录'
})

// ---------- 弹窗状态 ----------
const settingsOpen = ref(false)
const showAbortConfirm = ref(false)
const showSettle = ref(false)
const showSaveFailed = ref(false)
const showManualEntry = ref(false)
const editorOpen = ref(false)
const editingSession = ref<SessionVO | null>(null)

// 结算弹窗由 lastSettle 驱动（含自动归零、restore 补记）
watch(
  () => timer.lastSettle,
  (v) => {
    if (v) {
      showSettle.value = true
      void loadOverview()
    }
  },
)

// 恢复结果驱动保存失败弹窗
watch(
  () => timer.restoreResult,
  (kind) => {
    if (kind === 'save-failed') showSaveFailed.value = true
  },
)

/** 结束按钮：倒计时未归零 → 确认后记中断；正计时 → 直接完成 */
function onEndClick() {
  if (timer.saving || timer.finishing) return
  if (timer.mode === 'countdown' && timer.remainingMs > 0) {
    showAbortConfirm.value = true
  } else {
    void doFinish('completed')
  }
}

async function doFinish(status: 'completed' | 'aborted') {
  showAbortConfirm.value = false
  const r = await timer.finish(status)
  if (!r.ok) showSaveFailed.value = true
}

async function onRetrySave() {
  const r = await timer.retrySave()
  if (r.ok) showSaveFailed.value = false
}

function onDiscardSave() {
  showSaveFailed.value = false
  timer.discardPendingSave()
}

function onAgain() {
  timer.clearSettle()
  showSettle.value = false
  timer.start()
}

function onGoRecords() {
  timer.clearSettle()
  showSettle.value = false
  router.push('/records')
}

function onManualSaved() {
  showManualEntry.value = false
  void loadOverview()
}
</script>

<template>
  <main class="page focus-page">
    <header class="page-head">
      <div>
        <h1>番茄专注</h1>
        <p class="head-sub">把注意力留给当下，每一次专注都有迹可循</p>
      </div>
      <div class="toolbar">
        <button class="ghost-btn small" @click="router.push('/stats')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 20V10M12 20V4M19 20v-7" /></svg>
          专注统计
        </button>
        <button class="ghost-btn small" @click="showManualEntry = true">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
          补记专注
        </button>
      </div>
    </header>

    <div class="page-body focus-body">
      <div class="focus-layout">
        <div class="card focus-main">
          <div class="focus-tabs">
            <button
              :class="{ active: timer.mode === 'countdown' }"
              :disabled="timer.phase !== 'idle'"
              @click="switchMode('countdown')"
            >番茄计时</button>
            <button
              :class="{ active: timer.mode === 'countup' }"
              :disabled="timer.phase !== 'idle'"
              @click="switchMode('countup')"
            >正计时</button>
          </div>

          <div class="timer-ring">
            <svg viewBox="0 0 280 280">
              <circle class="ring-bg" cx="140" cy="140" :r="RING_R" />
              <circle
                class="ring-fg"
                :class="{ paused: timer.phase === 'paused' }"
                cx="140"
                cy="140"
                :r="RING_R"
                :style="{
                  strokeDasharray: RING_C,
                  strokeDashoffset: RING_C * (1 - ringProgress),
                }"
              />
            </svg>
            <div class="timer-center">
              <span class="timer-state">{{ phaseText }}</span>
              <span class="timer-time">{{ fmtClock(timer.displayMs) }}</span>
              <span class="timer-target">{{ targetLabel }}</span>
            </div>
          </div>

          <div v-if="timer.mode === 'countdown'" class="duration-row">
            <button
              v-for="minutes in DURATIONS"
              :key="minutes"
              class="duration-chip"
              :class="{ active: customMinutes === minutes }"
              :disabled="timer.phase !== 'idle'"
              @click="pickDuration(minutes)"
            >{{ minutes }} 分钟</button>
            <label class="custom-duration">
              自定义
              <input
                v-model.number="customMinutes"
                type="number"
                min="1"
                max="180"
                :disabled="timer.phase !== 'idle'"
              />
              分钟
            </label>
          </div>

          <div class="timer-actions">
            <button
              v-if="timer.phase === 'idle'"
              class="primary-btn lg"
              :disabled="!canStart"
              @click="startFocus"
            >
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 4l12 8-12 8z" /></svg>
              开始专注
            </button>
            <button
              v-else-if="timer.phase === 'running'"
              class="primary-btn lg"
              :disabled="timer.saving"
              @click="timer.pause()"
            >
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M9 5v14M15 5v14" /></svg>
              暂停
            </button>
            <button
              v-else
              class="primary-btn lg"
              :disabled="timer.saving"
              @click="timer.resume()"
            >
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 4l12 8-12 8z" /></svg>
              继续专注
            </button>
            <button
              v-if="timer.phase !== 'idle'"
              class="ghost-btn lg"
              :disabled="timer.saving"
              @click="onEndClick"
            >{{ timer.saving ? '保存中…' : '结束并记录' }}</button>
          </div>

          <div class="preset-row">
            <span class="preset-label">常用专注</span>
            <button
              v-for="preset in presets"
              :key="preset.id"
              class="preset-chip"
              :class="{ active: preset.mode === timer.mode && preset.plannedSec === timer.presetSec }"
              :title="presetMeta(preset)"
              :disabled="timer.phase !== 'idle'"
              @click="applyPreset(preset)"
            >
              {{ preset.name }}<template v-if="preset.mode === 'countdown'"> · {{ Math.round((preset.plannedSec ?? 0) / 60) }}′</template>
            </button>
            <button class="preset-chip ghost" :disabled="savingPreset" @click="quickSavePreset">+ 保存当前</button>
            <button class="text-btn" @click="settingsOpen = true">管理</button>
          </div>
          <p v-if="presetError && !settingsOpen" class="preset-error">{{ presetError }}</p>

          <div class="focus-fields">
            <label class="field">
              <span>关联任务 / 习惯</span>
              <select
                :value="associationValue"
                :disabled="timer.phase !== 'idle'"
                @change="onAssociationChange"
              >
                <option value="">不关联</option>
                <optgroup v-if="tasks.length" label="任务">
                  <option v-for="task in tasks" :key="`task-${task.id}`" :value="`task:${task.id}`">{{ task.title }}</option>
                </optgroup>
                <optgroup v-if="habits.length" label="习惯">
                  <option v-for="habit in habits" :key="`habit-${habit.id}`" :value="`habit:${habit.id}`">{{ habit.name }}</option>
                </optgroup>
              </select>
            </label>
            <label class="field">
              <span>专注事情</span>
              <input
                v-model="timer.taskTitle"
                type="text"
                maxlength="100"
                :disabled="timer.phase !== 'idle' || timer.taskId != null || timer.habitId != null"
                :placeholder="timer.taskId != null || timer.habitId != null ? '已关联，标题随关联项' : '不关联时，直接填写这次专注在做什么'"
              />
            </label>
            <label class="field note-full">
              <span>专注笔记</span>
              <textarea
                v-model="timer.note"
                rows="2"
                maxlength="2000"
                placeholder="记录这次专注的内容，结束后会写入记录"
              ></textarea>
            </label>
          </div>
        </div>

        <aside class="focus-aside">
          <div class="focus-overview">
            <div class="overview-item">
              <span>今日番茄</span>
              <strong>{{ todaySummary?.pomodoroCount ?? 0 }}</strong>
            </div>
            <div class="overview-item">
              <span>今日专注时长</span>
              <strong>{{ fmtDuration(todaySummary?.totalSec ?? 0) }}</strong>
            </div>
            <div class="overview-item">
              <span>总番茄</span>
              <strong>{{ totalSummary?.pomodoroCount ?? 0 }}</strong>
            </div>
            <div class="overview-item">
              <span>总专注时长</span>
              <strong>{{ fmtDuration(totalSummary?.totalSec ?? 0) }}</strong>
            </div>
          </div>

          <div class="card focus-records">
            <div class="records-head">
              <h4>专注记录 <span class="subtle">最近 20 条</span></h4>
              <div class="records-actions">
                <button class="icon-btn" title="补记专注" @click="showManualEntry = true">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
                </button>
                <button class="text-btn" @click="router.push('/records')">全部</button>
              </div>
            </div>

            <div v-if="recentGroups.length" class="record-groups">
              <section v-for="group in recentGroups" :key="group.date" class="record-group">
                <h5>{{ group.label }}</h5>
                <div
                  v-for="session in group.items"
                  :key="session.id"
                  class="record-item"
                  role="button"
                  tabindex="0"
                  @click="openRecord(session)"
                  @keyup.enter="openRecord(session)"
                >
                  <span class="record-rail"><span class="record-dot"></span></span>
                  <div class="record-content">
                    <div class="record-line">
                      <span class="record-time">{{ timeRange(session) }}</span>
                      <span class="record-duration">{{ fmtDuration(session.durationSec) }}</span>
                    </div>
                    <div class="record-title">{{ session.taskTitle || '未关联' }}</div>
                    <div v-if="session.note" class="record-note">{{ session.note }}</div>
                  </div>
                  <div class="record-actions">
                    <button class="link-btn" @click.stop="openRecord(session)">改</button>
                  </div>
                </div>
              </section>
            </div>
            <p v-else-if="overviewLoading" class="empty">加载中…</p>
            <p v-else class="empty">暂无记录，开始一次专注试试。</p>
          </div>
        </aside>
      </div>
    </div>

    <!-- 常用专注管理 -->
    <div v-if="settingsOpen" class="overlay" @click.self="settingsOpen = false">
      <section class="dialog settings-dialog" role="dialog" aria-modal="true" aria-label="常用专注管理">
        <header class="settings-head">
          <h3>常用专注</h3>
          <button class="icon-btn" aria-label="关闭" @click="settingsOpen = false">×</button>
        </header>

        <p v-if="timer.phase !== 'idle'" class="muted settings-hint">计时进行中，结束或暂停后可切换常用配置。</p>

        <div class="settings-section">
          <label>保存当前配置</label>
          <div class="preset-save">
            <input v-model="presetName" class="input" maxlength="50" placeholder="输入名称，例如：深度写作" @keyup.enter="saveCurrentPreset" />
            <button class="btn" :disabled="savingPreset" @click="saveCurrentPreset">
              {{ savingPreset ? '保存中…' : '保存' }}
            </button>
          </div>
          <p v-if="presetError" class="preset-error">{{ presetError }}</p>
          <div v-if="presets.length" class="preset-list">
            <div v-for="preset in presets" :key="preset.id" class="saved-preset">
              <button class="preset-apply" @click="applyPreset(preset); settingsOpen = false">
                <span class="preset-name">{{ preset.name }}</span>
                <span class="muted">{{ presetMeta(preset) }}</span>
              </button>
              <button class="btn-ghost preset-delete" @click="pendingPresetDelete = preset">删除</button>
            </div>
          </div>
          <div v-else class="muted preset-empty">暂无常用专注</div>
        </div>
      </section>
    </div>

    <!-- 倒计时提前结束二次确认 -->
    <ConfirmDialog
      :open="showAbortConfirm"
      title="结束这次专注？"
      message="还未完成，结束会记为中断，时长仍会保存。"
      confirm-text="结束"
      cancel-text="继续专注"
      danger
      @confirm="doFinish('aborted')"
      @cancel="showAbortConfirm = false"
    />

    <!-- 结算弹窗（不响应遮罩点击，避免结束确认的那次点击顺带关掉弹窗） -->
    <div v-if="showSettle && timer.lastSettle" class="overlay">
      <div class="dialog">
        <h3>{{ timer.lastSettle.status === 'completed' ? '专注完成' : '专注中断' }}</h3>
        <div class="settle-digits timer-digits">{{ fmtDuration(timer.lastSettle.durationSec) }}</div>
        <p class="muted">{{ timer.lastSettle.taskTitle || '未关联' }}</p>
        <div class="actions">
          <button class="btn" @click="onAgain">再来一次</button>
          <button class="btn btn-primary" @click="onGoRecords">查看记录</button>
        </div>
      </div>
    </div>

    <!-- 保存失败：重试 / 放弃 -->
    <ConfirmDialog
      :open="showSaveFailed"
      title="保存失败"
      message="本次专注数据已保留，可以重试保存。"
      confirm-text="重试"
      cancel-text="放弃"
      :loading="timer.saving"
      @confirm="onRetrySave"
      @cancel="onDiscardSave"
    />

    <SessionEditDialog
      :open="showManualEntry"
      @close="showManualEntry = false"
      @saved="onManualSaved"
    />

    <SessionEditDialog
      :open="editorOpen"
      :session="editingSession"
      @close="closeEditor"
      @saved="onRecordSaved"
    />

    <ConfirmDialog
      :open="!!pendingPresetDelete"
      title="删除这个常用专注？"
      :message="pendingPresetDelete?.name ?? ''"
      confirm-text="删除"
      danger
      :loading="deletingPreset"
      @confirm="confirmDeletePreset"
      @cancel="pendingPresetDelete = null"
    />
  </main>
</template>

<style scoped>
.focus-page .page-body {
  gap: 16px;
  padding: 0 24px 28px;
  background: var(--bg-soft);
}

.focus-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 16px;
  align-items: start;
  width: 100%;
}

.focus-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  min-height: 640px;
  justify-content: center;
  padding: 28px;
}

.focus-tabs {
  display: flex;
  padding: 3px;
  border-radius: 9px;
  background: #f1f3f6;
}

.focus-tabs button {
  padding: 5px 18px;
  border-radius: 7px;
  font-size: 12.5px;
  color: var(--ink-2);
}

.focus-tabs button.active {
  background: #fff;
  color: var(--accent);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.focus-tabs button:disabled {
  cursor: default;
  opacity: 0.7;
}

.timer-ring {
  position: relative;
  width: min(300px, 70vw);
  height: min(300px, 70vw);
}

.timer-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-bg {
  fill: none;
  stroke: #eef0f5;
  stroke-width: 12;
}

.ring-fg {
  fill: none;
  stroke: var(--accent);
  stroke-width: 12;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.25s linear;
}

.ring-fg.paused {
  stroke: #f5a623;
}

.timer-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.timer-state {
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--muted);
}

.timer-time {
  font-size: 48px;
  font-weight: 300;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.timer-target {
  font-size: 11px;
  color: var(--muted);
}

.duration-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.duration-chip {
  padding: 5px 12px;
  border-radius: 999px;
  background: #f4f6f9;
  color: var(--ink-2);
  font-size: 12px;
}

.duration-chip.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.duration-chip:disabled {
  opacity: 0.5;
  cursor: default;
}

.custom-duration {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--muted);
}

.custom-duration input {
  width: 54px;
  padding: 4px 6px;
  border: 1px solid var(--line);
  border-radius: 6px;
  font-size: 12px;
  background: #fff;
}

.timer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  max-width: 640px;
}

.preset-label {
  font-size: 11px;
  color: var(--muted);
}

.preset-chip {
  padding: 4px 10px;
  border-radius: 999px;
  background: #f7f8fa;
  color: var(--ink-2);
  font-size: 11.5px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preset-chip.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.preset-chip.ghost {
  border: 1px dashed var(--line);
}

.preset-chip:disabled {
  opacity: 0.55;
  cursor: default;
}

.preset-error {
  margin: 0;
  color: var(--danger);
  font-size: 12px;
}

.focus-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 12px;
  width: 100%;
  max-width: 660px;
}

.focus-fields .note-full {
  grid-column: 1 / -1;
}

.focus-fields input:disabled {
  background: var(--bg-soft, #f7f8fa);
  color: var(--muted);
  cursor: not-allowed;
}

.focus-aside {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.focus-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.overview-item {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  min-height: 88px;
  padding: 15px 17px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 14px;
}

.overview-item span {
  font-size: 12px;
  color: var(--muted);
}

.overview-item strong {
  font-size: 24px;
  font-weight: 650;
  line-height: 1;
  letter-spacing: -0.025em;
  font-variant-numeric: tabular-nums;
}

.focus-records {
  max-height: 540px;
  padding: 18px 12px 14px 18px;
  overflow: auto;
}

.records-head {
  position: sticky;
  top: -18px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -2px 0 6px;
  padding: 2px 6px 8px 0;
  background: #fff;
}

.records-head h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.subtle {
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
}

.records-actions {
  display: flex;
  align-items: center;
  gap: 2px;
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
  grid-template-columns: 14px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
  padding: 7px 0;
  border-radius: 8px;
  cursor: pointer;
}

.record-item:hover {
  background: #f8f9fb;
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
  background: var(--accent);
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

.record-actions {
  display: flex;
  align-items: center;
  gap: 1px;
  opacity: 0.55;
  transition: opacity 0.15s;
}

.record-item:hover .record-actions,
.record-actions:focus-within {
  opacity: 1;
}

.link-btn {
  padding: 3px;
  font-size: 11px;
  color: var(--accent);
}

/* 常用专注管理 */
.settings-dialog {
  max-width: 480px;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  text-align: left;
}

.settings-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.settings-head h3 {
  margin: 0;
  font-size: 16px;
}

.settings-hint {
  margin: 0 0 14px;
}

.settings-section > label {
  display: block;
  margin-bottom: 7px;
  color: var(--ink-2);
  font-size: 12px;
}

.preset-save {
  display: flex;
  gap: 8px;
}

.preset-save .input {
  min-width: 0;
}

.preset-save .btn {
  flex-shrink: 0;
}

.preset-list {
  max-height: 240px;
  margin-top: 10px;
  overflow-y: auto;
}

.saved-preset {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line);
}

.saved-preset:last-child {
  border-bottom: none;
}

.preset-apply {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 0;
  padding: 4px 8px;
  border-radius: 8px;
  background: transparent;
  color: var(--ink);
  text-align: left;
}

.preset-apply:hover {
  background: var(--accent-soft);
}

.preset-name {
  max-width: 100%;
  overflow: hidden;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preset-delete {
  flex-shrink: 0;
  padding: 6px 8px;
  color: var(--danger);
}

.preset-empty {
  margin-top: 10px;
}

.settle-digits {
  margin: 12px 0 4px;
  color: var(--accent);
  font-size: 34px;
  font-weight: 600;
}

@media (max-width: 1180px) {
  .focus-layout {
    grid-template-columns: 1fr;
  }

  .focus-records {
    max-height: none;
  }
}

@media (max-width: 720px) {
  .focus-page .page-body {
    padding: 0 16px 24px;
  }

  .focus-main {
    min-height: 0;
    padding: 20px 14px;
  }

  .focus-fields {
    grid-template-columns: 1fr;
  }

  .timer-actions {
    flex-direction: column;
    width: 100%;
  }

  .timer-actions .primary-btn,
  .timer-actions .ghost-btn {
    width: 100%;
  }
}
</style>
