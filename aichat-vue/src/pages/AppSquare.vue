<template>
  <div class="app-square">
    <div class="page-header">
      <h1>应用广场</h1>
      <p>发现精选的AI聊天应用</p>
    </div>
    
    <div class="apps-grid">
      <a-card
        v-for="app in apps"
        :key="app.appId"
        class="app-card"
      >
        <template #cover>
          <img v-if="app.cover" :src="app.cover" alt="cover" style="height: 160px; object-fit: cover;" />
        </template>
        
        <template #actions>
          <a-button type="link" @click="handleChat(app)">开始聊天</a-button>
          <a-button type="link" @click="handleViewDetail(app)">查看详情</a-button>
        </template>
        
        <a-card-meta
          :title="app.appName"
          :description="app.description"
        >
          <template #avatar>
            <a-avatar :src="app.cover" :size="48">
              {{ app.appName?.charAt(0) }}
            </a-avatar>
          </template>
        </a-card-meta>
        
        <div class="app-info">
          <div class="app-author">
            <UserOutlined />
            {{ app.userName }}
          </div>
          <div class="app-time">
            <ClockCircleOutlined />
            {{ formatTime(app.createTime) }}
          </div>
        </div>
      </a-card>
    </div>

    <div class="pagination-container">
      <a-pagination
        v-model:current="currentPage"
        v-model:pageSize="pageSize"
        :total="total"
        show-size-changer
        :page-size-options="['12', '24', '48', '96']"
        @change="handlePageChange"
        @showSizeChange="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { UserOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { appService } from '@/services'
import type { AppVO } from '@/types/api'

const router = useRouter()

// 响应式数据
const apps = ref<AppVO[]>([])
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)
const loading = ref(false)

// 方法
const loadApps = async () => {
  loading.value = true
  try {
    const response = await appService.listGoodApps({
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    if (response.code === 0) {
      apps.value = response.data.records
      total.value = response.data.total
    }
  } catch (error) {
    message.error('加载应用列表失败')
  } finally {
    loading.value = false
  }
}

const handleChat = (app: AppVO) => {
  router.push({
    path: '/chat',
    query: { appId: app.appId }
  })
}

const handleViewDetail = (app: AppVO) => {
  // 跳转到应用详情页
  console.log('查看应用详情:', app)
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  loadApps()
}

const handleSizeChange = (current: number, size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadApps()
}

const formatTime = (time: string) => {
  return new Date(time).toLocaleDateString()
}

// 生命周期
onMounted(() => {
  loadApps()
})
</script>

<style scoped>
.app-square {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 32px;
  margin-bottom: 8px;
  color: #262626;
}

.page-header p {
  font-size: 16px;
  color: #8c8c8c;
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.app-card {
  transition: all 0.3s;
  border-radius: 8px;
  overflow: hidden;
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.app-info {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #8c8c8c;
}

.app-author, .app-time {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

:deep(.ant-card-meta-title) {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

:deep(.ant-card-meta-description) {
  color: #666;
  line-height: 1.5;
}
</style>