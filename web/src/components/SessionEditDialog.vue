<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import dayjs from 'dayjs'
import {
  createSession,
  listHabits,
  listTasks,
  updateSession,
  type FocusHabit,
  type FocusTask,
  type SessionStatus,
  type SessionVO,
} from '../api/session'

type LinkType = 'none' | 'task' | 'habit'

const props = withDefaults(defineProps<{
  open: boolean
  session?: SessionVO | null
}>(), {
  session: null,
})

const emit = defineEmits<{
  close: []
  saved: [session: SessionVO]
}>()

const tasks = ref<FocusTask[]>([])
const habits = ref<FocusHabit[]>([])
const linkType = ref<LinkType>('none')
const taskId = ref<number | null>(null)
const habitId = ref<number | null>(null)
const customTitle = ref('')
const note = ref('')
const startedAt = ref('')
const endedAt = ref('')
const pausedSec = ref(0)
const status = ref<SessionStatus>('completed')
const saving = ref(false)
const error = ref('')
const linkLoadError = ref('')
let manualClientId = ''

function toDateTimeLocal(value: string): string {
  return dayjs(value).format('YYYY-MM-DDTHH:mm:ss')
}

/** 秒 → "1小时20分钟" / "25分钟" / "30秒" */
function formatHM(sec: number): string {
  const safe = Math.max(0, Math.floor(sec || 0))
  const h = Math.floor(safe / 3600)
  const m = Math.floor((safe % 3600) / 60)
  const s = safe % 60
  if (h > 0) return `${h}小时${m}分钟`
  if (m > 0) return s > 0 ? `${m}分${s}秒` : `${m}分钟`
  return `${s}秒`
}

/** 起止时间跨度（秒） */
const wallSec = computed(() => {
  const start = dayjs(startedAt.value)
  const end = dayjs(endedAt.value)
  if (!start.isValid() || !end.isValid() || end.valueOf() <= start.valueOf()) return 0
  return Math.floor((end.valueOf() - start.valueOf()) / 1000)
})

/** 专注时长 = 起止跨度 - 暂停，自动计算 */
const durationSec = computed(() => Math.max(0, wallSec.value - Math.max(0, pausedSec.value)))
const durationDisplay = computed(() => formatHM(durationSec.value))

function resetForm() {
  const session = props.session
  linkType.value = session?.taskId != null ? 'task' : session?.habitId != null ? 'habit' : 'none'
  taskId.value = session?.taskId ?? null
  habitId.value = taskId.value == null ? (session?.habitId ?? null) : null
  customTitle.value = session?.taskTitle ?? ''
  note.value = session?.note ?? ''
  pausedSec.value = session?.pausedSec ?? 0
  status.value = session?.status ?? 'completed'
  const end = session ? dayjs(session.endedAt) : dayjs().startOf('minute')
  const start = session ? dayjs(session.startedAt) : end.subtract(25, 'minute')
  startedAt.value = toDateTimeLocal(start.toISOString())
  endedAt.value = toDateTimeLocal(end.toISOString())
  manualClientId = crypto.randomUUID()
  error.value = ''
}

async function loadLinks() {
  linkLoadError.value = ''
  try {
    const [taskRows, habitRows] = await Promise.all([listTasks(), listHabits()])
    tasks.value = taskRows
    habits.value = habitRows
  } catch {
    tasks.value = []
    habits.value = []
    linkLoadError.value = '任务和习惯加载失败，可稍后重试'
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      resetForm()
      void loadLinks()
    }
  },
  { immediate: true },
)

function onLinkTypeChange() {
  taskId.value = null
  habitId.value = null
}

const associationTitle = computed(() => {
  if (taskId.value != null) {
    return tasks.value.find((task) => task.id === taskId.value)?.title
      ?? (props.session?.taskId === taskId.value ? props.session.taskTitle : '')
  }
  if (habitId.value != null) {
    return habits.value.find((habit) => habit.id === habitId.value)?.name
      ?? (props.session?.habitId === habitId.value ? props.session.taskTitle : '')
  }
  return customTitle.value.trim()
})

function close() {
  if (!saving.value) emit('close')
}

async function save() {
  error.value = ''
  if (linkType.value === 'task' && taskId.value == null) {
    error.value = '请选择要关联的任务'
    return
  }
  if (linkType.value === 'habit' && habitId.value == null) {
    error.value = '请选择要关联的习惯'
    return
  }

  const start = dayjs(startedAt.value)
  const end = dayjs(endedAt.value)
  if (!start.isValid() || !end.isValid() || !startedAt.value || !endedAt.value) {
    error.value = '请填写有效的开始和结束时间'
    return
  }
  if (start.valueOf() >= end.valueOf()) {
    error.value = '开始时间必须早于结束时间'
    return
  }
  if (!Number.isInteger(pausedSec.value) || pausedSec.value < 0) {
    error.value = '暂停时长必须是大于或等于 0 的整数秒'
    return
  }
  const spanSec = Math.floor((end.valueOf() - start.valueOf()) / 1000)
  const expectedDuration = spanSec - pausedSec.value
  if (expectedDuration < 1 || expectedDuration > 43200) {
    error.value = '扣除暂停后的专注时长需在 1—43200 秒之间'
    return
  }

  const linkedTaskId = linkType.value === 'task' ? taskId.value : null
  const linkedHabitId = linkType.value === 'habit' ? habitId.value : null
  const common = {
    taskId: linkedTaskId,
    habitId: linkedHabitId,
    taskTitle: associationTitle.value.trim().slice(0, 100),
    note: note.value.trim().slice(0, 2000),
    startedAt: start.toDate().toISOString(),
    endedAt: end.toDate().toISOString(),
    durationSec: durationSec.value,
    pausedSec: pausedSec.value,
  }

  saving.value = true
  try {
    const saved = props.session
      ? await updateSession(props.session.id, { ...common, version: props.session.version })
      : await createSession({
          ...common,
          clientId: manualClientId,
          mode: 'countup',
          plannedSec: null,
          status: status.value,
          source: 'manual',
        })
    emit('saved', saved)
    emit('close')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败，请重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="open" class="overlay" @click.self="close">
    <div class="dialog session-dialog" role="dialog" aria-modal="true">
      <h3>{{ session ? '编辑记录' : '补记专注' }}</h3>

      <form @submit.prevent="save">
        <div class="field">
          <label>关联</label>
          <div class="link-row">
            <select v-model="linkType" @change="onLinkTypeChange">
              <option value="none">不关联</option>
              <option value="task">任务</option>
              <option value="habit">习惯</option>
            </select>
            <select v-if="linkType === 'task'" v-model="taskId">
              <option :value="null" disabled>请选择任务</option>
              <option v-for="task in tasks" :key="task.id" :value="task.id">{{ task.title }}</option>
            </select>
            <select v-else-if="linkType === 'habit'" v-model="habitId">
              <option :value="null" disabled>请选择习惯</option>
              <option v-for="habit in habits" :key="habit.id" :value="habit.id">{{ habit.name }}</option>
            </select>
          </div>
          <div v-if="linkLoadError" class="link-error">{{ linkLoadError }}</div>
        </div>

        <div v-if="linkType === 'none'" class="field">
          <label>专注事情</label>
          <input v-model="customTitle" type="text" maxlength="100" placeholder="这次专注在做什么？（可选，不填则记为未关联）" />
        </div>

        <div class="field">
          <label>专注笔记</label>
          <textarea v-model="note" rows="3" maxlength="2000" placeholder="记录这阶段做了什么（可选）"></textarea>
        </div>

        <div class="time-grid">
          <div class="field">
            <label>开始时间</label>
            <input v-model="startedAt" type="datetime-local" step="1" required />
          </div>
          <div class="field">
            <label>结束时间</label>
            <input v-model="endedAt" type="datetime-local" step="1" required />
          </div>
          <div class="field">
            <label>专注时长</label>
            <div class="duration-display" :class="{ invalid: durationSec <= 0 }">{{ durationDisplay }}</div>
          </div>
          <div class="field">
            <label>暂停时长（秒）</label>
            <input v-model.number="pausedSec" type="number" min="0" step="1" required />
          </div>
        </div>

        <div v-if="!session" class="field">
          <label>状态</label>
          <select v-model="status">
            <option value="completed">完成</option>
            <option value="aborted">中断</option>
          </select>
        </div>

        <p v-if="error" class="error-text">{{ error }}</p>
        <div class="actions">
          <button type="button" class="btn" :disabled="saving" @click="close">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="saving">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.overlay .session-dialog {
  max-width: 560px;
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  text-align: left;
}

.overlay .session-dialog h3 {
  margin-bottom: 20px;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg);
  color: var(--text);
  font: inherit;
  font-size: 15px;
  padding: 10px 12px;
  outline: none;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  border-color: var(--primary);
}

.field textarea {
  resize: vertical;
  min-height: 76px;
}

.duration-display {
  display: flex;
  align-items: center;
  min-height: 43px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-soft, #f7f8fa);
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.duration-display.invalid {
  color: var(--muted);
  font-weight: 400;
}

.link-row {
  display: grid;
  grid-template-columns: minmax(110px, 0.7fr) minmax(0, 1.3fr);
  gap: 10px;
}

.link-row select:only-child {
  grid-column: 1 / -1;
}

.link-error {
  margin-top: 6px;
  color: var(--danger);
  font-size: 12px;
}

.time-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}

.overlay .session-dialog .error-text {
  margin: 0 0 16px;
  color: var(--danger);
  font-size: 13px;
}

@media (max-width: 560px) {
  .time-grid {
    grid-template-columns: 1fr;
  }
}
</style>
