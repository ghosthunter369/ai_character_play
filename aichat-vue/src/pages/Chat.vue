<template>
  <div class="chat-page">
    <a-layout class="chat-layout">
      <!-- 侧边栏 -->
      <a-layout-sider class="sidebar" width="300">
        <div class="sidebar-header">
          <h3>AI 聊天应用</h3>
          <a-button type="primary" @click="showCreateAppModal = true">
            新建应用
          </a-button>
        </div>
        
        <a-menu
          v-model:selectedKeys="selectedAppId"
          mode="inline"
          @select="handleAppSelect"
        >
          <a-menu-item v-for="app in apps" :key="app.appId">
            <template #icon>
              <img :src="app.cover" class="app-cover" />
            </template>
            {{ app.appName }}
          </a-menu-item>
        </a-menu>
      </a-layout-sider>

      <!-- 主内容区 -->
      <a-layout-content class="chat-content">
        <div v-if="selectedApp" class="chat-container">
          <!-- 聊天头部 -->
          <div class="chat-header">
            <h2>{{ selectedApp.appName }}</h2>
            <p>{{ selectedApp.description }}</p>
          </div>

          <!-- 聊天消息区域 -->
          <div class="messages-container">
            <div
              v-for="message in messages"
              :key="message.id"
              :class="['message', message.type]"
            >
              <div class="message-content">
                {{ message.content }}
              </div>
            </div>
            
            <!-- 流式响应显示区域 -->
            <div v-if="streamingContent" class="message ai">
              <div class="message-content">
                {{ streamingContent }}
              </div>
            </div>
          </div>

          <!-- 输入区域 -->
          <div class="input-container">
            <a-input
              v-model:value="inputMessage"
              placeholder="输入消息..."
              @press-enter="sendMessage"
              :disabled="isStreaming"
            >
              <template #suffix>
                <a-button
                  type="primary"
                  @click="sendMessage"
                  :loading="isStreaming"
                >
                  发送
                </a-button>
              </template>
            </a-input>
          </div>
        </div>

        <div v-else class="welcome-container">
          <a-empty description="请选择一个应用开始聊天" />
        </div>
      </a-layout-content>
    </a-layout>

    <!-- 创建应用模态框 -->
    <a-modal
      v-model:open="showCreateAppModal"
      title="创建新应用"
      @ok="createApp"
      @cancel="showCreateAppModal = false"
    >
      <a-form :model="newAppForm" layout="vertical">
        <a-form-item label="应用名称">
          <a-input v-model:value="newAppForm.appName" />
        </a-form-item>
        <a-form-item label="应用描述">
          <a-textarea v-model:value="newAppForm.description" />
        </a-form-item>
        <a-form-item label="初始化提示">
          <a-textarea v-model:value="newAppForm.initPrompt" />
        </a-form-item>
        <a-form-item label="开场白">
          <a-textarea v-model:value="newAppForm.prologue" />
        </a-form-item>
        <a-form-item label="封面图片URL">
          <a-input v-model:value="newAppForm.cover" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { appService } from '@/services'
import { chatService, SSEManager } from '@/services/chatService'
import type { AppDTO, AppVO } from '@/types/api'

// 路由
const route = useRoute()

// 响应式数据
const apps = ref<AppVO[]>([])
const selectedAppId = ref<number[]>([])
const selectedApp = ref<AppVO | null>(null)
const messages = ref<any[]>([])
const inputMessage = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const showCreateAppModal = ref(false)

const newAppForm = ref<AppDTO>({
  appName: '',
  description: '',
  initPrompt: '',
  prologue: '',
  cover: ''
})

// SSE管理器
const sseManager = new SSEManager()

// 生命周期
onMounted(() => {
  loadApps()
})

// 方法
const loadApps = async () => {
  try {
    console.log('开始加载应用列表...')
    
    // 先加载当前用户的应用列表
    const response = await appService.listMyApps({
      pageNum: 1,
      pageSize: 20
    })
    if (response.code === 0) {
      apps.value = response.data.records
      console.log('应用列表加载完成:', apps.value)
      
      // 如果有路由参数，尝试选择对应的应用
      if (route.query.appId) {
        const appId = parseInt(route.query.appId as string, 10)
        console.log('路由参数存在，尝试选择应用:', appId, '类型:', typeof appId)
        selectAppById(appId)
      } else if (apps.value.length > 0) {
        // 如果没有路由参数，默认选择第一个应用
        console.log('默认选择第一个应用')
        selectAppById(apps.value[0].appId)
      }
    } else {
      console.log('应用列表加载失败:', response.message)
      // 如果加载失败，创建一个默认应用用于测试
      createDefaultAppForTesting()
    }
  } catch (error) {
    console.error('加载应用列表失败:', error)
    message.error('加载应用列表失败')
    // 如果API调用失败，创建一个默认应用用于测试
    createDefaultAppForTesting()
  }
}

const createDefaultAppForTesting = () => {
  console.log('创建默认应用用于测试')
  const defaultApp = {
    appId: 1,
    appName: '默认聊天助手',
    description: '这是一个默认的聊天应用',
    cover: '',
    createTime: new Date().toISOString()
  }
  apps.value = [defaultApp]
  selectedApp.value = defaultApp
  selectedAppId.value = [1]
  console.log('默认应用已创建:', defaultApp)
}

const selectAppById = (appId: number) => {
  console.log('尝试选择应用ID:', appId)
  console.log('当前应用列表:', apps.value)
  const app = apps.value.find((app: AppVO) => app.appId === appId)
  if (app) {
    console.log('找到应用:', app)
    selectedApp.value = app
    selectedAppId.value = [appId]
    loadChatHistory(appId)
  } else {
    console.log('未找到应用，应用列表为空或应用不存在')
  }
}

const handleAppSelect = ({ key }: { key: string }) => {
  const appId = parseInt(key, 10)
  selectAppById(appId)
}

// 监听路由参数变化（仅在应用列表加载后处理）
watch(() => route.query.appId, (newAppId) => {
  console.log('路由参数变化:', newAppId)
  // 应用列表为空时不处理，等待loadApps完成
  if (newAppId && apps.value.length > 0) {
    const appId = parseInt(newAppId as string, 10)
    console.log('选择应用ID:', appId, '类型:', typeof appId)
    selectAppById(appId)
  }
})

const loadChatHistory = async (appId: number) => {
  try {
    const response = await chatService.getChatHistory(appId)
    if (response.code === 0) {
      messages.value = response.data.records.map(record => ({
        id: record.id,
        type: record.messageType === 'USER' ? 'user' : 'ai',
        content: record.content
      }))
    }
  } catch (error) {
    console.error('加载聊天历史失败:', error)
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || !selectedApp.value || isStreaming.value) {
    return
  }

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  isStreaming.value = true
  streamingContent.value = ''

  // 添加用户消息到聊天记录
  messages.value.push({
    id: Date.now(),
    type: 'user',
    content: userMessage
  })

  try {
    // 设置SSE回调
    sseManager.onMessage((data: string) => {
      streamingContent.value += data
    })

    sseManager.onDone(() => {
      // 流式响应完成，添加到消息列表
      if (streamingContent.value) {
        messages.value.push({
          id: Date.now() + 1,
          type: 'ai',
          content: streamingContent.value
        })
        streamingContent.value = ''
      }
      isStreaming.value = false
    })

    sseManager.onError((error) => {
      console.error('SSE error:', error)
      message.error('聊天发送失败')
      isStreaming.value = false
      streamingContent.value = ''
    })

    // 开始聊天
    sseManager.startChat(selectedApp.value.appId, userMessage)
  } catch (error) {
    console.error('发送消息失败:', error)
    message.error('发送消息失败')
    isStreaming.value = false
  }
}

const createApp = async () => {
  try {
    const response = await appService.createApp(newAppForm.value)
    if (response.code === 0) {
      message.success('创建应用成功')
      showCreateAppModal.value = false
      newAppForm.value = {
        appName: '',
        description: '',
        initPrompt: '',
        prologue: '',
        cover: ''
      }
      loadApps()
    }
  } catch (error) {
    message.error('创建应用失败')
  }
}
</script>

<style scoped>
.chat-page {
  height: 100vh;
}

.chat-layout {
  height: 100%;
}

.sidebar {
  background: #fff;
  border-right: 1px solid #f0f0f0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.sidebar-header h3 {
  margin-bottom: 12px;
}

.app-cover {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  object-fit: cover;
}

.chat-content {
  background: #f5f5f5;
  padding: 0;
}

.chat-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-header {
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.messages-container {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.message {
  margin-bottom: 16px;
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.ai {
  justify-content: flex-start;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 8px;
  word-wrap: break-word;
}

.message.user .message-content {
  background: #1890ff;
  color: white;
}

.message.ai .message-content {
  background: #fff;
  border: 1px solid #f0f0f0;
}

.input-container {
  padding: 16px 24px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}

.welcome-container {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>