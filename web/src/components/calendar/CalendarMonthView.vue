<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import { calendarEventKey, type CalendarDayInfo, type CalendarEvent } from '../../api/calendar'

const props = defineProps<{
  startDate: string
  days: number
  referenceDate: string
  weekStart: 0 | 1
  events: CalendarEvent[]
  dayInfo: CalendarDayInfo[]
  showLunar: boolean
  showHolidays: boolean
  compact?: boolean
  selectionMode: boolean
  selectedIds: string[]
}>()

const emit = defineEmits<{
  open: [event: CalendarEvent]
  select: [event: CalendarEvent]
  create: [date: string]
}>()

const weekdays = computed(() => {
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return Array.from({ length: 7 }, (_, index) => names[(props.weekStart + index) % 7])
})

const cells = computed(() => {
  const start = dayjs(props.startDate)
  const reference = dayjs(props.referenceDate)
  return Array.from({ length: props.days }, (_, index) => {
    const date = start.add(index, 'day')
    const key = date.format('YYYY-MM-DD')
    const dayStart = date.startOf('day')
    const dayEnd = dayStart.add(1, 'day')
    const events = props.events
      .filter((event) => dayjs(event.startAt).isBefore(dayEnd) && dayjs(event.endAt).isAfter(dayStart))
      .sort((a, b) => Number(b.allDay) - Number(a.allDay) || a.startAt.localeCompare(b.startAt))
    return {
      date: key,
      day: date.date(),
      inMonth: date.month() === reference.month(),
      events,
    }
  })
})

const infoMap = computed(() => new Map(props.dayInfo.map((info) => [info.date, info])))

function secondaryText(date: string): string {
  const info = infoMap.value.get(date)
  if (!info) return ''
  if (props.showHolidays && info.holiday) return info.holiday
  if (props.showHolidays && info.lunarFestivals?.length) return info.lunarFestivals[0]
  return props.showLunar ? info.lunar || '' : ''
}

function metaKind(date: string): string {
  const info = infoMap.value.get(date)
  if (!info || !props.showHolidays) return ''
  if (info.holiday) return 'holiday-name'
  if (info.lunarFestivals?.length) return 'festival'
  return ''
}

function isSelected(event: CalendarEvent): boolean {
  return props.selectedIds.includes(calendarEventKey(event))
}

function activate(event: CalendarEvent) {
  emit(props.selectionMode ? 'select' : 'open', event)
}
</script>

<template>
  <div class="grid-scroll" :class="{ compact }">
    <div class="weekday-row"><span v-for="day in weekdays" :key="day">{{ day }}</span></div>
    <div class="month-grid" :style="{ gridTemplateRows: `repeat(${Math.ceil(days / 7)}, minmax(96px, 1fr))` }">
      <div
        v-for="cell in cells"
        :key="cell.date"
        class="day-cell"
        :class="{
          'out-month': !compact && !cell.inMonth,
          today: cell.date === dayjs().format('YYYY-MM-DD'),
          weekend: [0, 6].includes(dayjs(cell.date).day()),
        }"
      >
        <div class="cell-head">
          <button class="day-number" title="新增当天日程" @click="emit('create', cell.date)">{{ cell.day }}</button>
          <span v-if="showHolidays && infoMap.get(cell.date)?.dayType" class="day-type" :class="infoMap.get(cell.date)?.dayType">
            {{ infoMap.get(cell.date)?.dayType === 'holiday' ? '休' : '班' }}
          </span>
        </div>
        <div class="day-meta" :class="metaKind(cell.date)" :title="secondaryText(cell.date)">{{ secondaryText(cell.date) || '\u00a0' }}</div>
        <div class="day-events">
          <button
            v-for="event in cell.events.slice(0, compact ? 2 : 3)"
            :key="calendarEventKey(event)"
            class="event-chip"
            :class="{ selected: isSelected(event), completed: event.completed, aborted: event.status === 'aborted' }"
            :style="{ '--event-color': event.color || '#3b6ef6' }"
            :aria-pressed="selectionMode ? isSelected(event) : undefined"
            :title="`${event.allDay ? '全天' : dayjs(event.startAt).format('HH:mm')} ${event.title}`"
            @click.stop="activate(event)"
          >
            <span v-if="selectionMode" class="select-mark">{{ isSelected(event) ? '✓' : '' }}</span>
            <span class="event-name">{{ event.title }}</span>
          </button>
          <div v-if="cell.events.length > (compact ? 2 : 3)" class="more-count">
            还有 {{ cell.events.length - (compact ? 2 : 3) }} 项
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.weekday-row,
.month-grid {
  min-width: 650px;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.weekday-row {
  padding: 0 4px 6px;
  color: var(--muted);
  font-size: 12px;
}

.weekday-row span {
  padding-left: 8px;
}

.month-grid {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: auto;
  background: #fff;
}

.day-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-height: 0;
  padding: 5px 6px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: transparent;
  overflow: hidden;
  cursor: pointer;
  transition: background 0.15s;
}

.day-cell:nth-child(7n) {
  border-right: 0;
}

.day-cell:nth-last-child(-n + 7) {
  border-bottom: 0;
}

.day-cell:hover {
  background: #fafbff;
}

.day-cell.out-month {
  background: #fcfcfd;
  color: var(--muted);
}

.day-cell.out-month .event-chip {
  opacity: 0.62;
}

.cell-head {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  flex: 0 0 22px;
}

.day-number {
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.day-number:hover {
  background: var(--accent-soft);
  color: var(--accent);
}

.day-cell.weekend .day-number {
  color: #c0392b;
}

.day-cell.today .day-number {
  background: var(--accent);
  color: #fff;
}

.day-type {
  margin-left: auto;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 9px;
  line-height: 1;
}

.day-type.holiday {
  background: #fdecec;
  color: #c0392b;
}

.day-type.workday {
  background: #eef2ff;
  color: #3b5bdb;
}

.day-meta {
  height: 15px;
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  line-height: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.day-meta.holiday-name {
  color: #c0392b;
}

.day-meta.festival {
  color: #c2410c;
}

.day-events {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 1px;
  min-height: 0;
}

.event-chip {
  --event-color: #4772fa;
  display: flex;
  align-items: center;
  gap: 3px;
  width: 100%;
  min-width: 0;
  padding: 2px 5px;
  border: 0;
  border-left: 2px solid var(--event-color);
  border-radius: 4px;
  background: color-mix(in srgb, var(--event-color) 12%, #fff);
  color: var(--ink);
  font: inherit;
  font-size: 11px;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
}

.event-chip:hover {
  background: color-mix(in srgb, var(--event-color) 22%, #fff);
}

.event-chip.selected {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.event-chip.completed .event-name {
  color: var(--muted);
  text-decoration: line-through;
}

.event-chip.aborted {
  border-left-style: dashed;
  opacity: 0.7;
}

.event-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-mark {
  flex: 0 0 13px;
  width: 13px;
  height: 13px;
  border: 1px solid var(--accent);
  border-radius: 3px;
  color: var(--accent);
  font-size: 10px;
  line-height: 11px;
  text-align: center;
}

.more-count {
  padding-left: 3px;
  color: var(--muted);
  font-size: 10px;
}

.compact .day-cell {
  min-height: 82px;
}

.compact .day-meta {
  display: none;
}
</style>
