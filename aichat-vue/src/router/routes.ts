import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/featured-apps'
  },

  {
    path: '/user',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      {
        path: 'login',
        name: 'Login',
        component: () => import('@/pages/User/Login.vue')
      },
      {
        path: 'register',
        name: 'Register',
        component: () => import('@/pages/User/Register.vue')
      }
    ]
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    children: [
      {
        path: '/featured-apps',
        name: 'FeaturedApps',
        component: () => import('@/pages/FeaturedApps.vue'),
        meta: { title: '精选应用' }
      },
      {
        path: '/app-square',
        name: 'AppSquare',
        component: () => import('@/pages/AppSquare.vue'),
        meta: { title: '应用广场' }
      },
      {
        path: '/welcome',
        name: 'Welcome',
        component: () => import('@/pages/Welcome.vue'),
        meta: { title: '欢迎页' }
      },
      {
        path: '/create-app',
        name: 'CreateApp',
        component: () => import('@/pages/CreateApp.vue'),
        meta: { title: '创建应用', requireAuth: true }
      },
      {
        path: '/my-apps',
        name: 'MyApps',
        component: () => import('@/pages/MyApps.vue'),
        meta: { title: '我的应用', requireAuth: true }
      },
      {
        path: '/chat',
        name: 'Chat',
        component: () => import('@/pages/Chat.vue'),
        meta: { title: 'AI聊天' }
      },
      {
        path: '/admin/user',
        name: 'UserAdmin',
        component: () => import('@/pages/Admin/User.vue'),
        meta: { title: '用户管理', access: 'canAdmin' }
      },
      {
        path: '/admin/app',
        name: 'AppAdmin',
        component: () => import('@/pages/Admin/App.vue'),
        meta: { title: '应用管理', access: 'canAdmin' }
      },
      {
        path: '/admin',
        redirect: '/admin/app'
      },
      {
        path: '/test-login',
        name: 'TestLogin',
        component: () => import('@/pages/TestLogin.vue'),
        meta: { title: '登录状态测试' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/pages/404.vue')
  }
]