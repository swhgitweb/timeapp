import { createRouter, createWebHistory } from 'vue-router'
import FocusView from '../views/FocusView.vue'
import RecordsView from '../views/RecordsView.vue'

// MVP 只有两个路由（文档第 3 节）
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'focus', component: FocusView },
    { path: '/records', name: 'records', component: RecordsView },
  ],
})

export default router
