<template>
  <div class="basic-layout">
    <el-container class="layout-container">
      <el-header class="header">
        <div class="header-content">
          <div class="logo">
            <el-icon class="logo-icon"><ChatDotRound /></el-icon>
            <span class="logo-text">AI Chat</span>
          </div>
          
          <div class="nav-center">
            <el-menu
              :default-active="activeIndex"
              mode="horizontal"
              background-color="#001529"
              text-color="#fff"
              active-text-color="#ffd04b"
              @select="handleMenuSelect"
            >
              <el-menu-item index="/app-square">
                <el-icon><Grid /></el-icon>
                <span>应用广场</span>
              </el-menu-item>
              <el-menu-item v-if="isLoggedIn" index="/create-app">
                <el-icon><Plus /></el-icon>
                <span>创建应用</span>
              </el-menu-item>
              <el-menu-item v-if="isAdmin" index="/admin">
                <el-icon><Setting /></el-icon>
                <span>管理应用</span>
              </el-menu-item>
              <el-menu-item v-if="isLoggedIn" index="/my-apps">
                <el-icon><Collection /></el-icon>
                <span>我的应用</span>
              </el-menu-item>
            </el-menu>
          </div>
          
          <div class="nav-right">
            <el-input
              v-if="isLoggedIn"
              v-model="searchKeyword"
              placeholder="搜索应用..."
              class="search-input"
              :prefix-icon="Search"
              @keyup.enter="handleSearch"
            />
            
            <div v-if="!isLoggedIn" class="auth-buttons">
              <el-button type="primary" @click="goToLogin">登录</el-button>
            </div>
            
            <div v-else class="user-info">
              <el-dropdown @command="handleUserCommand">
                <div class="user-avatar">
                  <el-avatar 
                    :src="userInfo?.userAvatar" 
                    :size="32"
                    :icon="UserFilled"
                  >
                    {{ userInfo?.userName?.charAt(0) || userInfo?.userAccount?.charAt(0) || 'U' }}
                  </el-avatar>
                  <span class="user-name">{{ userInfo?.userName || userInfo?.userAccount }}</span>
                </div>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="profile">
                      <el-icon><User /></el-icon>
                      个人中心
                    </el-dropdown-item>
                    <el-dropdown-item command="logout" divided>
                      <el-icon><SwitchButton /></el-icon>
                      退出登录
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>
      </el-header>
      
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  ChatDotRound, 
  Grid, 
  Plus, 
  Setting, 
  Collection, 
  Search, 
  UserFilled, 
  User, 
  SwitchButton 
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { userService } from '@/services'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeIndex = ref('/app-square')
const searchKeyword = ref('')

// 计算属性
const isLoggedIn = computed(() => userStore.isLoggedIn)
const isAdmin = computed(() => userStore.user?.userRole === 'admin')
const userInfo = computed(() => userStore.user)

// 监听路由变化更新激活菜单
watch(() => route.path, (newPath) => {
  activeIndex.value = newPath
}, { immediate: true })

// 方法
const goToLogin = () => {
  router.push('/user/login')
}

const handleMenuSelect = (index: string) => {
  router.push(index)
}

const handleSearch = () => {
  if (searchKeyword.value.trim()) {
    // 搜索逻辑，可以跳转到搜索页面或触发搜索
    console.log('搜索关键词:', searchKeyword.value)
    ElMessage.info(`搜索: ${searchKeyword.value}`)
  }
}

const handleUserCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await userService.logout()
      userStore.clearUser()
      ElMessage.success('退出登录成功')
      router.push('/')
    } catch (error) {
      ElMessage.error('退出登录失败')
    }
  } else if (command === 'profile') {
    ElMessage.info('个人中心功能开发中')
  }
}

// 初始化用户信息
onMounted(async () => {
  if (!userStore.user && userStore.token) {
    try {
      const response = await userService.getCurrentUser()
      if (response.code === 0) {
        userStore.setUser(response.data)
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
    }
  }
})
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
}

.layout-container {
  min-height: 100vh;
}

.header {
  background: #001529;
  padding: 0;
  height: 64px;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 24px;
}

.logo {
  display: flex;
  align-items: center;
  color: white;
  font-size: 20px;
  font-weight: bold;
  cursor: pointer;
}

.logo-icon {
  margin-right: 8px;
  font-size: 24px;
}

.logo-text {
  font-size: 20px;
}

.nav-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-input {
  width: 200px;
}

.user-avatar {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-avatar:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.user-name {
  color: white;
  font-size: 14px;
}

.main-content {
  padding: 24px;
  background: #f5f7fa;
  min-height: calc(100vh - 64px);
}

:deep(.el-menu--horizontal) {
  border-bottom: none;
}

:deep(.el-menu--horizontal .el-menu-item) {
  height: 64px;
  line-height: 64px;
  border-bottom: 2px solid transparent;
}

:deep(.el-menu--horizontal .el-menu-item.is-active) {
  border-bottom-color: #ffd04b;
}
</style>