import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { logout as logoutApi } from '@/api/auth'

const storage = window.sessionStorage

export const useUserStore = defineStore('user', () => {
  const token = ref(storage.getItem('token') || '')
  const refreshToken = ref(storage.getItem('refreshToken') || '')
  const userInfo = ref(JSON.parse(storage.getItem('userInfo') || '{}'))

  const isAdmin = computed(() => userInfo.value.roleCode === 'ADMIN')
  const isDoctor = computed(() => userInfo.value.roleCode === 'DOCTOR')

  const setToken = (accessToken, refresh) => {
    token.value = accessToken
    storage.setItem('token', accessToken)
    if (refresh) {
      refreshToken.value = refresh
      storage.setItem('refreshToken', refresh)
    }
  }

  const setUserInfo = (info) => {
    userInfo.value = info
    storage.setItem('userInfo', JSON.stringify(info))
  }

  const logout = async () => {
    try {
      await logoutApi()
    } catch (error) {
      console.warn('Logout request failed', error)
    }
    token.value = ''
    refreshToken.value = ''
    userInfo.value = {}
    storage.removeItem('token')
    storage.removeItem('refreshToken')
    storage.removeItem('userInfo')
  }

  return { token, refreshToken, userInfo, isAdmin, isDoctor, setToken, setUserInfo, logout }
})
