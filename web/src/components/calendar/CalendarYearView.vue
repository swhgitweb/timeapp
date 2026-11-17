<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import type { CalendarDaily } from '../../api/calendar'

const props = defineProps<{
  year: number
  daily: CalendarDaily[]
  weekStart: 0 | 1
}>()

const emit = defineEmits<{ select: [date: string] }>()

const dailyMap = computed(() => new Map(props.daily.map((day) => [day.date, day])))
const maxCount = computed(() => Math.max(0, ...props.daily.map((day) => day.count)))
const weekdays = computed(() => {
  const names = ['日', '一', '二', '三', '四', '五', '六']
  return Array.from({ length: 7 }, (_, index) => names[(props.weekStart + index) % 7])
})

const months = computed(() => Array.from({ length: 12 }, (_, monthIndex) => {
  const first = dayjs(`${props.year}-${String(monthIndex + 1).padStart(2, '0')}-01`)
  const gridStart = first.subtract((first.day() - props.weekStart + 7) % 7, 'day')
  return {
    month: monthIndex + 1,
    firstDate: first.format('YYYY-MM-DD'),
    cells: Array.from({ length: 42 }, (_, index) => {
      const date = gridStart.add(index, 'day')
      const key = date.format('YYYY-MM-DD')
      const day = dailyMap.value.get(key)
      return {
        date: key,
        day: date.date(),
        inMonth: date.month() === monthIndex,
        count: day?.count ?? 0,
        focusSec: day?.focusSec ?? 0,
      }
    }),
  }
}))

function level(count: number): number {
  if (!count) return 0
  if (maxCount.value <= 1) return 2
  const ratio = count / maxCount.value
  return ratio <= 0.25 ? 1 : ratio <= 0.5 ? 2 : ratio <= 0.75 ? 3 : 4
}

function focusText(seconds: number): string {
  if (!seconds) return ''
  return seconds >= 3600 ? ` · 专注 ${(seconds / 3600).toFixed(1)} 小时` : ` · 专注 ${Math.round(seconds / 60)} 分钟`
}
</script>

<template>
  <div class="year-grid">
    <article v-for="month in months" :key="month.month" class="month-card">
      <button class="month-title" @click="emit('select', month.firstDate)">{{ month.month }}月</button>
      <div class="mini-weekdays"><span v-for="(day, index) in weekdays" :key="index">{{ day }}</span></div>
      <div class="mini-grid">
        <template v-for="cell in month.cells" :key="cell.date">
          <button
            v-if="cell.inMonth"
            class="heat-day"
            :class="[`lv${level(cell.count)}`, { today: cell.date === dayjs().format('YYYY-MM-DD') }]"
            :title="`${cell.date} · ${cell.count} 项日程${focusText(cell.focusSec)}`"
            @click="emit('select', cell.date)"
          >{{ cell.day }}</button>
          <span v-else class="heat-placeholder"></span>
        </template>
      </div>
    </article>
  </div>
</template>

<style scoped>
.year-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.month-card { padding: 14px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); min-width: 0; }
.month-title { border: 0; background: transparent; color: var(--text); padding: 0 0 10px; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.month-title:hover { color: var(--primary); }
.mini-weekdays, .mini-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 3px; }
.mini-weekdays { margin-bottom: 4px; color: var(--text-2); font-size: 10px; text-align: center; }
.heat-day, .heat-placeholder { width: 100%; aspect-ratio: 1; }
.heat-day { display: grid; place-items: center; min-width: 0; padding: 0; border: 1px solid transparent; border-radius: 5px; background: var(--bg); color: var(--text-2); font: inherit; font-size: 10px; cursor: pointer; }
.heat-day:hover { border-color: var(--primary); color: var(--text); }
.heat-day.lv1 { background: color-mix(in srgb, var(--primary) 12%, var(--surface)); color: var(--text); }
.heat-day.lv2 { background: color-mix(in srgb, var(--primary) 24%, var(--surface)); color: var(--text); }
.heat-day.lv3 { background: color-mix(in srgb, var(--primary) 38%, var(--surface)); color: var(--text); }
.heat-day.lv4 { background: color-mix(in srgb, var(--primary) 58%, var(--surface)); color: var(--text); }
.heat-day.today { border-color: var(--primary); font-weight: 700; }
@media (max-width: 980px) { .year-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 720px) { .year-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } }
@media (max-width: 420px) { .year-grid { grid-template-columns: 1fr; } .month-card { padding: 16px; } .heat-day { font-size: 12px; } }
</style>
