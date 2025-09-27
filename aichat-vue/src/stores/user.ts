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
    localStorage.setItem('token', newToken)
  }

  const clearUser = () => {
    user.value = null
    token.value = ''
    isLoggedIn.value = false
    localStorage.removeItem('token')
  }

  // 初始化用户状态 - 从localStorage恢复token并验证
  const initUser = async () => {
    const savedToken = localStorage.getItem('token')
    if (savedToken) {
      token.value = savedToken
      try {
        // 验证token有效性并获取用户信息
        const response = await userService.getCurrentUser()
        if (response.code === 0 && response.data) {
          setUser(response.data)
          console.log('用户状态已恢复:', response.data)
        } else {
          // token无效，清除本地存储
          clearUser()
        }
      } catch (error) {
        console.error('验证用户登录状态失败:', error)
        // 网络错误或token无效，清除本地存储
        clearUser()
      }
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