<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useTimerStore } from '../stores/timer'
import { getSummary, listTaskTitles, type SummaryVO } from '../api/session'
import { fmtClock, fmtDuration, todayStr } from '../utils/date'
import TimerRing from '../components/TimerRing.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'

const timer = useTimerStore()
const router = useRouter()

// ---------- 任务历史候选 ----------
const taskTitles = ref<string[]>([])
const showCandidates = ref(false)

async function loadTaskTitles() {
  try {
    taskTitles.value = await listTaskTitles(10)
  } catch {
    taskTitles.value = []
  }
}

function selectTask(title: string) {
  timer.taskTitle = title
  showCandidates.value = false
}

// ---------- 今日统计条 ----------
const todaySummary = ref<SummaryVO | null>(null)

async function loadTodaySummary() {
  try {
    const day = todayStr()
    todaySummary.value = await getSummary(day, day)
  } catch {
    todaySummary.value = null
  }
}

onMounted(() => {
  void loadTaskTitles()
  void loadTodaySummary()
})

// ---------- 计时控制 ----------
const PRESETS = [
  { sec: 900, label: '15' },
  { sec: 1500, label: '25' },
  { sec: 2700, label: '45' },
]

function startFocus() {
  timer.start()
}

const canStart = computed(() => timer.phase === 'idle' && !timer.saving)

/** 环形进度：倒计时剩余比例，正计时常亮满环 */
const ringProgress = computed(() => {
  if (timer.mode === 'countup') return 1
  return timer.remainingMs / (timer.presetSec * 1000)
})

const phaseText = computed(() => {
  if (timer.phase === 'running') return '专注中'
  if (timer.phase === 'paused') return '已暂停'
  return '准备开始'
})

// ---------- 弹窗状态 ----------
const showAbortConfirm = ref(false) // 倒计时提前结束二次确认（R3）
const showSettle = ref(false) // 结算弹窗
const showRestoreDecision = ref(false) // 正计时恢复三选一（R9）
const showSaveFailed = ref(false) // 保存失败：重试 / 放弃

// 结算弹窗由 lastSettle 驱动（含自动归零、restore 补记）
watch(
  () => timer.lastSettle,
  (v) => {
    if (v) {
      showSettle.value = true
      void loadTaskTitles()
      void loadTodaySummary()
    }
  },
)

// 恢复结果驱动三选一 / 保存失败弹窗
watch(
  () => timer.restoreResult,
  (kind) => {
    if (kind === 'needs-decision') showRestoreDecision.value = true
    if (kind === 'save-failed') showSaveFailed.value = true
  },
)

/** 结束按钮：倒计时未归零 → 确认后记中断；正计时 → 直接完成（R1/R3） */
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
  if (r.ok && !r.skipped) {
    // 成功后结算弹窗由 lastSettle watcher 打开
  } else if (r.ok && r.skipped) {
    // R4：小于 1 秒不保存
  } else {
    showSaveFailed.value = true
  }
}

async function onRetrySave() {
  const r = await timer.retrySave()
  if (r.ok) {
    showSaveFailed.value = false
  }
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

// R9 三选一
function onResumeRestore() {
  showRestoreDecision.value = false
  timer.confirmResume()
}

function onFinishRestore() {
  showRestoreDecision.value = false
  void doFinish('completed')
}

function onDiscardRestore() {
  showRestoreDecision.value = false
  timer.discardRestore()
}
</script>

<template>
  <main class="focus-page">
    <!-- 空闲态：配置 + 开始 -->
    <section v-if="timer.phase === 'idle'" class="card idle-card">
      <div class="mode-switch">
        <button class="seg" :class="{ active: timer.mode === 'countup' }" @click="timer.mode = 'countup'">
          正计时
        </button>
        <button class="seg" :class="{ active: timer.mode === 'countdown' }" @click="timer.mode = 'countdown'">
          倒计时
        </button>
      </div>

      <div v-if="timer.mode === 'countdown'" class="presets">
        <button
          v-for="p in PRESETS"
          :key="p.sec"
          class="preset"
          :class="{ active: timer.presetSec === p.sec }"
          @click="timer.presetSec = p.sec"
        >
          {{ p.label }}
        </button>
      </div>

      <div class="timer-digits preview">{{ fmtClock(timer.displayMs) }}</div>

      <div class="field task-field">
        <label>任务</label>
        <div class="task-input">
          <input
            v-model="timer.taskTitle"
            placeholder="如：数学-高数第三章"
            maxlength="100"
            @focus="showCandidates = true"
            @blur="showCandidates = false"
          />
          <button
            v-if="taskTitles.length"
            class="btn-ghost caret"
            type="button"
            tabindex="-1"
            @click="showCandidates = !showCandidates"
          >
            ▾
          </button>
          <ul v-if="showCandidates && taskTitles.length" class="candidates">
            <li
              v-for="t in taskTitles"
              :key="t"
              @mousedown.prevent="selectTask(t)"
            >
              {{ t }}
            </li>
          </ul>
        </div>
      </div>

      <div class="field">
        <label>事项</label>
        <input v-model="timer.note" placeholder="这阶段要做什么（可选）" maxlength="200" />
      </div>

      <button class="btn btn-primary btn-block" :disabled="!canStart" @click="startFocus">
        开始专注
      </button>

      <div class="today-bar muted">
        <template v-if="todaySummary">
          今日 {{ fmtDuration(todaySummary.totalSec) }} · {{ todaySummary.pomodoroCount }} 个番茄
        </template>
        <template v-else>今日 0m · 0 个番茄</template>
      </div>
    </section>

    <!-- 运行 / 暂停态 -->
    <section v-else class="card running-card">
      <TimerRing :progress="ringProgress">
        <div class="timer-digits" :class="{ paused: timer.phase === 'paused' }">
          {{ fmtClock(timer.displayMs) }}
        </div>
        <div class="muted">{{ phaseText }}</div>
      </TimerRing>

      <div class="current-task">
        <div class="ct-title">{{ timer.taskTitle || '未填写任务' }}</div>
        <div v-if="timer.note" class="muted">{{ timer.note }}</div>
      </div>

      <div class="run-actions">
        <button
          v-if="timer.phase === 'running'"
          class="btn"
          :disabled="timer.saving"
          @click="timer.pause()"
        >
          暂停
        </button>
        <button
          v-else
          class="btn btn-primary"
          :disabled="timer.saving"
          @click="timer.resume()"
        >
          继续
        </button>
        <button class="btn btn-danger" :disabled="timer.saving" @click="onEndClick">
          {{ timer.saving ? '保存中…' : '结束' }}
        </button>
      </div>
    </section>

    <!-- R3：倒计时提前结束二次确认 -->
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

    <!-- 结算弹窗 -->
    <div v-if="showSettle && timer.lastSettle" class="overlay" @click.self="onGoRecords">
      <div class="dialog">
        <h3>{{ timer.lastSettle.status === 'completed' ? '专注完成' : '专注中断' }}</h3>
        <div class="settle-digits timer-digits">
          {{ fmtDuration(timer.lastSettle.durationSec) }}
        </div>
        <p class="muted">{{ timer.lastSettle.taskTitle || '未填写任务' }}</p>
        <div class="actions">
          <button class="btn" @click="onAgain">再来一次</button>
          <button class="btn btn-primary" @click="onGoRecords">查看记录</button>
        </div>
      </div>
    </div>

    <!-- R9：正计时恢复三选一 -->
    <div v-if="showRestoreDecision" class="overlay">
      <div class="dialog">
        <h3>有未结束的正计时</h3>
        <p class="muted">上次页面关闭前有一次正计时未结束，要继续吗？</p>
        <div class="actions three">
          <button class="btn" @click="onResumeRestore">继续</button>
          <button class="btn btn-primary" @click="onFinishRestore">结束保存</button>
          <button class="btn btn-ghost" @click="onDiscardRestore">丢弃</button>
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
  </main>
</template>

<style scoped>
.idle-card {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.mode-switch {
  display: inline-flex;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 3px;
  margin-bottom: 16px;
}

.seg {
  border: none;
  background: transparent;
  color: var(--text-2);
  font-size: 14px;
  padding: 7px 18px;
  border-radius: 8px;
  cursor: pointer;
}

.seg.active {
  background: var(--primary);
  color: #fff;
}

.presets {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}

.preset {
  width: 56px;
  height: 44px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.preset.active {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-weak);
}

.timer-digits.preview {
  margin: 8px 0 20px;
  color: var(--text);
}

.task-field {
  width: 100%;
  margin-top: 8px;
}

.idle-card .field {
  width: 100%;
}

.task-input {
  position: relative;
}

.task-input .caret {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  padding: 4px 8px;
}

.candidates {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  margin: 0;
  padding: 4px;
  list-style: none;
  z-index: 20;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.candidates li {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.candidates li:hover {
  background: var(--primary-weak);
}

.today-bar {
  margin-top: 16px;
}

/* 运行态 */
.running-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 24px;
}

.timer-digits.paused {
  color: var(--text-2);
}

.current-task {
  margin: 20px 0 8px;
  text-align: center;
}

.ct-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.run-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.run-actions .btn {
  min-width: 108px;
}

.settle-digits {
  margin: 12px 0 4px;
  color: var(--primary);
}

.actions.three {
  flex-wrap: nowrap;
}
</style>
