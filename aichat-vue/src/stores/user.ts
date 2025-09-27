import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userService } from '@/services/userService'

export interface User {
  id: number
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
    // 使用 session 认证，不需要存储 token 到 localStorage
  }

  const clearUser = () => {
    user.value = null
    token.value = ''
    isLoggedIn.value = false
    // 清除可能存在的旧 token
    localStorage.removeItem('token')
  }

  // 初始化用户状态 - 通过 session 验证登录状态
  const initUser = async () => {
    try {
      // 直接尝试获取当前登录用户（通过 session）
      const response = await userService.getCurrentUser()
      if (response.code === 0 && response.data) {
        setUser(response.data)
        console.log('用户状态已恢复:', response.data)
      } else {
        // session 无效，清除用户状态
        clearUser()
      }
    } catch (error) {
      console.error('验证用户登录状态失败:', error)
      // 网络错误或 session 无效，清除用户状态
      clearUser()
    }
  }

  return {
    user,
    token,
    isLoggedIn,
    setUser,
    setToken,
    clearUser,
    initUser
  }
})