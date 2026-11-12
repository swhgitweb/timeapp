<script setup lang="ts">
import { onMounted, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { useTimerStore } from './stores/timer'
import { fmtClock } from './utils/date'

const timer = useTimerStore()
const route = useRoute()

// R9：App 挂载时尝试恢复未结束的计时
onMounted(() => {
  void timer.restore()
})

// R10：运行中标题显示 MM:SS · 专注中
watchEffect(() => {
  const running = timer.phase !== 'idle'
  if (running) {
    const state = timer.phase === 'paused' ? '已暂停' : '专注中'
    document.title = `${fmtClock(timer.displayMs)} · ${state}`
  } else {
    document.title = '番茄专注'
  }
})
</script>

<template>
  <div class="container">
    <header class="header">
      <span class="title">番茄专注</span>
      <nav class="nav">
        <RouterLink to="/">专注</RouterLink>
        <RouterLink to="/records">记录</RouterLink>
      </nav>
    </header>
    <RouterView :key="route.fullPath" />
  </div>
</template>
