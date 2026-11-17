<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import {
  createSubscription,
  deleteSubscription,
  enableCalendarExport,
  getCalendarExport,
  listSubscriptions,
  refreshSubscription,
  revokeCalendarExport,
  updateSubscription,
  type CalendarSubscription,
  type ExportStatus,
} from '../../api/subscription'

const emit = defineEmits<{ changed: [] }>()
const open = ref(false)
const loading = ref(false)
const saving = ref(false)
const refreshingId = ref<number | null>(null)
const error = ref('')
const notice = ref('')
const subscriptions = ref<CalendarSubscription[]>([])
const exportStatus = ref<ExportStatus>({ enabled: false, url: null })
const editingId = ref<number | null>(null)
const form = reactive({ name: '', url: '', color: '#7c6cf7', enabled: true })

const exportUrl = computed(() => exportStatus.value.url
  ? new URL(exportStatus.value.url, window.location.origin).toString()
  : '')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [items, status] = await Promise.all([listSubscriptions(), getCalendarExport()])
    subscriptions.value = items
    exportStatus.value = status
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载订阅失败'
  } finally {
    loading.value = false
  }
}

function show() {
  open.value = true
  resetForm()
  notice.value = ''
  void load()
}

function resetForm() {
  editingId.value = null
  form.name = ''
  form.url = ''
  form.color = '#7c6cf7'
  form.enabled = true
}

function edit(item: CalendarSubscription) {
  editingId.value = item.id
  form.name = item.name
  form.url = item.url
  form.color = item.color
  form.enabled = item.enabled
  notice.value = ''
  error.value = ''
}

async function submit() {
  if (!form.name.trim() || !form.url.trim()) {
    error.value = '请填写订阅名称和地址'
    return
  }
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const payload = { name: form.name.trim(), url: form.url.trim(), color: form.color, enabled: form.enabled }
    const saved = editingId.value
      ? await updateSubscription(editingId.value, payload)
      : await createSubscription(payload)
    notice.value = saved.lastError || '订阅已保存'
    resetForm()
    await load()
    emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存订阅失败'
  } finally {
    saving.value = false
  }
}

async function toggle(item: CalendarSubscription) {
  error.value = ''
  try {
    await updateSubscription(item.id, {
      name: item.name, url: item.url, color: item.color, enabled: !item.enabled,
    })
    await load()
    emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '更新订阅状态失败'
  }
}

async function refresh(item: CalendarSubscription) {
  refreshingId.value = item.id
  error.value = ''
  notice.value = ''
  try {
    const result = await refreshSubscription(item.id)
    notice.value = result.ok
      ? result.notModified ? '订阅已是最新' : `刷新成功，共 ${result.eventCount ?? 0} 条日程`
      : result.lastError || '刷新失败'
    await load()
    if (result.ok) emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '刷新订阅失败'
  } finally {
    refreshingId.value = null
  }
}

async function remove(item: CalendarSubscription) {
  if (!window.confirm(`取消订阅“${item.name}”？其外部日程也会从本应用移除。`)) return
  error.value = ''
  try {
    await deleteSubscription(item.id)
    if (editingId.value === item.id) resetForm()
    await load()
    emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取消订阅失败'
  }
}

async function enableExport() {
  error.value = ''
  try {
    exportStatus.value = await enableCalendarExport()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '生成导出地址失败'
  }
}

async function revokeExport() {
  if (!window.confirm('撤销后，旧导出地址会立即失效，确定继续吗？')) return
  error.value = ''
  try {
    await revokeCalendarExport()
    exportStatus.value = { enabled: false, url: null }
    notice.value = '旧导出地址已撤销'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '撤销导出地址失败'
  }
}

async function copyExportUrl() {
  try {
    await navigator.clipboard.writeText(exportUrl.value)
    notice.value = '导出地址已复制'
  } catch {
    error.value = '自动复制失败，请选中地址手动复制'
  }
}

function formatRefreshed(value: string | null) {
  return value ? dayjs(`${value}Z`).format('YYYY-MM-DD HH:mm') : '尚未刷新'
}
</script>

<template>
  <button class="btn subscription-trigger" type="button" @click="show">订阅与导出</button>
  <Teleport to="body">
    <div v-if="open" class="overlay" @click.self="open = false">
      <section class="dialog subscription-dialog" role="dialog" aria-modal="true" aria-label="日历订阅与导出">
        <header class="dialog-header">
          <h2>日历订阅与导出</h2>
          <button class="btn btn-ghost" type="button" aria-label="关闭" @click="open = false">关闭</button>
        </header>

        <p v-if="error" class="message error-message">{{ error }}</p>
        <p v-if="notice" class="message notice-message">{{ notice }}</p>

        <form class="subscription-form" @submit.prevent="submit">
          <div class="form-title">{{ editingId ? '编辑订阅' : '添加 ICS 订阅' }}</div>
          <input v-model="form.name" class="input" maxlength="100" placeholder="订阅名称" aria-label="订阅名称">
          <input v-model="form.url" class="input" maxlength="2000" placeholder="https://example.com/calendar.ics" aria-label="ICS 订阅地址">
          <div class="form-options">
            <label class="color-label">颜色 <input v-model="form.color" type="color" aria-label="订阅颜色"></label>
            <label><input v-model="form.enabled" type="checkbox"> 在日历中显示</label>
            <button class="btn btn-primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : editingId ? '保存修改' : '添加订阅' }}</button>
            <button v-if="editingId" class="btn" type="button" @click="resetForm">取消编辑</button>
          </div>
        </form>

        <div v-if="loading" class="muted state-text">正在加载…</div>
        <div v-else class="subscription-list">
          <article v-for="item in subscriptions" :key="item.id" class="subscription-item">
            <span class="subscription-color" :style="{ background: item.color }"></span>
            <div class="subscription-info">
              <strong>{{ item.name }}</strong>
              <span class="muted">上次刷新：{{ formatRefreshed(item.lastRefreshedAt) }}</span>
              <span v-if="item.lastError" class="item-error">{{ item.lastError }}</span>
            </div>
            <div class="subscription-actions">
              <button class="btn btn-ghost" type="button" @click="toggle(item)">{{ item.enabled ? '停用' : '启用' }}</button>
              <button class="btn btn-ghost" type="button" :disabled="refreshingId === item.id" @click="refresh(item)">
                {{ refreshingId === item.id ? '刷新中…' : '刷新' }}
              </button>
              <button class="btn btn-ghost" type="button" @click="edit(item)">编辑</button>
              <button class="btn btn-ghost danger-text" type="button" @click="remove(item)">取消订阅</button>
            </div>
          </article>
          <p v-if="!subscriptions.length" class="muted state-text">暂无外部日历订阅</p>
        </div>

        <section class="export-section">
          <div>
            <h3>导出本站日程</h3>
            <p class="muted">包含任务、习惯、纪念日和课程，不导出专注记录及外部订阅。</p>
          </div>
          <button v-if="!exportStatus.enabled" class="btn" type="button" @click="enableExport">生成导出地址</button>
          <template v-else>
            <input class="input export-url" :value="exportUrl" readonly aria-label="ICS 导出地址" @focus="($event.target as HTMLInputElement).select()">
            <div class="export-actions">
              <button class="btn" type="button" @click="copyExportUrl">复制地址</button>
              <button class="btn btn-ghost danger-text" type="button" @click="revokeExport">撤销旧地址</button>
            </div>
          </template>
        </section>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.subscription-trigger { padding: 7px 12px; font-size: 13px; white-space: nowrap; }
.subscription-dialog { width: min(760px, 100%); max-height: min(88vh, 900px); overflow-y: auto; padding: 22px; }
.dialog-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.dialog-header h2 { margin: 0; font-size: 19px; }
.message { padding: 9px 12px; border-radius: 8px; font-size: 13px; }
.error-message { color: var(--danger); background: var(--danger-weak); }
.notice-message { color: var(--primary); background: var(--primary-weak); }
.subscription-form { display: grid; gap: 10px; padding-bottom: 18px; border-bottom: 1px solid var(--border); }
.form-title { font-weight: 600; }
.form-options { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; font-size: 13px; }
.form-options .btn { padding: 7px 12px; }
.color-label { display: inline-flex; align-items: center; gap: 6px; }
.color-label input { width: 34px; height: 28px; padding: 0; border: 1px solid var(--border); background: transparent; }
.subscription-list { margin-top: 8px; }
.subscription-item { display: flex; align-items: center; gap: 10px; padding: 13px 0; border-bottom: 1px solid var(--border); }
.subscription-color { flex: 0 0 auto; width: 10px; height: 10px; border-radius: 50%; }
.subscription-info { display: flex; flex: 1; min-width: 0; flex-direction: column; }
.subscription-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.subscription-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 2px; }
.subscription-actions .btn { padding: 5px 7px; font-size: 12px; }
.item-error { color: var(--danger); font-size: 12px; overflow-wrap: anywhere; }
.danger-text { color: var(--danger); }
.state-text { padding: 18px 0; text-align: center; }
.export-section { margin-top: 18px; padding-top: 18px; border-top: 1px solid var(--border); }
.export-section h3 { margin: 0 0 4px; font-size: 16px; }
.export-section p { margin: 0 0 12px; }
.export-url { margin-top: 10px; font-size: 12px; }
.export-actions { display: flex; gap: 8px; margin-top: 10px; }
@media (max-width: 640px) {
  .subscription-dialog { padding: 16px; }
  .subscription-item { align-items: flex-start; flex-wrap: wrap; }
  .subscription-info { flex-basis: calc(100% - 24px); }
  .subscription-actions { width: 100%; justify-content: flex-start; }
}
</style>
