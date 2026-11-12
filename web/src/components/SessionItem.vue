<script setup lang="ts">
import { computed } from 'vue'
import type { SessionVO } from '../api/session'
import { fmtDuration } from '../utils/date'
import dayjs from 'dayjs'

const props = defineProps<{ session: SessionVO }>()

const emit = defineEmits<{ remove: [session: SessionVO] }>()

const timeRange = computed(() => {
  const s = dayjs(props.session.startedAt).format('HH:mm')
  const e = dayjs(props.session.endedAt).format('HH:mm')
  return `${s}–${e}`
})
</script>

<template>
  <div class="item">
    <div class="main">
      <div class="row1">
        <span class="time">{{ timeRange }}</span>
        <span class="task">{{ session.taskTitle || '未填写任务' }}</span>
        <span class="badge" :class="session.status === 'completed' ? 'badge-completed' : 'badge-aborted'">
          {{ session.status === 'completed' ? '完成' : '中断' }}
        </span>
      </div>
      <div v-if="session.note" class="note muted">{{ session.note }}</div>
    </div>
    <div class="side">
      <span class="duration">{{ fmtDuration(session.durationSec) }}</span>
      <button class="btn-ghost delete" @click="emit('remove', session)">删除</button>
    </div>
  </div>
</template>

<style scoped>
.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--border);
}

.item:last-child {
  border-bottom: none;
}

.main {
  min-width: 0;
  flex: 1;
}

.row1 {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.time {
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  white-space: nowrap;
}

.task {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.duration {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.delete {
  font-size: 13px;
  padding: 4px 8px;
}
</style>
