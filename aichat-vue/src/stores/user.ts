import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface User {
  id: string
  userAccount: string
  userName: string
  userAvatar?: string
  userProfile?: string
  userRole: string
  createTime?: string
  updateTime?: string
}

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const token = ref<string>('')
  const isLoggedIn = ref(false)

  const setUser = (userData: User) => {
    user.value = userData
    isLoggedIn.value = true
  }

  const setToken = (newToken: string) => {
    token.value = newToken
  }

  const clearUser = () => {
    user.value = null
    token.value = ''
    isLoggedIn.value = false
  }

  return {
    user,
    token,
    isLoggedIn,
    setUser,
    setToken,
    clearUser
  }
})