<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { deleteSession, getSession, listSessions, type SessionVO } from '../api/session'
import { fmtDateCN, fmtDuration, todayStr } from '../utils/date'
import SessionItem from '../components/SessionItem.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import SessionEditDialog from '../components/SessionEditDialog.vue'

const route = useRoute()
const router = useRouter()

// ---------- 数据 ----------
const sessions = ref<SessionVO[]>([])
const loading = ref(true)
const loadError = ref('')

async function loadAll() {
  loading.value = true
  loadError.value = ''
  try {
    sessions.value = await listSessions({ limit: 100 })
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadAll()
  const id = Number(route.query.edit)
  if (!Number.isInteger(id) || id <= 0) return
  try {
    openEdit(await getSession(id))
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '无法打开这条专注记录'
  }
})

// ---------- 补记 / 编辑 ----------
const editorOpen = ref(false)
const editing = ref<SessionVO | null>(null)

function openCreate() {
  editing.value = null
  editorOpen.value = true
}

function openEdit(session: SessionVO) {
  editing.value = session
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
  editing.value = null
  if (route.query.edit !== undefined) {
    const query = { ...route.query }
    delete query.edit
    void router.replace({ query })
  }
}

async function onEditorSaved() {
  closeEditor()
  await loadAll()
}

// ---------- 列表按日分组 ----------
const groups = computed(() => {
  const today = todayStr()
  const yesterday = dayjs().subtract(1, 'day').format('YYYY-MM-DD')
  const map = new Map<string, SessionVO[]>()
  for (const s of sessions.value) {
    const arr = map.get(s.sessionDate) ?? []
    arr.push(s)
    map.set(s.sessionDate, arr)
  }
  return [...map.entries()].map(([date, items]) => ({
    date,
    label: date === today ? '今天' : date === yesterday ? '昨天' : fmtDateCN(date),
    items,
  }))
})

const isEmpty = computed(() => !loading.value && !loadError.value && sessions.value.length === 0)
const countText = computed(() => loading.value ? '正在加载…' : `共 ${sessions.value.length} 条记录`)

// ---------- 删除（软删除，二次确认） ----------
const pendingRemove = ref<SessionVO | null>(null)
const removing = ref(false)

function askRemove(session: SessionVO) {
  pendingRemove.value = session
}

async function confirmRemove() {
  if (!pendingRemove.value) return
  removing.value = true
  try {
    await deleteSession(pendingRemove.value.id)
    pendingRemove.value = null
    await loadAll()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    removing.value = false
  }
}
</script>

<template>
  <main class="page records-page">
    <header class="page-head">
      <div>
        <h1>专注记录</h1>
        <p class="head-sub">{{ countText }} · 回顾每一次专注</p>
      </div>
      <div class="toolbar">
        <button class="primary-btn" @click="openCreate">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
          补记专注
        </button>
      </div>
    </header>

    <div class="page-body records-body">
    <div v-if="loadError" class="card error-card">
      <p>{{ loadError }}</p>
      <button class="btn" @click="loadAll">重试</button>
    </div>

    <template v-else>
      <div v-if="loading" class="muted empty-hint">加载中…</div>

      <div v-else-if="isEmpty" class="card empty-card">
        <p class="muted">还没有记录，去专注一次吧</p>
        <RouterLink to="/" class="btn btn-primary">去专注</RouterLink>
      </div>

      <div v-else class="card records-card">
        <div v-for="g in groups" :key="g.date" class="group">
          <div class="group-label">{{ g.label }}</div>
          <SessionItem
            v-for="s in g.items"
            :key="s.id"
            :session="s"
            @edit="openEdit"
            @remove="askRemove"
          />
        </div>
      </div>
    </template>
    </div>

    <SessionEditDialog
      :open="editorOpen"
      :session="editing"
      @close="closeEditor"
      @saved="onEditorSaved"
    />

    <!-- 删除确认 -->
    <ConfirmDialog
      :open="!!pendingRemove"
      title="删除这条记录？"
      :message="pendingRemove ? `${pendingRemove.taskTitle || '未关联'} · ${fmtDuration(pendingRemove.durationSec)}` : ''"
      confirm-text="删除"
      danger
      :loading="removing"
      @confirm="confirmRemove"
      @cancel="pendingRemove = null"
    />
  </main>
</template>

<style scoped>
.records-page {
  min-height: 100%;
}

.records-body {
  padding: 0 24px 28px;
  background: var(--bg-soft);
}

.records-card {
  max-width: 900px;
  padding: 8px 18px 14px;
}

.group-label {
  padding: 12px 4px 4px;
  color: var(--muted);
  font-size: 11.5px;
  font-weight: 500;
}

.group + .group .group-label {
  border-top: 1px solid var(--line);
  margin-top: 8px;
}

.empty-hint {
  padding: 20px 0;
}

.empty-card {
  text-align: center;
  padding: 40px 24px;
}

.empty-card .btn {
  margin-top: 12px;
}

.error-card {
  text-align: center;
}

.error-card .btn {
  margin-top: 12px;
}

@media (max-width: 720px) {
  .records-body {
    padding: 0 16px 24px;
  }

  .records-card {
    padding: 6px 12px 10px;
  }
}
</style>
