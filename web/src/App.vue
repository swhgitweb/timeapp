<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, watchEffect } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useTimerStore } from './stores/timer'
import { useAuthStore } from './stores/auth'
import { useCalendarStore } from './stores/calendar'
import { logout } from './api/auth'
import { fmtClock } from './utils/date'

const NAV_ITEMS = [
  { name: 'records', label: '记录', to: '/records', icon: 'M4 4h16v16H4zM8 12l3 3 5-6' },
  { name: 'calendar', label: '日历', to: '/calendar', icon: 'M4 7h16v13H4zM8 3v4M16 3v4M4 11h16' },
  { name: 'focus', label: '专注', to: '/', icon: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zm0-13v5l3 2' },
  { name: 'stats', label: '统计', to: '/stats', icon: 'M5 20V10M12 20V4M19 20v-7' },
]

const LIST_COLORS = ['#4772fa', '#34a853', '#f5a623', '#e05c5c', '#8b5cf6', '#14b8a6']

const timer = useTimerStore()
const auth = useAuthStore()
const calendar = useCalendarStore()
const route = useRoute()
const router = useRouter()

const addingList = ref(false)
const newListName = ref('')
const newListColor = ref(LIST_COLORS[0])
const listError = ref('')

const lists = computed(() => calendar.lists.filter((list) => !list.archived))

const avatarText = computed(() => {
  const name = auth.user?.displayName || auth.user?.username || ''
  return name.slice(0, 1).toUpperCase() || 'U'
})

function isListVisible(id: number): boolean {
  const visible = calendar.settings.visibleListIds
  return visible.length === 0 || visible.includes(id)
}

function listCount(id: number): number {
  return calendar.tasks.filter((task) => task.listId === id && !task.completed).length
}

function toggleList(id: number) {
  if (calendar.settingsSaving) return
  void calendar.toggleList(id)
}

async function createList() {
  const name = newListName.value.trim()
  if (!name) return
  listError.value = ''
  try {
    await calendar.addList({ name: name.slice(0, 30), color: newListColor.value })
    newListName.value = ''
    newListColor.value = LIST_COLORS[lists.value.length % LIST_COLORS.length]
    addingList.value = false
  } catch (e) {
    listError.value = e instanceof Error ? e.message : '创建清单失败'
  }
}

// 登录态就绪后再恢复未结束的计时，避免初始化阶段清掉本地快照
watch(() => auth.user?.id, (id) => {
  if (id) {
    void timer.restore()
    void calendar.ensureMeta()
  }
}, { immediate: true })

watchEffect(() => {
  if (timer.phase !== 'idle') {
    const state = timer.phase === 'paused' ? '已暂停' : '专注中'
    document.title = `${fmtClock(timer.displayMs)} · ${state}`
  } else {
    document.title = '番茄专注'
  }
})

async function onLogout() {
  try { await logout() } finally {
    auth.markLoggedOut()
    timer.resetForLogout()
    await router.replace('/login')
  }
}

function onUnauthorized() {
  auth.markLoggedOut()
  timer.resetForLogout()
  if (route.name !== 'login') void router.replace('/login')
}

onMounted(() => window.addEventListener('auth:unauthorized', onUnauthorized))
onUnmounted(() => window.removeEventListener('auth:unauthorized', onUnauthorized))
</script>

<template>
  <RouterView v-if="route.meta.public" />
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 6L9 17l-5-5" />
          </svg>
        </div>
        <div class="brand-text">
          <strong>番茄专注</strong>
          <span>日历 · 专注</span>
        </div>
      </div>

      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="item in NAV_ITEMS"
          :key="item.name"
          :to="item.to"
          class="nav-item"
          :class="{ active: route.name === item.name }"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path :d="item.icon" />
          </svg>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="section-head">
        <span>清单</span>
        <button class="icon-btn" title="新建清单" @click="addingList = !addingList">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
        </button>
      </div>

      <div v-if="addingList" class="new-list">
        <input v-model="newListName" class="new-list-input" placeholder="清单名称" maxlength="30" @keyup.enter="createList" />
        <div class="swatches">
          <button
            v-for="color in LIST_COLORS"
            :key="color"
            class="swatch"
            :class="{ selected: newListColor === color }"
            :style="{ background: color }"
            @click="newListColor = color"
          ></button>
        </div>
        <p v-if="listError" class="new-list-error">{{ listError }}</p>
        <div class="new-list-actions">
          <button class="text-btn" @click="addingList = false">取消</button>
          <button class="mini-btn" @click="createList">添加</button>
        </div>
      </div>

      <ul class="list-nav">
        <li v-for="list in lists" :key="list.id">
          <button
            class="list-item"
            :class="{ off: !isListVisible(list.id) }"
            :title="isListVisible(list.id) ? '点击在日历中隐藏' : '点击在日历中显示'"
            @click="toggleList(list.id)"
          >
            <span class="list-dot" :style="{ background: list.color }">
              <svg v-if="isListVisible(list.id)" viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5" /></svg>
            </span>
            <span class="list-name">{{ list.name }}</span>
            <span class="list-count">{{ listCount(list.id) }}</span>
          </button>
        </li>
      </ul>

      <div class="sidebar-user">
        <span class="user-avatar" :title="auth.user?.displayName || auth.user?.username || ''">{{ avatarText }}</span>
        <span class="user-name">{{ auth.user?.displayName || auth.user?.username }}</span>
        <button class="icon-btn tiny" title="退出登录" aria-label="退出登录" @click="onLogout">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 4H5v16h9M17 8l4 4-4 4M21 12H10" /></svg>
        </button>
      </div>

      <div class="sidebar-foot">
        <span class="sync-dot"></span>
        <span>数据已同步到本地服务</span>
      </div>
    </aside>

    <div class="workspace">
      <main class="app-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
