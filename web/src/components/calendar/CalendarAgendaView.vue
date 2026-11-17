<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import { CALENDAR_KIND_LABELS, calendarEventKey, type CalendarDayInfo, type CalendarEvent } from '../../api/calendar'

const props = defineProps<{
  events: CalendarEvent[]
  dayInfo: CalendarDayInfo[]
  showLunar: boolean
  showHolidays: boolean
  selectionMode: boolean
  selectedIds: string[]
}>()

const emit = defineEmits<{ open: [event: CalendarEvent]; select: [event: CalendarEvent] }>()

const groups = computed(() => {
  const map = new Map<string, CalendarEvent[]>()
  for (const event of [...props.events].sort((a, b) => a.startAt.localeCompare(b.startAt))) {
    const date = dayjs(event.startAt).format('YYYY-MM-DD')
    map.set(date, [...(map.get(date) ?? []), event])
  }
  return [...map.entries()].map(([date, events]) => ({ date, events }))
})

const infoMap = computed(() => new Map(props.dayInfo.map((info) => [info.date, info])))

function dayLabel(date: string): string {
  const day = dayjs(date)
  return `${day.format('M月D日')} 周${'日一二三四五六'[day.day()]}`
}

function dayMeta(date: string): string {
  const info = infoMap.value.get(date)
  if (!info) return ''
  const values: string[] = []
  if (props.showHolidays && info.holiday) values.push(info.holiday)
  if (props.showHolidays && info.lunarFestivals?.length) values.push(...info.lunarFestivals)
  if (props.showLunar && info.lunar) values.push(info.lunar)
  return [...new Set(values)].join(' · ')
}

function timeText(event: CalendarEvent): string {
  if (event.allDay) return '全天'
  const start = dayjs(event.startAt)
  const end = dayjs(event.endAt)
  return start.isSame(end, 'day')
    ? `${start.format('HH:mm')}–${end.format('HH:mm')}`
    : `${start.format('HH:mm')} → ${end.format('M月D日 HH:mm')}`
}

function isSelected(event: CalendarEvent): boolean {
  return props.selectedIds.includes(calendarEventKey(event))
}

function activate(event: CalendarEvent) {
  emit(props.selectionMode ? 'select' : 'open', event)
}
</script>

<template>
  <div class="agenda-list">
    <section v-for="group in groups" :key="group.date" class="agenda-day">
      <header class="agenda-head">
        <h3>{{ dayLabel(group.date) }}</h3>
        <span v-if="dayMeta(group.date)" class="day-meta">{{ dayMeta(group.date) }}</span>
        <span class="count">{{ group.events.length }} 项</span>
      </header>
      <button
        v-for="event in group.events"
        :key="calendarEventKey(event)"
        class="agenda-event"
        :class="{ selected: isSelected(event), completed: event.completed }"
        :style="{ '--event-color': event.color || '#3b6ef6' }"
        :aria-pressed="selectionMode ? isSelected(event) : undefined"
        @click="activate(event)"
      >
        <span v-if="selectionMode" class="select-mark">{{ isSelected(event) ? '✓' : '' }}</span>
        <span class="event-time">{{ timeText(event) }}</span>
        <span class="event-main">
          <span class="event-title">{{ event.title }}</span>
          <span v-if="event.note" class="event-note">{{ event.note }}</span>
        </span>
        <span class="kind-badge">{{ CALENDAR_KIND_LABELS[event.kind] }}</span>
        <span v-if="event.status === 'aborted'" class="status-text">已中断</span>
      </button>
    </section>
  </div>
</template>

<style scoped>
.agenda-list { display: flex; flex-direction: column; gap: 18px; }
.agenda-day { min-width: 0; }
.agenda-head { display: flex; align-items: baseline; gap: 10px; padding: 0 4px 8px; border-bottom: 1px solid var(--border); }
.agenda-head h3 { margin: 0; font-size: 15px; }
.day-meta { flex: 1; min-width: 0; overflow: hidden; color: var(--text-2); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.count { margin-left: auto; color: var(--text-2); font-size: 12px; }
.agenda-event { --event-color: #3b6ef6; display: flex; align-items: center; gap: 12px; width: 100%; padding: 12px 8px; border: 0; border-bottom: 1px solid var(--border); border-left: 3px solid var(--event-color); background: transparent; color: var(--text); font: inherit; text-align: left; cursor: pointer; }
.agenda-event:hover { background: color-mix(in srgb, var(--event-color) 8%, var(--surface)); }
.agenda-event.selected { background: var(--primary-weak); }
.event-time { flex: 0 0 105px; color: var(--text-2); font-size: 12px; font-variant-numeric: tabular-nums; }
.event-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.event-title { overflow: hidden; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.completed .event-title { color: var(--text-2); text-decoration: line-through; }
.event-note { overflow: hidden; color: var(--text-2); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.kind-badge { flex: 0 0 auto; padding: 2px 7px; border-radius: 999px; background: color-mix(in srgb, var(--event-color) 13%, var(--surface)); color: var(--text-2); font-size: 11px; }
.status-text { color: var(--danger); font-size: 11px; }
.select-mark { flex: 0 0 18px; width: 18px; height: 18px; border: 1px solid var(--primary); border-radius: 4px; color: var(--primary); line-height: 16px; text-align: center; }
@media (max-width: 560px) {
  .agenda-event { gap: 8px; padding: 11px 5px; }
  .event-time { flex-basis: 74px; font-size: 11px; }
  .kind-badge { display: none; }
  .day-meta { display: none; }
}
</style>
