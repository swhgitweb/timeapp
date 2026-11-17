<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import { calendarEventKey, type CalendarEvent } from '../../api/calendar'

dayjs.extend(utc)
dayjs.extend(timezone)

const HOUR_HEIGHT = 52
const GRID_HEIGHT = 24 * HOUR_HEIGHT

interface TimeBlock {
  event: CalendarEvent
  style: Record<string, string>
  time: string
  continued: boolean
}

const props = defineProps<{
  startDate: string
  days: number
  events: CalendarEvent[]
  auxiliaryTimezone: string
  selectionMode: boolean
  selectedIds: string[]
}>()

const emit = defineEmits<{
  open: [event: CalendarEvent]
  select: [event: CalendarEvent]
  create: [date: string]
}>()

const bodyRef = ref<HTMLElement | null>(null)
const nowTick = ref(Date.now())
let nowTimer: number | undefined

const columns = computed(() => Array.from({ length: props.days }, (_, index) => {
  const day = dayjs(props.startDate).add(index, 'day')
  return {
    date: day.format('YYYY-MM-DD'),
    day: day.date(),
    month: day.format('M月'),
    weekday: `周${'日一二三四五六'[day.day()]}`,
  }
}))

const gridStyle = computed(() => ({
  gridTemplateColumns: `76px repeat(${props.days}, minmax(${props.days > 3 ? '108px' : '160px'}, 1fr))`,
  minWidth: `${76 + props.days * (props.days > 3 ? 108 : 160)}px`,
}))

const hours = Array.from({ length: 24 }, (_, hour) => hour)

const blocksByDay = computed(() => {
  const map = new Map<string, TimeBlock[]>()
  for (const column of columns.value) {
    const dayStart = dayjs(column.date).startOf('day')
    const dayEnd = dayStart.add(1, 'day')
    const blocks: TimeBlock[] = []
    for (const event of props.events) {
      if (event.allDay) continue
      const start = dayjs(event.startAt)
      const end = dayjs(event.endAt)
      if (!start.isBefore(dayEnd) || !end.isAfter(dayStart)) continue
      const visibleStart = start.isBefore(dayStart) ? dayStart : start
      const visibleEnd = end.isAfter(dayEnd) ? dayEnd : end
      const top = visibleStart.diff(dayStart, 'minute') / 60 * HOUR_HEIGHT
      const height = Math.max(20, visibleEnd.diff(visibleStart, 'minute') / 60 * HOUR_HEIGHT)
      blocks.push({
        event,
        continued: start.isBefore(dayStart),
        time: start.isBefore(dayStart) ? '00:00' : start.format('HH:mm'),
        style: {
          top: `${top}px`,
          height: `${height}px`,
          '--event-color': event.color || '#3b6ef6',
        },
      })
    }
    map.set(column.date, blocks.sort((a, b) => a.event.startAt.localeCompare(b.event.startAt)))
  }
  return map
})

const nowTop = computed(() => {
  const now = dayjs(nowTick.value)
  return (now.hour() * 60 + now.minute()) / 60 * HOUR_HEIGHT
})

function allDayEvents(date: string): CalendarEvent[] {
  const start = dayjs(date).startOf('day')
  const end = start.add(1, 'day')
  return props.events.filter((event) => event.allDay && dayjs(event.startAt).isBefore(end) && dayjs(event.endAt).isAfter(start))
}

function auxiliaryTime(hour: number): string {
  if (!props.auxiliaryTimezone) return ''
  const local = dayjs(props.startDate).hour(hour).minute(0).second(0)
  return dayjs.tz(local, props.auxiliaryTimezone).format('MM-DD HH:mm')
}

function isSelected(event: CalendarEvent): boolean {
  return props.selectedIds.includes(calendarEventKey(event))
}

function activate(event: CalendarEvent) {
  emit(props.selectionMode ? 'select' : 'open', event)
}

function isToday(date: string): boolean {
  return dayjs(nowTick.value).format('YYYY-MM-DD') === date
}

onMounted(() => {
  if (bodyRef.value) bodyRef.value.scrollTop = 7 * HOUR_HEIGHT
  nowTimer = window.setInterval(() => { nowTick.value = Date.now() }, 60000)
})

onBeforeUnmount(() => {
  if (nowTimer) window.clearInterval(nowTimer)
})
</script>

<template>
  <div class="time-scroll">
    <div class="time-head" :style="gridStyle">
      <div class="axis-head">
        <span>本地</span>
        <small v-if="auxiliaryTimezone">{{ auxiliaryTimezone.replaceAll('_', ' ') }}</small>
      </div>
      <div v-for="column in columns" :key="column.date" class="day-head" :class="{ today: isToday(column.date) }">
        <span>{{ column.weekday }}</span>
        <button title="新增当天日程" @click="emit('create', column.date)">{{ column.month }}{{ column.day }}日</button>
      </div>
    </div>

    <div class="all-day-row" :style="gridStyle">
      <div class="all-day-label">全天</div>
      <div v-for="column in columns" :key="column.date" class="all-day-cell">
        <button
          v-for="event in allDayEvents(column.date)"
          :key="calendarEventKey(event)"
          class="all-day-event"
          :class="{ selected: isSelected(event), completed: event.completed }"
          :style="{ '--event-color': event.color || '#3b6ef6' }"
          @click="activate(event)"
        >
          <span v-if="selectionMode">{{ isSelected(event) ? '✓ ' : '○ ' }}</span>{{ event.title }}
        </button>
      </div>
    </div>

    <div ref="bodyRef" class="time-body">
      <div class="time-grid" :style="{ ...gridStyle, height: `${GRID_HEIGHT}px` }">
        <div class="time-axis">
          <div v-for="hour in hours" :key="hour" class="axis-label" :style="{ top: `${hour * HOUR_HEIGHT}px` }">
            <span>{{ String(hour).padStart(2, '0') }}:00</span>
            <small v-if="auxiliaryTimezone">{{ auxiliaryTime(hour) }}</small>
          </div>
        </div>
        <div v-for="column in columns" :key="column.date" class="time-column">
          <button
            v-for="block in blocksByDay.get(column.date)"
            :key="`${calendarEventKey(block.event)}-${column.date}`"
            class="time-block"
            :class="{ compact: parseFloat(block.style.height) < 34, selected: isSelected(block.event), completed: block.event.completed, aborted: block.event.status === 'aborted' }"
            :style="block.style"
            :title="`${block.time} ${block.event.title}`"
            @click="activate(block.event)"
          >
            <span class="block-time">{{ block.time }}{{ block.continued ? ' · 续' : '' }}</span>
            <span class="block-title">{{ block.event.title }}</span>
            <span v-if="selectionMode" class="block-select">{{ isSelected(block.event) ? '✓' : '○' }}</span>
          </button>
          <div v-if="isToday(column.date)" class="now-line" :style="{ top: `${nowTop}px` }"><span></span></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.time-scroll { overflow-x: auto; }
.time-head, .all-day-row, .time-grid { display: grid; }
.time-head { border-bottom: 1px solid var(--line); }
.axis-head { position: sticky; left: 0; z-index: 5; display: flex; flex-direction: column; justify-content: center; padding: 5px 8px; background: #fff; color: var(--muted); font-size: 11px; }
.axis-head small { max-width: 66px; overflow: hidden; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.day-head { display: flex; align-items: center; justify-content: center; gap: 6px; min-height: 52px; color: var(--muted); font-size: 11px; }
.day-head button { border: 0; border-radius: 999px; background: transparent; color: var(--ink); padding: 4px 9px; font: inherit; font-size: 12.5px; font-weight: 600; cursor: pointer; }
.day-head button:hover { background: var(--accent-soft); color: var(--accent); }
.day-head.today { color: var(--accent); }
.day-head.today button { background: var(--accent); color: #fff; }
.all-day-row { min-height: 34px; border-bottom: 1px solid var(--line); }
.all-day-label { position: sticky; left: 0; z-index: 5; padding: 8px; background: #fff; color: var(--muted); font-size: 11px; text-align: right; }
.all-day-cell { padding: 3px; border-left: 1px solid var(--line); }
.all-day-event { --event-color: #4772fa; display: block; width: 100%; margin-bottom: 2px; overflow: hidden; padding: 2px 5px; border: 0; border-left: 2px solid var(--event-color); border-radius: 4px; background: color-mix(in srgb, var(--event-color) 12%, #fff); color: var(--ink); font: inherit; font-size: 10.5px; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.all-day-event:hover { background: color-mix(in srgb, var(--event-color) 22%, #fff); }
.all-day-event.selected { outline: 2px solid var(--accent); }
.all-day-event.completed { text-decoration: line-through; opacity: 0.65; }
.time-body { max-height: 620px; overflow-y: auto; scrollbar-gutter: stable; }
.time-grid { position: relative; }
.time-axis { position: relative; background: #fff; }
.axis-label { position: absolute; left: 0; right: 5px; display: flex; flex-direction: column; align-items: flex-end; height: 52px; padding-top: 3px; color: var(--muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.axis-label small { font-size: 8px; }
.time-column { position: relative; border-left: 1px solid var(--line); background-image: repeating-linear-gradient(to bottom, var(--line) 0 1px, transparent 1px 52px); }
.time-block { --event-color: #4772fa; position: absolute; left: 3px; right: 3px; z-index: 2; display: flex; flex-direction: column; align-items: flex-start; gap: 1px; min-width: 0; overflow: hidden; padding: 4px 6px; border: 0; border-left: 3px solid var(--event-color); border-radius: 5px; background: color-mix(in srgb, var(--event-color) 14%, #fff); color: var(--ink); font: inherit; font-size: 11px; line-height: 1.3; text-align: left; cursor: pointer; }
.time-block:hover { z-index: 4; background: color-mix(in srgb, var(--event-color) 24%, #fff); }
.time-block.selected { outline: 2px solid var(--accent); outline-offset: -1px; }
.time-block.completed .block-title { text-decoration: line-through; opacity: 0.65; }
.time-block.aborted { border-left-style: dashed; opacity: 0.7; }
.time-block.compact { flex-direction: row; align-items: center; padding-top: 2px; padding-bottom: 2px; }
.time-block.compact .block-title { display: none; }
.block-time { color: var(--ink-2); font-size: 10px; font-variant-numeric: tabular-nums; }
.block-title { max-width: 100%; overflow: hidden; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.block-select { position: absolute; top: 3px; right: 4px; color: var(--accent); }
.now-line { position: absolute; left: 0; right: 0; z-index: 3; height: 2px; background: var(--danger); pointer-events: none; }
.now-line span { position: absolute; left: -3px; top: -3px; width: 8px; height: 8px; border-radius: 50%; background: var(--danger); }
</style>
