<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 环形进度 0~1（倒计时剩余比例；正计时传 1 表示常亮满环） */
    progress?: number
    size?: number
    /** 外圈刻度盘，参考滴答清单专注页的样式 */
    showTicks?: boolean
  }>(),
  { progress: 1, size: 240, showTicks: false },
)

const R = 104
const CIRC = 2 * Math.PI * R

const dashOffset = computed(() => {
  const p = Math.min(1, Math.max(0, props.progress))
  return CIRC * (1 - p)
})

// 60 条刻线，每 5 条加重，形成表盘效果
const ticks = computed(() => Array.from({ length: 60 }, (_, index) => {
  const angle = ((index * 6) - 90) * Math.PI / 180
  const major = index % 5 === 0
  const inner = major ? 107 : 112
  return {
    x1: 120 + inner * Math.cos(angle),
    y1: 120 + inner * Math.sin(angle),
    x2: 120 + 118 * Math.cos(angle),
    y2: 120 + 118 * Math.sin(angle),
    major,
  }
}))
</script>

<template>
  <div class="ring" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" viewBox="0 0 240 240">
      <g v-if="showTicks">
        <line
          v-for="(tick, index) in ticks"
          :key="index"
          class="ring-tick"
          :class="{ major: tick.major }"
          :x1="tick.x1"
          :y1="tick.y1"
          :x2="tick.x2"
          :y2="tick.y2"
        />
      </g>
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

.ring-tick {
  stroke: var(--border);
  stroke-width: 1.2;
}

.ring-tick.major {
  stroke: var(--text-2);
  stroke-width: 1.8;
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
