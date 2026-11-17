import { createRouter, createWebHistory } from 'vue-router'
import FocusView from '../views/FocusView.vue'
import RecordsView from '../views/RecordsView.vue'
import CalendarView from '../views/CalendarView.vue'
import StatsView from '../views/StatsView.vue'
import LoginView from '../views/LoginView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'focus', component: FocusView, meta: { width: 'focus' } },
    { path: '/records', name: 'records', component: RecordsView },
    { path: '/calendar', name: 'calendar', component: CalendarView, meta: { width: 'calendar' } },
    { path: '/stats', name: 'stats', component: StatsView, meta: { width: 'wide' } },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.checkStatus()
  if (!auth.initialized && to.name !== 'login') return { name: 'login' }
  if (auth.initialized && !auth.authenticated && to.name !== 'login') return { name: 'login' }
  if (auth.authenticated && to.name === 'login') return { name: 'focus' }
  return true
})

export default router
