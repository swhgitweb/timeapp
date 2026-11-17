<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { initializeAccount, login } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const isInitialize = computed(() => !auth.initialized)
const username = ref('')
const password = ref('')
const displayName = ref('')
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const payload = { username: username.value.trim(), password: password.value, displayName: displayName.value.trim(), timezone }
    const user = isInitialize.value ? await initializeAccount(payload) : await login(payload)
    auth.setAuthenticated(user)
    await router.replace('/')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="card auth-card">
      <div class="auth-brand">番茄专注</div>
      <h1>{{ isInitialize ? '初始化账号' : '欢迎回来' }}</h1>
      <p class="muted">{{ isInitialize ? '创建本应用的第一个账号，旧专注记录会自动归入此账号。' : '登录后同步专注记录、常用专注与日历设置。' }}</p>
      <form @submit.prevent="submit">
        <div class="field">
          <label>用户名</label>
          <input v-model="username" autocomplete="username" required minlength="3" maxlength="50" placeholder="3-50 位字母、数字或 _ . -" />
        </div>
        <div v-if="isInitialize" class="field">
          <label>显示名称（可选）</label>
          <input v-model="displayName" maxlength="50" autocomplete="nickname" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="password" type="password" :autocomplete="isInitialize ? 'new-password' : 'current-password'" required minlength="1" maxlength="72" />
        </div>
        <p v-if="error" class="auth-error">{{ error }}</p>
        <button class="btn btn-primary btn-block" :disabled="loading">{{ loading ? '请稍候…' : isInitialize ? '创建并登录' : '登录' }}</button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: grid; place-items: center; padding: 24px 16px; background: var(--bg-soft); }
.auth-card { width: 100%; max-width: 420px; padding: 32px; border-radius: 16px; box-shadow: var(--shadow); }
.auth-brand { color: var(--primary); font-weight: 700; letter-spacing: .08em; margin-bottom: 24px; }
h1 { margin: 0 0 8px; font-size: 26px; }
form { margin-top: 24px; }
.auth-error { color: var(--danger); font-size: 13px; }
</style>
