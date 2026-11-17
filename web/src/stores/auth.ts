import { defineStore } from 'pinia'
import { getAuthStatus, type UserVO } from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    statusLoaded: false,
    initialized: false,
    user: null as UserVO | null,
  }),
  getters: {
    authenticated: (state) => state.user !== null,
  },
  actions: {
    async checkStatus(force = false) {
      if (this.statusLoaded && !force) return
      const status = await getAuthStatus()
      this.initialized = status.initialized
      this.user = status.user
      this.statusLoaded = true
    },
    setAuthenticated(user: UserVO) {
      this.initialized = true
      this.user = user
      this.statusLoaded = true
    },
    markLoggedOut() {
      this.user = null
    },
  },
})
