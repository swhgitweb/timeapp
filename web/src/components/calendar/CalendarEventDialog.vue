<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezonePlugin from 'dayjs/plugin/timezone'
import ConfirmDialog from '../ConfirmDialog.vue'
import {
  COMMON_TIMEZONES,
  createCalendarEvent,
  deleteCalendarEvent,
  updateCalendarEvent,
  type CalendarEvent,
  type CalendarEventPayload,
  type CalendarHabit,
  type CalendarTask,
  type EditableCalendarKind,
} from '../../api/calendar'

dayjs.extend(utc)
dayjs.extend(timezonePlugin)

const props = defineProps<{
  open: boolean
  event: CalendarEvent | null
  initialDate: string
  tasks: CalendarTask[]
  habits: CalendarHabit[]
}>()

const emit = defineEmits<{ close: []; saved: []; manageData: [] }>()
const localTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'
const kind = ref<EditableCalendarKind>('task')
const taskId = ref('')
const habitId = ref('')
const title = ref('')
const note = ref('')
const startDate = ref('')
const startTime = ref('09:00')
const endDate = ref('')
const endTime = ref('10:00')
const allDay = ref(false)
const timezone = ref(localTimezone)
const rrule = ref('')
const color = ref('#3b6ef6')
const saving = ref(false)
const deleting = ref(false)
const confirmDelete = ref(false)
const error = ref('')

const readOnly = computed(() => !!props.event && (props.event.readOnly || props.event.source === 'ics'))
const editingId = computed(() => props.event ? props.event.eventId ?? props.event.id : null)
const timezoneOptions = computed(() => [...new Set([props.event?.timezone, localTimezone, ...COMMON_TIMEZONES].filter(Boolean))] as string[])
const repeatOptions = [
  { value: '', label: '不重复' },
  { value: 'FREQ=DAILY', label: '每天' },
  { value: 'FREQ=WEEKLY', label: '每周' },
  { value: 'FREQ=MONTHLY', label: '每月' },
  { value: 'FREQ=YEARLY', label: '每年' },
]
const customRule = computed(() => !!rrule.value && !repeatOptions.some((option) => option.value === rrule.value))
const activeHabits = computed(() => props.habits.filter((habit) => !habit.archived || habit.id === props.event?.habitId))

function reset() {
  if (!props.open) return
  error.value = ''
  confirmDelete.value = false
  const event = props.event
  kind.value = event?.kind === 'ics'
    ? 'anniversary'
    : event && ['task', 'habit', 'anniversary', 'course'].includes(event.kind)
      ? event.kind as EditableCalendarKind
      : 'task'
  taskId.value = event?.taskId ? String(event.taskId) : ''
  habitId.value = event?.habitId ? String(event.habitId) : ''
  title.value = event?.title ?? ''
  note.value = event?.note ?? ''
  allDay.value = event?.allDay ?? false
  timezone.value = event?.timezone || localTimezone
  rrule.value = event?.rrule || ''
  color.value = event?.color && /^#[0-9a-fA-F]{6}$/.test(event.color) ? event.color : '#3b6ef6'
  if (event) {
    const start = dayjs.tz(event.startAt, timezone.value)
    const end = dayjs.tz(event.endAt, timezone.value)
    startDate.value = start.format('YYYY-MM-DD')
    startTime.value = start.format('HH:mm')
    endDate.value = (allDay.value ? end.subtract(1, 'day') : end).format('YYYY-MM-DD')
    endTime.value = end.format('HH:mm')
  } else {
    const date = dayjs(props.initialDate || dayjs()).format('YYYY-MM-DD')
    startDate.value = date
    endDate.value = date
    startTime.value = '09:00'
    endTime.value = '10:00'
  }
}

watch(() => [props.open, props.event?.occurrenceId, props.initialDate], reset, { immediate: true })

function zonedInstant(date: string, time: string, zone: string): string {
  const zoned = dayjs.tz(`${date} ${time}`, zone)
  return dayjs(`${date}T${time}:00`).utcOffset(zoned.utcOffset(), true).toISOString()
}

function close() {
  if (!saving.value && !deleting.value) emit('close')
}

async function save() {
  error.value = ''
  if (kind.value === 'task' && !taskId.value) { error.value = '请选择关联任务'; return }
  if (kind.value === 'habit' && !habitId.value) { error.value = '请选择关联习惯'; return }
  if (['anniversary', 'course'].includes(kind.value) && !title.value.trim()) { error.value = '请填写日程标题'; return }
  if (!startDate.value || !endDate.value) { error.value = '请选择起止日期'; return }
  if (allDay.value && endDate.value < startDate.value) { error.value = '结束日期不能早于开始日期'; return }

  const startAt = zonedInstant(startDate.value, allDay.value ? '00:00' : startTime.value, timezone.value)
  const finalEndDate = allDay.value ? dayjs(endDate.value).add(1, 'day').format('YYYY-MM-DD') : endDate.value
  const endAt = zonedInstant(finalEndDate, allDay.value ? '00:00' : endTime.value, timezone.value)
  if (dayjs(startAt).valueOf() >= dayjs(endAt).valueOf()) { error.value = '结束时间必须晚于开始时间'; return }

  const payload: CalendarEventPayload = {
    kind: kind.value,
    taskId: kind.value === 'task' ? Number(taskId.value) : null,
    habitId: kind.value === 'habit' ? Number(habitId.value) : null,
    title: title.value.trim() || undefined,
    note: note.value.trim(),
    startAt,
    endAt,
    allDay: allDay.value,
    timezone: timezone.value,
    rrule: rrule.value || null,
    color: color.value,
    version: props.event?.version,
  }
  saving.value = true
  try {
    if (editingId.value) await updateCalendarEvent(editingId.value, payload)
    else await createCalendarEvent(payload)
    emit('saved')
    emit('close')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败，请重试'
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!editingId.value || !props.event) return
  deleting.value = true
  error.value = ''
  try {
    await deleteCalendarEvent(editingId.value, props.event.version)
    confirmDelete.value = false
    emit('saved')
    emit('close')
  } catch (cause) {
    confirmDelete.value = false
    error.value = cause instanceof Error ? cause.message : '删除失败，请重试'
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div v-if="open" class="overlay" @click.self="close">
    <section class="dialog event-dialog" role="dialog" aria-modal="true" :aria-label="readOnly ? '查看订阅日程' : editingId ? '编辑日程' : '新增日程'">
      <header class="dialog-head">
        <div>
          <h3>{{ readOnly ? '查看订阅日程' : editingId ? '编辑日程' : '新增日程' }}</h3>
          <p v-if="readOnly">ICS 订阅内容只读，请在订阅来源处修改。</p>
          <p v-else-if="event?.rrule">重复日程的修改将作用于整个系列。</p>
        </div>
        <button class="close-btn" aria-label="关闭" @click="close">×</button>
      </header>

      <form @submit.prevent="save">
        <fieldset :disabled="readOnly || saving">
          <div class="form-grid">
            <div class="field">
              <label>类型</label>
              <input v-if="readOnly" class="input" value="订阅日程" disabled />
              <select v-else v-model="kind" class="input" :disabled="!!editingId">
                <option value="task">任务</option><option value="habit">习惯</option>
                <option value="anniversary">纪念日</option><option value="course">课程</option>
              </select>
            </div>
            <div v-if="kind === 'task'" class="field">
              <label>关联任务</label>
              <select v-model="taskId" class="input" required>
                <option value="">请选择任务</option>
                <option v-for="task in tasks" :key="task.id" :value="String(task.id)">{{ task.title }}{{ task.completed ? '（已完成）' : '' }}</option>
              </select>
              <button v-if="!tasks.length" type="button" class="link-btn" @click="emit('manageData')">先创建任务</button>
            </div>
            <div v-else-if="kind === 'habit'" class="field">
              <label>关联习惯</label>
              <select v-model="habitId" class="input" required>
                <option value="">请选择习惯</option>
                <option v-for="habit in activeHabits" :key="habit.id" :value="String(habit.id)">{{ habit.name }}</option>
              </select>
              <button v-if="!activeHabits.length" type="button" class="link-btn" @click="emit('manageData')">先创建习惯</button>
            </div>
            <div v-else class="field">
              <label>标题</label>
              <input v-model="title" class="input" maxlength="200" required placeholder="请输入标题" />
            </div>
            <div v-if="kind === 'task' || kind === 'habit'" class="field wide">
              <label>标题（可选）</label>
              <input v-model="title" class="input" maxlength="200" placeholder="留空则使用关联任务或习惯的名称" />
            </div>
          </div>

          <label class="check-line"><input v-model="allDay" type="checkbox" /> 全天日程</label>
          <div class="form-grid time-grid-fields">
            <div class="field">
              <label>开始{{ allDay ? '日期' : '时间' }}</label>
              <div class="date-time"><input v-model="startDate" class="input" type="date" required /><input v-if="!allDay" v-model="startTime" class="input" type="time" required /></div>
            </div>
            <div class="field">
              <label>结束{{ allDay ? '日期（含当天）' : '时间' }}</label>
              <div class="date-time"><input v-model="endDate" class="input" type="date" required /><input v-if="!allDay" v-model="endTime" class="input" type="time" required /></div>
            </div>
            <div class="field">
              <label>时区</label>
              <select v-model="timezone" class="input"><option v-for="zone in timezoneOptions" :key="zone" :value="zone">{{ zone.replaceAll('_', ' ') }}</option></select>
            </div>
            <div class="field">
              <label>重复</label>
              <select v-model="rrule" class="input">
                <option v-for="option in repeatOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                <option v-if="customRule" :value="rrule">保留当前自定义规则</option>
              </select>
            </div>
            <div class="field color-field">
              <label>颜色</label>
              <div><input v-model="color" type="color" aria-label="日程颜色" /><span>{{ color }}</span></div>
            </div>
          </div>
          <div class="field">
            <label>备注</label>
            <textarea v-model="note" class="input" rows="3" maxlength="2000" placeholder="补充地点、说明等信息"></textarea>
          </div>
        </fieldset>
        <p v-if="error" class="form-error">{{ error }}</p>
        <footer class="dialog-actions">
          <button v-if="editingId && !readOnly" type="button" class="btn danger-text" :disabled="saving" @click="confirmDelete = true">删除</button>
          <span class="action-space"></span>
          <button type="button" class="btn" @click="close">{{ readOnly ? '关闭' : '取消' }}</button>
          <button v-if="!readOnly" type="submit" class="btn btn-primary" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        </footer>
      </form>
    </section>
    <ConfirmDialog :open="confirmDelete" title="删除这项日程？" message="重复日程会删除整个系列，此操作无法撤销。" confirm-text="删除" danger :loading="deleting" @confirm="remove" @cancel="confirmDelete = false" />
  </div>
</template>

<style scoped>
.overlay .event-dialog { max-width: 640px; max-height: calc(100vh - 32px); overflow-y: auto; padding: 22px; text-align: left; }
.dialog-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.dialog-head h3 { margin: 0; }
.dialog-head p { margin: 4px 0 0; font-size: 12px; }
.close-btn { border: 0; background: transparent; color: var(--text-2); font-size: 25px; line-height: 1; cursor: pointer; }
fieldset { min-width: 0; margin: 0; padding: 0; border: 0; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 14px; }
.field.wide { grid-column: 1 / -1; }
.field label { display: block; }
.input { margin: 0; }
select.input, textarea.input { width: 100%; border: 1px solid var(--border); border-radius: 10px; background: var(--bg); color: var(--text); padding: 10px 12px; font: inherit; font-size: 14px; }
textarea.input { resize: vertical; }
.check-line { display: flex; align-items: center; gap: 7px; margin: 0 0 16px; color: var(--text-2); font-size: 13px; }
.date-time { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(100px, .8fr); gap: 7px; }
.date-time:has(input:only-child) { grid-template-columns: 1fr; }
.color-field > div { display: flex; align-items: center; gap: 10px; min-height: 43px; color: var(--text-2); font-size: 12px; }
.color-field input { width: 42px; height: 32px; padding: 2px; border: 1px solid var(--border); border-radius: 7px; background: var(--bg); }
.link-btn { border: 0; background: transparent; color: var(--primary); padding: 5px 0; font-size: 12px; cursor: pointer; }
.form-error { color: var(--danger); font-size: 13px; }
.dialog-actions { display: flex; align-items: center; gap: 9px; margin-top: 18px; }
.dialog-actions .btn { padding: 8px 16px; font-size: 14px; }
.action-space { flex: 1; }
.danger-text { color: var(--danger); }
@media (max-width: 560px) {
  .overlay .event-dialog { padding: 18px; }
  .form-grid { grid-template-columns: 1fr; }
  .field.wide { grid-column: auto; }
  .dialog-actions { flex-wrap: wrap; }
}
</style>
