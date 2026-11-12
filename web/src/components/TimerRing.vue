<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 环形进度 0~1（倒计时剩余比例；正计时传 1 表示常亮满环） */
    progress?: number
    size?: number
  }>(),
  { progress: 1, size: 240 },
)

const R = 104
const CIRC = 2 * Math.PI * R

const dashOffset = computed(() => {
  const p = Math.min(1, Math.max(0, props.progress))
  return CIRC * (1 - p)
})
</script>

<template>
  <div class="ring" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" viewBox="0 0 240 240">
      <circle class="ring-track" cx="120" cy="120" :r="R" fill="none" stroke-width="6" />
      <circle
        class="ring-bar"
        cx="120"
        cy="120"
        :r="R"
        fill="none"
        stroke-width="6"
        stroke-linecap="round"
        :stroke-dasharray="CIRC"
        :stroke-dashoffset="dashOffset"
        transform="rotate(-90 120 120)"
      />
    </svg>
    <div class="ring-center">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.ring {
  position: relative;
  margin: 0 auto;
}

.ring-track {
  stroke: var(--border);
}

.ring-bar {
  stroke: var(--primary);
  transition: stroke-dashoffset 0.3s linear;
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
</style>
