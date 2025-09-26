<template>
  <div class="app-square">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <el-icon class="title-icon"><Grid /></el-icon>
          应用广场
        </h1>
        <p class="page-subtitle">探索所有AI智能应用，找到最适合你的聊天伙伴</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" size="large" @click="goToCreateApp">
          <el-icon><Plus /></el-icon>
          创建应用
        </el-button>
      </div>
    </div>

    <!-- 搜索和筛选 -->
    <div class="search-section">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索应用名称或描述..."
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
        <div
          v-for="app in apps"
          :key="`app-square-${app.appId}`"
          class="app-card"
          @click="handleChat(app)"
        >
          <div class="card-cover">
            <img 
              v-if="app.cover" 
              :src="app.cover" 
              :alt="app.appName"
              @error="handleImageError"
            />
            <div v-else class="default-cover">
              <el-icon size="48"><ChatDotRound /></el-icon>
            </div>
            <div class="card-overlay">
              <el-button type="primary" size="large" round>
                <el-icon><ChatLineRound /></el-icon>
                开始聊天
              </el-button>
            </div>
          </div>
          
          <div class="card-content">
            <div class="card-header">
              <h3 class="app-name">{{ app.appName }}</h3>
              <el-tag v-if="app.priority > 0" type="warning" size="small">精选</el-tag>
            </div>
            
            <p class="app-description">{{ app.description || '暂无描述' }}</p>
            
            <div class="card-footer">
              <div class="author-info">
                <el-avatar :size="24" :src="app.userAvatar">
                  <el-icon><User /></el-icon>
                </el-avatar>
                <span class="author-name">{{ app.userName }}</span>
              </div>
              <div class="create-time">
                <el-icon><Clock /></el-icon>
                {{ formatTime(app.createTime) }}
              </div>
            </div>
          </div>
        </div>
      </transition-group>

      <!-- 空状态 -->
      <el-empty 
        v-if="!loading && apps.length === 0" 
        description="暂无应用"
        class="empty-state"
      >
        <el-button type="primary" @click="goToCreateApp">创建第一个应用</el-button>
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
    <FloatingChatBox />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  Grid, Plus, Search, ChatDotRound, ChatLineRound, 
  User, Clock 
} from '@element-plus/icons-vue'
import { listAllAppVoByPage, getAppVoById } from '@/api/appController'
import type { AppVO } from '@/types/api'
import FloatingChatBox from '@/components/FloatingChatBox.vue'

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
          initPrompt: appDetail.initPrompt
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

<style scoped>
.app-square {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding: 0 20px;
}

.header-content {
  flex: 1;
}

.page-title {
  font-size: 36px;
  font-weight: 700;
  color: white;
  margin-bottom: 8px;
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

.header-actions {
  flex-shrink: 0;
}

.search-section {
  max-width: 600px;
  margin: 0 auto 40px;
  padding: 0 20px;
}

.search-input {
  --el-input-bg-color: rgba(255, 255, 255, 0.95);
  --el-input-border-color: transparent;
  border-radius: 25px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.apps-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 40px;
}

.app-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  position: relative;
}

.app-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
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
  transform: scale(1.05);
}

.default-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: white;
  font-size: 48px;
}

.card-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.app-card:hover .card-overlay {
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
  line-height: 1.4;
  flex: 1;
  margin-right: 12px;
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  font-size: 12px;
  color: #999;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-name {
  font-weight: 500;
}

.create-time {
  display: flex;
  align-items: center;
  gap: 4px;
}

.empty-state {
  margin: 60px 0;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 40px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  backdrop-filter: blur(10px);
}

/* 动画效果 */
.app-card-enter-active,
.app-card-leave-active {
  transition: all 0.3s ease;
}

.app-card-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.app-card-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

.app-card-move {
  transition: transform 0.3s ease;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .app-square {
    padding: 16px;
  }
  
  .page-header {
    flex-direction: column;
    gap: 20px;
    text-align: center;
  }
  
  .page-title {
    font-size: 28px;
  }
  
  .apps-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .search-section {
    margin-bottom: 24px;
  }
}

@media (max-width: 480px) {
  .card-content {
    padding: 16px;
  }
  
  .card-cover {
    height: 160px;
  }
}
</style>