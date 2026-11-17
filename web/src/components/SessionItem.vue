<script setup lang="ts">
import { computed } from 'vue'
import type { SessionVO } from '../api/session'
import { fmtDuration } from '../utils/date'
import dayjs from 'dayjs'

const props = defineProps<{ session: SessionVO }>()

const emit = defineEmits<{
  edit: [session: SessionVO]
  remove: [session: SessionVO]
}>()

const timeRange = computed(() => {
  const s = dayjs(props.session.startedAt).format('HH:mm')
  const e = dayjs(props.session.endedAt).format('HH:mm')
  return `${s} - ${e}`
})
</script>

<template>
  <div class="item">
    <span class="rail"><span class="dot" :class="{ aborted: session.status !== 'completed' }"></span></span>
    <div class="main">
      <div class="row1">
        <span class="time">{{ timeRange }}</span>
        <span class="duration">{{ fmtDuration(session.durationSec) }}</span>
      </div>
      <div class="task">{{ session.taskTitle || '未关联' }}</div>
      <div v-if="session.note" class="note">{{ session.note }}</div>
    </div>
    <div class="side">
      <button class="text-btn" @click="emit('edit', session)">改</button>
      <button class="text-btn danger" @click="emit('remove', session)">删除</button>
    </div>
  </div>
</template>

<style scoped>
.item {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
}

.item:last-child {
  border-bottom: none;
}

.rail {
  position: relative;
  align-self: stretch;
  display: flex;
  justify-content: center;
  padding-top: 6px;
}

.rail::after {
  content: '';
  position: absolute;
  top: 17px;
  bottom: -17px;
  width: 1px;
  background: var(--line);
}

.item:last-child .rail::after {
  display: none;
}

.dot {
  position: relative;
  z-index: 1;
  width: 8px;
  height: 8px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 1px var(--line);
}

.dot.aborted {
  background: #f5a623;
}

.main {
  min-width: 0;
}

.row1 {
  display: flex;
  align-items: center;
  gap: 10px;
}

.time {
  color: var(--muted);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.duration {
  color: var(--ink-2);
  font-size: 11.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.task {
  margin-top: 2px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note {
  margin-top: 3px;
  overflow: hidden;
  color: var(--muted);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0.55;
  transition: opacity 0.15s;
}

.item:hover .side,
.side:focus-within {
  opacity: 1;
}

.side .danger {
  color: var(--danger);
}
</style>
