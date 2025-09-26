<template>
  <div class="basic-layout">
    <el-container class="layout-container">
      <!-- 顶部导航栏 -->
      <el-header class="layout-header">
        <div class="header-content">
          <div class="header-left">
            <el-menu
              :default-active="activeMenu"
              mode="horizontal"
              class="nav-menu"
              @select="handleMenuSelect"
            >
              <el-menu-item index="/featured-apps">
                <el-icon><Star /></el-icon>
                精选应用
              </el-menu-item>
              <el-menu-item index="/app-square">
                <el-icon><Grid /></el-icon>
                应用广场
              </el-menu-item>
              <el-menu-item index="/my-apps">
                <el-icon><Folder /></el-icon>
                我的应用
              </el-menu-item>
              <el-menu-item index="/create-app">
                <el-icon><Plus /></el-icon>
                创建应用
              </el-menu-item>
              <el-menu-item index="/chat">
                <el-icon><ChatLineRound /></el-icon>
                聊天对话
              </el-menu-item>
            </el-menu>
          </div>
          
          <div class="header-right">
            <!-- 用户信息区域 -->
            <div class="user-section">
              <el-tooltip content="点击查看个人信息" placement="bottom">
                <div class="user-info" @click="showUserMenu = !showUserMenu">
                  <el-avatar :size="36" :src="userStore.user?.userAvatar" class="user-avatar">
                    <el-icon><User /></el-icon>
                  </el-avatar>
                  <div class="user-details">
                    <div class="username">{{ userStore.user?.userName || '用户' }}</div>
                    <div class="user-role">{{ getRoleText(userStore.user?.userRole) }}</div>
                  </div>
                  <el-icon class="dropdown-icon" :class="{ rotated: showUserMenu }">
                    <ArrowDown />
                  </el-icon>
                </div>
              </el-tooltip>
              
              <!-- 用户菜单 -->
              <transition name="menu-fade">
                <div v-if="showUserMenu" class="user-menu" @click.stop>
                  <div class="menu-header">
                    <el-avatar :size="48" :src="userStore.user?.userAvatar">
                      <el-icon><User /></el-icon>
                    </el-avatar>
                    <div class="user-info-detail">
                      <div class="name">{{ userStore.user?.userName || '用户' }}</div>
                      <div class="account">{{ userStore.user?.userAccount }}</div>
                    </div>
                  </div>
                  
                  <el-divider style="margin: 12px 0;" />
                  
                  <div class="menu-items">
                    <div class="menu-item" @click="handleUserCommand('profile')">
                      <el-icon><User /></el-icon>
                      <span>个人资料</span>
                    </div>
                    <div class="menu-item" @click="handleUserCommand('settings')">
                      <el-icon><Setting /></el-icon>
                      <span>设置</span>
                    </div>
                    <div class="menu-item" @click="handleUserCommand('theme')">
                      <el-icon><Sunny /></el-icon>
                      <span>主题设置</span>
                    </div>
                    <div class="menu-divider"></div>
                    <div class="menu-item logout" @click="handleUserCommand('logout')">
                      <el-icon><SwitchButton /></el-icon>
                      <span>退出登录</span>
                    </div>
                  </div>
                </div>
              </transition>
            </div>
          </div>
        </div>
      </el-header>

      <!-- 主内容区域 -->
      <el-main class="layout-main">
        <div class="main-content">
          <router-view v-slot="{ Component }">
            <transition name="page" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </el-main>
    </el-container>
    
    <!-- 点击外部关闭菜单 -->
    <div v-if="showUserMenu" class="menu-overlay" @click="showUserMenu = false"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound, Star, Folder, Plus, ChatLineRound, User,
  ArrowDown, Setting, SwitchButton, Sunny, Grid
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 响应式数据
const showUserMenu = ref(false)

// 计算当前激活的菜单项
const activeMenu = computed(() => {
  return route.path
})

// 方法
const goHome = () => {
  router.push('/featured-apps')
}

const goToChat = () => {
  router.push('/chat')
}

const handleMenuSelect = (index: string) => {
  router.push(index)
  showUserMenu.value = false
}

const getRoleText = (role?: string) => {
  switch (role) {
    case 'admin':
      return '管理员'
    case 'user':
      return '普通用户'
    default:
      return '用户'
  }
}

const handleUserCommand = async (command: string) => {
  showUserMenu.value = false
  
  switch (command) {
    case 'profile':
      ElMessage.info('个人资料功能开发中...')
      break
    case 'settings':
      ElMessage.info('设置功能开发中...')
      break
    case 'theme':
      ElMessage.info('主题设置功能开发中...')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm(
          '确定要退出登录吗？退出后需要重新登录才能使用。', 
          '退出确认', 
          {
            confirmButtonText: '确定退出',
            cancelButtonText: '取消',
            type: 'warning',
            customClass: 'logout-confirm'
          }
        )
        
        // 调用后端退出接口
        try {
          // await userService.logout()
        } catch (error) {
          console.error('退出登录接口调用失败:', error)
        }
        
        // 清除本地状态
        userStore.clearUser()
        localStorage.removeItem('token')
        ElMessage.success('已安全退出登录')
        router.push('/user/login')
      } catch {
        // 用户取消
      }
      break
  }
}

// 点击外部关闭菜单
const handleClickOutside = (event: Event) => {
  const target = event.target as HTMLElement
  if (!target.closest('.user-section')) {
    showUserMenu.value = false
  }
}

// 生命周期
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.layout-container {
  min-height: 100vh;
}

/* 头部样式 */
.layout-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
  padding: 0;
  height: 64px;
  position: sticky;
  top: 0;
  z-index: 1000;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  flex: 1;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.logo:hover {
  transform: scale(1.05);
}

.logo-icon {
  color: #667eea;
}

.logo-text {
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea, #764ba2);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-menu {
  border: none;
  background: transparent;
  flex: 1;
}

.nav-menu :deep(.el-menu-item) {
  border-bottom: 2px solid transparent;
  color: #666;
  font-weight: 500;
  transition: all 0.3s ease;
}

.nav-menu :deep(.el-menu-item:hover) {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.nav-menu :deep(.el-menu-item.is-active) {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
  border-bottom-color: #667eea;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.chat-btn {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  border-radius: 20px;
  padding: 8px 20px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.chat-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
}

/* 用户信息区域 */
.user-section {
  position: relative;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 16px;
  border-radius: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.user-avatar {
  border: 2px solid rgba(255, 255, 255, 0.3);
  transition: all 0.3s ease;
}

.user-info:hover .user-avatar {
  border-color: rgba(255, 255, 255, 0.6);
}

.user-details {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.username {
  font-weight: 600;
  color: white;
  font-size: 14px;
  line-height: 1.2;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1;
}

.dropdown-icon {
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  transition: transform 0.3s ease;
}

.dropdown-icon.rotated {
  transform: rotate(180deg);
}

/* 用户菜单 */
.user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 280px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
  z-index: 1000;
}

.menu-header {
  padding: 20px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info-detail .name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.user-info-detail .account {
  font-size: 12px;
  opacity: 0.8;
}

.menu-items {
  padding: 8px 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
  color: #333;
}

.menu-item:hover {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.menu-item.logout {
  color: #f56565;
}

.menu-item.logout:hover {
  background: rgba(245, 101, 101, 0.1);
  color: #e53e3e;
}

.menu-divider {
  height: 1px;
  background: #f0f0f0;
  margin: 8px 20px;
}

.menu-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: transparent;
}

/* 菜单动画 */
.menu-fade-enter-active,
.menu-fade-leave-active {
  transition: all 0.3s ease;
}

.menu-fade-enter-from {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}

.menu-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}

/* 主内容区域 */
.layout-main {
  padding: 0;
  background: transparent;
}

.main-content {
  min-height: calc(100vh - 64px);
}

/* 页面切换动画 */
.page-enter-active,
.page-leave-active {
  transition: all 0.3s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.page-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .header-content {
    padding: 0 16px;
  }
  
  .header-left {
    gap: 24px;
  }
}

@media (max-width: 768px) {
  .layout-header {
    height: 56px;
  }
  
  .header-content {
    padding: 0 12px;
  }
  
  .header-left {
    gap: 16px;
  }
  
  .nav-menu {
    display: none;
  }
  
  .logo-text {
    font-size: 20px;
  }
  
  .username {
    display: none;
  }
  
  .chat-btn {
    padding: 6px 16px;
    font-size: 14px;
  }
  
  .main-content {
    min-height: calc(100vh - 56px);
  }
}

@media (max-width: 480px) {
  .header-content {
    padding: 0 8px;
  }
  
  .header-right {
    gap: 8px;
  }
  
  .chat-btn span {
    display: none;
  }
  
  .chat-btn {
    padding: 8px;
    border-radius: 50%;
  }
}

/* 下拉菜单样式 */
:deep(.el-dropdown-menu) {
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 8px 0;
}

:deep(.el-dropdown-menu__item) {
  padding: 8px 16px;
  font-size: 14px;
  transition: all 0.3s ease;
}

:deep(.el-dropdown-menu__item:hover) {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

:deep(.el-dropdown-menu__item.is-divided) {
  border-top: 1px solid #f0f0f0;
  margin-top: 4px;
  padding-top: 12px;
}
</style>