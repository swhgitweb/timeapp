<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useCalendarStore } from '../../stores/calendar'
import type { CalendarList } from '../../api/calendar'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const store = useCalendarStore()
const tab = ref<'list' | 'task' | 'habit'>('list')
const name = ref('')
const note = ref('')
const listId = ref('')
const color = ref('#3b6ef6')
const saving = ref(false)
const changingListId = ref<number | null>(null)
const error = ref('')

const activeLists = computed(() => store.lists.filter((list) => !list.archived))
const tabs = [{ value: 'list', label: '清单' }, { value: 'task', label: '任务' }, { value: 'habit', label: '习惯' }] as const

watch(() => [props.open, tab.value], () => {
  error.value = ''
  name.value = ''
  note.value = ''
  if (!listId.value || !activeLists.value.some((list) => String(list.id) === listId.value)) {
    listId.value = activeLists.value[0] ? String(activeLists.value[0].id) : ''
  }
}, { immediate: true })

function listColor(id: number | null): string {
  return store.lists.find((list) => list.id === id)?.color || '#3b6ef6'
}

async function submit() {
  error.value = ''
  if (!name.value.trim()) { error.value = tab.value === 'task' ? '请输入任务标题' : '请输入名称'; return }
  if (tab.value === 'task' && !listId.value) { error.value = '请先创建并选择一个清单'; return }
  saving.value = true
  try {
    if (tab.value === 'list') await store.addList({ name: name.value.trim(), color: color.value })
    else if (tab.value === 'habit') await store.addHabit({ name: name.value.trim(), color: color.value })
    else await store.addTask({ listId: Number(listId.value), title: name.value.trim(), note: note.value.trim() })
    name.value = ''
    note.value = ''
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '创建失败，请重试'
  } finally {
    saving.value = false
  }
}

async function onListColor(list: CalendarList, event: Event) {
  const color = (event.target as HTMLInputElement).value
  changingListId.value = list.id
  error.value = ''
  try {
    await store.setListColor(list, color)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '修改清单颜色失败'
  } finally {
    changingListId.value = null
  }
}
</script>

<template>
  <div v-if="open" class="overlay" @click.self="emit('close')">
    <section class="dialog data-dialog" role="dialog" aria-modal="true" aria-label="清单与数据">
      <header class="dialog-head">
        <div><h3>清单与数据</h3><p>先创建可关联的数据，再在日历中安排日程。</p></div>
        <button class="close-btn" aria-label="关闭" @click="emit('close')">×</button>
      </header>
      <div class="data-tabs">
        <button v-for="item in tabs" :key="item.value" :class="{ active: tab === item.value }" @click="tab = item.value">{{ item.label }}</button>
      </div>
      <form class="create-form" @submit.prevent="submit">
        <div class="field">
          <label>{{ tab === 'task' ? '任务标题' : tab === 'list' ? '清单名称' : '习惯名称' }}</label>
          <input v-model="name" class="input" maxlength="200" required :placeholder="tab === 'task' ? '例如：完成项目方案' : tab === 'list' ? '例如：工作' : '例如：每日阅读'" />
        </div>
        <div v-if="tab === 'task'" class="field">
          <label>所属清单</label>
          <select v-model="listId" class="input" required>
            <option value="">请选择清单</option>
            <option v-for="list in activeLists" :key="list.id" :value="String(list.id)">{{ list.name }}</option>
          </select>
          <small class="muted">任务颜色跟随所属清单；当前共 {{ store.tasks.length }} 个任务。</small>
        </div>
        <div v-if="tab === 'task'" class="field">
          <label>备注（可选）</label>
          <textarea v-model="note" class="input" rows="2" maxlength="2000"></textarea>
        </div>
        <div v-else class="color-row">
          <label>颜色</label><input v-model="color" type="color" aria-label="选择颜色" /><span>{{ color }}</span>
        </div>
        <p v-if="tab === 'task' && !activeLists.length" class="form-hint">还没有清单，请先切换到“清单”创建。</p>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn btn-primary" :disabled="saving || (tab === 'task' && !activeLists.length)">{{ saving ? '创建中…' : `新建${tab === 'task' ? '任务' : tab === 'list' ? '清单' : '习惯'}` }}</button>
      </form>

      <div class="existing-data">
        <h4>已有{{ tab === 'task' ? '任务' : tab === 'list' ? '清单' : '习惯' }}</h4>
        <template v-if="tab === 'list'">
          <div v-for="list in store.lists" :key="list.id" class="data-row">
            <input class="list-color-editor" type="color" :value="list.color" :disabled="changingListId === list.id" title="修改清单颜色" :aria-label="`修改${list.name}的颜色`" @change="onListColor(list, $event)">
            {{ list.name }}<small v-if="list.archived">已归档</small>
          </div>
        </template>
        <template v-else-if="tab === 'task'">
          <div v-for="task in store.tasks" :key="task.id" class="data-row" :class="{ done: task.completed }"><span class="color-dot" :style="{ background: listColor(task.listId) }"></span>{{ task.title }}<small v-if="task.completed">已完成</small></div>
        </template>
        <template v-else>
          <div v-for="habit in store.habits" :key="habit.id" class="data-row"><span class="color-dot" :style="{ background: habit.color }"></span>{{ habit.name }}<small v-if="habit.archived">已归档</small></div>
        </template>
        <p v-if="(tab === 'list' && !store.lists.length) || (tab === 'task' && !store.tasks.length) || (tab === 'habit' && !store.habits.length)" class="muted empty">暂无数据</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.overlay .data-dialog { max-width: 520px; max-height: calc(100vh - 32px); overflow-y: auto; text-align: left; }
.dialog-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.dialog-head h3 { margin: 0; }
.dialog-head p { margin: 4px 0 0; font-size: 12px; }
.close-btn { border: 0; background: transparent; color: var(--text-2); font-size: 25px; cursor: pointer; }
.data-tabs { display: flex; gap: 5px; margin: 18px 0; padding: 4px; border-radius: 10px; background: var(--bg); }
.data-tabs button { flex: 1; padding: 7px; border: 0; border-radius: 7px; background: transparent; color: var(--text-2); font: inherit; cursor: pointer; }
.data-tabs button.active { background: var(--surface); color: var(--primary); box-shadow: var(--shadow-card); }
select.input, textarea.input { width: 100%; border: 1px solid var(--border); border-radius: 10px; background: var(--bg); color: var(--text); padding: 10px 12px; font: inherit; font-size: 14px; }
textarea.input { resize: vertical; }
.field small { display: block; margin-top: 6px; }
.color-row { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; color: var(--text-2); font-size: 13px; }
.color-row input { width: 42px; height: 32px; padding: 2px; border: 1px solid var(--border); border-radius: 7px; background: var(--bg); }
.create-form > .btn { width: 100%; }
.form-hint { color: var(--text-2); font-size: 13px; }
.form-error { color: var(--danger); font-size: 13px; }
.existing-data { margin-top: 22px; padding-top: 16px; border-top: 1px solid var(--border); }
.existing-data h4 { margin: 0 0 8px; font-size: 13px; }
.data-row { display: flex; align-items: center; gap: 8px; padding: 7px 2px; border-bottom: 1px solid var(--border); font-size: 13px; }
.data-row.done { color: var(--text-2); text-decoration: line-through; }
.data-row small { margin-left: auto; color: var(--text-2); }
.color-dot { width: 9px; height: 9px; border-radius: 50%; }
.list-color-editor { width: 28px; height: 24px; padding: 1px; border: 1px solid var(--border); border-radius: 6px; background: transparent; cursor: pointer; }
.empty { margin: 12px 0 0; }
</style>
