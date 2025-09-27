import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: 'http://localhost:8123/api',
  timeout: 10000,
  withCredentials: true, // 重要：支持 session cookie
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 移除 token 相关逻辑，使用 session 认证
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    const { data } = response
    // 处理未登录状态
    if (data.code === 40100) {
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        ElMessage.warning('请先登录')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return data
  },
  (error) => {
    // 统一错误处理
    if (error.response?.status === 401 || error.response?.data?.code === 40100) {
      if (!window.location.pathname.includes('/user/login')) {
        ElMessage.warning('请先登录')
        window.location.href = '/user/login'
      }
    } else {
      ElMessage.error(error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

export default api