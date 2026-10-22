<template>
  <div class="featured-apps-page catalog-page">
    <div class="page-container">
      <!-- 页面头部 -->
      <div class="page-header">
        <div class="header-content">
          <div class="header-left">
            <h1 class="page-title">
              <el-icon class="title-icon"><Star /></el-icon>
              精选角色
            </h1>
            <p class="page-subtitle">从一个问题开始，和不同的角色聊聊。</p>
          </div>
        </div>
      </div>

      <!-- 搜索区域 -->
      <div class="search-section">
        <div class="search-container">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索角色名称..."
            size="large"
            class="search-input"
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
      </div>

      <!-- 应用列表 -->
      <div class="apps-section" v-loading="loading">
        <div class="apps-grid">
          <transition-group name="app-item" tag="div" class="grid-container">
        <RoleCard v-for="app in apps" :key="app.appId" :app="app" :curated="true" @start="startChat(app)" />
          </transition-group>
        </div>

        <!-- 空状态 -->
        <el-empty v-if="!loading && apps.length === 0" description="暂无精选角色">
          <el-button type="primary" @click="goToCreate">创建第一个角色</el-button>
        </el-empty>
      </div>

      <!-- 分页 -->
      <div class="pagination-section" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[8, 16, 24, 32]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import RoleCard from '@/components/RoleCard.vue'
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Star, Plus, Search, ChatDotRound, ChatLineRound, ArrowRight,
  View, User, Clock
} from '@element-plus/icons-vue'
import { listGoodAppVoByPage, getAppVoById } from '@/api/appController'
import type { AppVO, AppQueryRequest } from '@/types/api'

const router = useRouter()

// 响应式数据
const apps = ref<AppVO[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(8)
const total = ref(0)
const searchKeyword = ref('')

// 方法
const loadApps = async () => {
  loading.value = true
  try {
    const params: AppQueryRequest = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      appName: searchKeyword.value || undefined,
      sortField: 'create_time',
      sortOrder: 'descend'
    }

    const response = await listGoodAppVoByPage(params)
    if (response.data?.code === 0) {
      apps.value = response.data?.data?.records || []
      total.value = response.data?.data?.total || 0
    }
  } catch (error) {
    ElMessage.error('加载精选角色失败')
  } finally {
    loading.value = false
  }
}

const goToCreate = () => {
  router.push('/create-app')
}

const goToAppSquare = () => {
  router.push('/app-square')
}

const startChat = async (app: AppVO) => {
  try {
    // 先获取应用详情
    const response = await getAppVoById({ id: app.id || app.appId })
    if (response.data?.code === 0) {
      const appDetail = response.data.data
      // 跳转到聊天页面，传递应用详情
      router.push({
        path: '/chat',
        query: {
          appId: appDetail.id || appDetail.appId,
          appName: appDetail.appName,
          initPrompt: appDetail.initPrompt,
          prologue: appDetail.prologue
        }
      })
      ElMessage.success(`开始与 ${appDetail.appName} 对话`)
    } else {
      ElMessage.error('获取应用信息失败')
    }
  } catch (error) {
    console.error('获取应用详情失败:', error)
    ElMessage.error('获取应用信息失败，请稍后重试')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadApps()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  loadApps()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadApps()
}

const handleImageError = (event: Event) => {
  const target = event.target as HTMLImageElement
  target.style.display = 'none'
}

const formatTime = (time: string) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return '今天'
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString()
  }
}



// 生命周期
onMounted(() => {
  loadApps()
})
</script>
