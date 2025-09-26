import axios from 'axios'
import { ElMessage } from 'element-plus'


// 创建 Axios 实例
const myAxios = axios.create({
    baseURL: "http://localhost:8123/api",
    timeout: 600000,
    withCredentials: true,
})

// 全局请求拦截器
myAxios.interceptors.request.use(
    (config) => {
        // 可以在这里统一加 token 或其他处理
        return config
    },
    (error) => {
        return Promise.reject(error)
    },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
    (response) => {
        const { data } = response
        // 未登录
        if (data.code === 40100) {
            if (
                !response.request.responseURL.includes('user/get/login') &&
                !window.location.pathname.includes('/user/login')
            ) {
                ElMessage.warning('请先登录')
                window.location.href = `/user/login?redirect=${window.location.href}`
            }
        }
        return response
    },
    (error) => {
        // 请求出错时的统一提示
        ElMessage.error(error.message || '请求失败')
        return Promise.reject(error)
    },
)

export default myAxios
