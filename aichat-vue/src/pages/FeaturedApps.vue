<template>
  <div class="featured-apps-page">
    <div class="page-container">
      <!-- 页面头部 -->
      <div class="page-header">
        <div class="header-content">
          <div class="header-left">
            <h1 class="page-title">
              <el-icon class="title-icon"><Star /></el-icon>
              精选应用
            </h1>
            <p class="page-subtitle">发现精选的AI智能应用，开启智能对话之旅</p>
          </div>
        </div>
      </div>

      <!-- 搜索区域 -->
      <div class="search-section">
        <div class="search-container">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索精选应用..."
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
            <div
              v-for="app in apps"
              :key="`featured-app-${app.id || app.appId}`"
              class="app-card"
              @click="startChat(app)"
            >
              <div class="card-cover">
                <img v-if="app.cover" :src="app.cover" :alt="app.appName" @error="handleImageError" />
                <div v-else class="default-cover">
                  <el-icon size="48"><ChatDotRound /></el-icon>
                </div>
                <div class="card-overlay">
                  <el-button type="primary" round>
                    <el-icon><ChatLineRound /></el-icon>
                    开始聊天
                  </el-button>
                </div>
                <el-tag class="featured-tag" type="warning" size="small">
                  <el-icon><Star /></el-icon>
                  精选
                </el-tag>
                <!-- 查看更多按钮 -->
                <div class="view-more-btn" @click.stop="goToAppSquare">
                  <el-button type="info" size="small" round>
                    <el-icon><ArrowRight /></el-icon>
                    查看更多
                  </el-button>
                </div>
              </div>
              
              <div class="card-content">
                <div class="card-header">
                  <h3 class="app-name">{{ app.appName }}</h3>
                  <div class="app-stats">
                    <el-icon><View /></el-icon>
                    <span>{{ getRandomViews() }}</span>
                  </div>
                </div>
                
                <p class="app-description">{{ app.description || '暂无描述' }}</p>
                
                <div class="card-footer">
                  <div class="app-meta">
                    <span class="meta-item">
                      <el-icon><User /></el-icon>
                      {{ app.userName || '匿名用户' }}
                    </span>
                    <span class="meta-item">
                      <el-icon><Clock /></el-icon>
                      {{ formatTime(app.createTime) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </transition-group>
        </div>

        <!-- 空状态 -->
        <el-empty v-if="!loading && apps.length === 0" description="暂无精选应用">
          <el-button type="primary" @click="goToCreate">创建第一个应用</el-button>
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
    ElMessage.error('加载精选应用失败')
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

const getRandomViews = () => {
  return Math.floor(Math.random() * 1000) + 100
}

// 生命周期
onMounted(() => {
  loadApps()
})
</script>

<style scoped>
.featured-apps-page {
  min-height: calc(100vh - 64px);
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.page-container {
  max-width: 1400px;
  margin: 0 auto;
}

/* 页面头部 */
.page-header {
  margin-bottom: 32px;
}

.header-content {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-icon {
  color: #ffd700;
}

.page-subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
}

/* 搜索区域 */
.search-section {
  margin-bottom: 32px;
}

.search-container {
  display: flex;
  justify-content: center;
}

.search-input {
  max-width: 600px;
  width: 100%;
}

.search-input :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 24px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.search-input :deep(.el-input__wrapper:hover) {
  border-color: #667eea;
  box-shadow: 0 8px 32px rgba(102, 126, 234, 0.2);
}

.search-input :deep(.el-input__wrapper.is-focus) {
  border-color: #667eea;
  box-shadow: 0 8px 32px rgba(102, 126, 234, 0.3);
}

/* 应用网格 */
.apps-section {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  min-height: 400px;
}

.grid-container {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.app-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  cursor: pointer;
  position: relative;
}

.app-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.2);
}

.card-cover {
  position: relative;
  height: 180px;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.app-card:hover .card-cover img {
  transform: scale(1.1);
}

.default-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: white;
}

.card-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.app-card:hover .card-overlay {
  opacity: 1;
}

.featured-tag {
  position: absolute;
  top: 12px;
  left: 12px;
  background: linear-gradient(135deg, #ffd700, #ffb347);
  border: none;
  color: white;
  font-weight: 600;
}

.view-more-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.app-card:hover .view-more-btn {
  opacity: 1;
}

.card-content {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.app-name {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0;
  flex: 1;
  margin-right: 12px;
}

.app-stats {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #999;
  font-size: 12px;
}

.app-description {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}

.card-footer {
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.app-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 分页 */
.pagination-section {
  display: flex;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* 动画 */
.app-item-enter-active,
.app-item-leave-active {
  transition: all 0.3s ease;
}

.app-item-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.app-item-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .grid-container {
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  }
}

@media (max-width: 768px) {
  .featured-apps-page {
    padding: 16px;
  }
  
  .header-content {
    flex-direction: column;
    gap: 16px;
    text-align: center;
  }
  
  .page-title {
    font-size: 24px;
  }
  
  .grid-container {
    grid-template-columns: 1fr;
  }
  
  .search-input {
    max-width: 100%;
  }
}
</style>