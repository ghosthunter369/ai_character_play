import type { Router, RouteLocationNormalized, NavigationGuardNext } from 'vue-router'
import { useUserStore } from '@/stores'
import { message } from 'ant-design-vue'

export function setupRouterGuard(router: Router) {
  // 路由前置守卫
  router.beforeEach((to: RouteLocationNormalized, from: RouteLocationNormalized, next: NavigationGuardNext) => {
    const userStore = useUserStore()
    
    // 检查是否需要登录
    if (to.meta.requireAuth && !userStore.isLoggedIn) {
      message.warning('请先登录')
      next('/user/login')
      return
    }
    
    // 检查管理员权限
    if (to.meta.access === 'canAdmin' && userStore.user?.userRole !== 'admin') {
      message.error('无权限访问')
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