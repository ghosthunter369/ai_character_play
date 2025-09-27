import type { Router, RouteLocationNormalized, NavigationGuardNext } from 'vue-router'
import { useUserStore } from '@/stores'
import { ElMessage } from 'element-plus'

export function setupRouterGuard(router: Router) {
  // 路由前置守卫
  router.beforeEach(async (to: RouteLocationNormalized, from: RouteLocationNormalized, next: NavigationGuardNext) => {
    const userStore = useUserStore()
    
    // 如果用户状态未初始化，先尝试通过 session 恢复用户状态
    if (!userStore.isLoggedIn && !userStore.user) {
      try {
        await userStore.initUser()
      } catch (error) {
        console.error('路由守卫中恢复用户状态失败:', error)
      }
    }
    
    // 检查是否需要登录
    if (to.meta.requireAuth && !userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      next('/user/login')
      return
    }
    
    // 检查管理员权限
    if (to.meta.access === 'canAdmin' && userStore.user?.userRole !== 'admin') {
      ElMessage.error('无权限访问')
      next('/')
      return
    }
    
    next()
  })

  // 路由后置守卫
  router.afterEach((to: RouteLocationNormalized) => {
    // 设置页面标题
    if (to.meta.title) {
      document.title = `${to.meta.title} - AI Chat`
    } else {
      document.title = 'AI Chat'
    }
  })
}