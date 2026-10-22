<template>
  <div class="app-square catalog-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <el-icon class="title-icon"><Grid /></el-icon>
          发现角色
        </h1>
        <p class="page-subtitle">找一位对话伙伴，换个角度看问题。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" size="large" @click="goToCreateApp">
          <el-icon><Plus /></el-icon>
          创建角色
        </el-button>
      </div>
    </div>

    <!-- 搜索和筛选 -->
    <div class="search-section">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索角色名称或介绍..."
        size="large"
        class="search-input"
        @input="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- 应用网格 -->
    <div class="apps-container" v-loading="loading">
      <transition-group name="app-card" tag="div" class="apps-grid">
        <RoleCard v-for="app in apps" :key="app.appId" :app="app" :curated="app.priority > 0" @start="handleChat(app)" />
          </transition-group>

      <!-- 空状态 -->
      <el-empty
        v-if="!loading && apps.length === 0"
        description="暂无角色"
        class="empty-state"
      >
        <el-button type="primary" @click="goToCreateApp">创建第一个角色</el-button>
      </el-empty>
    </div>

    <!-- 分页 -->
    <div class="pagination-container" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 48, 96]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 浮动聊天框 -->

  </div>
</template>

<script setup lang="ts">
import RoleCard from '@/components/RoleCard.vue'
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Grid, Plus, Search, ChatDotRound, ChatLineRound,
  User, Clock
} from '@element-plus/icons-vue'
import { listAllAppVoByPage, getAppVoById } from '@/api/appController'
import type { AppVO } from '@/types/api'

const router = useRouter()

// 响应式数据
const apps = ref<AppVO[]>([])
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)
const loading = ref(false)
const searchKeyword = ref('')

// 方法
const loadApps = async () => {
  loading.value = true
  try {
    const response = await listAllAppVoByPage({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      appName: searchKeyword.value || undefined,
      sortField: 'create_time',
      sortOrder: 'descend'
    })
    if (response.data?.code === 0) {
      apps.value = response.data?.data?.records || []
      total.value = response.data?.data?.total || 0
    }
  } catch (error) {
    ElMessage.error('加载应用列表失败')
  } finally {
    loading.value = false
  }
}

const handleChat = async (app: AppVO) => {
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

const goToCreateApp = () => {
  router.push('/create-app')
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

const handleSearch = () => {
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

// 监听搜索关键词变化
watch(searchKeyword, () => {
  if (searchKeyword.value === '') {
    handleSearch()
  }
}, { debounce: 500 })
</script>
