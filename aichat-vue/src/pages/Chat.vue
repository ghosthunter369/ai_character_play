<template>
  <div class="chat-page">
    <el-container class="chat-container">
      <!-- 侧边栏 -->
      <el-aside class="chat-sidebar" width="320px">
        <div class="sidebar-header">
          <div class="header-title">
            <el-icon size="24"><ChatDotRound /></el-icon>
            <h3>AI 聊天助手</h3>
          </div>
          <el-button type="primary" @click="showCreateModal = true" circle>
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
        
        <!-- 当前选中的APP信息 -->
        <div v-if="selectedApp" class="current-app-info">
          <div class="current-app-card">
            <el-avatar :size="48" :src="selectedApp.cover" class="current-app-avatar">
              <el-icon><Avatar /></el-icon>
            </el-avatar>
            <div class="current-app-details">
              <h4 class="current-app-name">{{ selectedApp.appName }}</h4>
              <p class="current-app-desc">{{ selectedApp.description || '暂无描述' }}</p>
              <div class="app-meta">
                <el-tag size="small" type="success">{{ selectedApp.userName || '系统' }}</el-tag>
                <span class="create-time">{{ formatTime(selectedApp.createTime) }}</span>
              </div>
            </div>
          </div>
          <div class="welcome-message">
            <el-icon class="welcome-icon"><Promotion /></el-icon>
            <p>欢迎使用 {{ selectedApp.appName }}！我是您的AI助手，有什么可以帮助您的吗？</p>
          </div>
        </div>
        
        <div class="app-list">
          <div
            v-for="app in apps"
            :key="app.appId"
            :class="['app-item', { active: selectedApp?.appId === app.appId }]"
            @click="selectApp(app)"
          >
            <el-avatar :size="40" :src="app.cover" class="app-avatar">
              <el-icon><Avatar /></el-icon>
            </el-avatar>
            <div class="app-info">
              <h4 class="app-name">{{ app.appName }}</h4>
              <p class="app-desc">{{ app.description || '暂无描述' }}</p>
            </div>
            <el-badge v-if="getUnreadCount(app.appId)" :value="getUnreadCount(app.appId)" class="unread-badge" />
          </div>
        </div>
      </el-aside>

      <!-- 主聊天区域 -->
      <el-main class="chat-main">
        <div v-if="selectedApp" class="chat-content">
          <!-- 聊天头部 -->
          <div class="chat-header">
            <div class="header-left">
              <el-avatar :size="36" :src="selectedApp.cover">
                <el-icon><Avatar /></el-icon>
              </el-avatar>
              <div class="app-details">
                <h3>{{ selectedApp.appName }}</h3>
                <p>{{ selectedApp.description }}</p>
              </div>
            </div>
            
            <div class="header-right">
              <!-- 聊天模式切换 -->
              <div class="mode-switch">
                <el-segmented v-model="chatMode" :options="modeOptions" size="small" />
              </div>
              
              <el-dropdown @command="handleMenuCommand">
                <el-button circle>
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="clear">清空对话</el-dropdown-item>
                    <el-dropdown-item command="export">导出对话</el-dropdown-item>
                    <el-dropdown-item command="settings">设置</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>

          <!-- 消息列表 -->
          <div class="messages-container" ref="messagesContainer">
            <div class="messages-list">
              <!-- 欢迎消息 -->
              <div v-if="messages.length === 0 && selectedApp.prologue" class="welcome-message">
                <div class="message-bubble ai-message">
                  <el-avatar :size="32" :src="selectedApp.cover" class="message-avatar">
                    <el-icon><Avatar /></el-icon>
                  </el-avatar>
                  <div class="bubble-content">
                    <div class="message-text">{{ selectedApp.prologue }}</div>
                    <div class="message-time">{{ formatTime(new Date()) }}</div>
                  </div>
                </div>
              </div>

              <!-- 历史消息 -->
              <div
                v-for="message in messages"
                :key="message.id"
                :class="['message-item', message.type]"
              >
                <div :class="['message-bubble', `${message.type}-message`]">
                  <el-avatar 
                    v-if="message.type === 'ai'" 
                    :size="32" 
                    :src="selectedApp.cover" 
                    class="message-avatar"
                  >
                    <el-icon><Avatar /></el-icon>
                  </el-avatar>
                  
                  <div class="bubble-content">
                    <div class="message-text" v-html="formatMessageContent(message.content)"></div>
                    <div class="message-time">{{ formatTime(message.timestamp) }}</div>
                  </div>
                  
                  <el-avatar 
                    v-if="message.type === 'user'" 
                    :size="32" 
                    :src="userStore.user?.userAvatar" 
                    class="message-avatar"
                  >
                    <el-icon><User /></el-icon>
                  </el-avatar>
                </div>
              </div>

              <!-- 流式响应消息 -->
              <div v-if="streamingContent" class="message-item ai">
                <div class="message-bubble ai-message streaming">
                  <el-avatar :size="32" :src="selectedApp.cover" class="message-avatar">
                    <el-icon><Avatar /></el-icon>
                  </el-avatar>
                  <div class="bubble-content">
                    <div class="message-text">{{ streamingContent }}</div>
                    <div class="typing-indicator">
                      <span></span>
                      <span></span>
                      <span></span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 输入区域 -->
          <div class="input-area">
            <div class="input-container">
              <!-- 语音模式 -->
              <div v-if="chatMode === 'voice'" class="voice-mode-container">
                <VoiceChatBox 
                  :app-id="selectedApp.appId"
                  :auto-connect="true"
                  ref="voiceChatBoxRef"
                />
              </div>

              <!-- 文字模式 -->
              <div v-else class="text-input">
                <el-input
                  v-model="inputMessage"
                  type="textarea"
                  :rows="1"
                  :autosize="{ minRows: 1, maxRows: 4 }"
                  placeholder="输入消息..."
                  @keydown.enter.exact.prevent="sendMessage"
                  @keydown.enter.shift.exact="handleShiftEnter"
                  :disabled="isStreaming"
                  class="message-input"
                />
                <div class="input-actions">
                  <el-button
                    type="primary"
                    @click="sendMessage"
                    :loading="isStreaming"
                    :disabled="!inputMessage.trim()"
                    circle
                  >
                    <el-icon><Promotion /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 未选择应用状态 -->
        <div v-else class="empty-chat">
          <el-empty description="请选择一个应用开始聊天">
            <el-button type="primary" @click="showCreateModal = true">
              创建新应用
            </el-button>
          </el-empty>
        </div>
      </el-main>
    </el-container>

    <!-- 创建应用对话框 -->
    <el-dialog
      v-model="showCreateModal"
      title="创建新应用"
      width="600px"
      :before-close="handleCloseModal"
    >
      <el-form :model="newAppForm" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="newAppForm.appName" placeholder="请输入应用名称" />
        </el-form-item>
        
        <el-form-item label="应用描述" prop="description">
          <el-input
            v-model="newAppForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入应用描述"
          />
        </el-form-item>
        
        <el-form-item label="初始化提示" prop="initPrompt">
          <el-input
            v-model="newAppForm.initPrompt"
            type="textarea"
            :rows="4"
            placeholder="请输入系统提示词"
          />
        </el-form-item>
        
        <el-form-item label="开场白" prop="prologue">
          <el-input
            v-model="newAppForm.prologue"
            type="textarea"
            :rows="2"
            placeholder="请输入开场白"
          />
        </el-form-item>
        
        <el-form-item label="封面图片">
          <el-input v-model="newAppForm.cover" placeholder="请输入图片URL（可选）" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showCreateModal = false">取消</el-button>
        <el-button type="primary" @click="createApp" :loading="createLoading">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound, Plus, Avatar, MoreFilled, User, Microphone,
  VideoPause, Promotion
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { getAppVoById, listMyAppVoByPage, createApp1, getOpeningRemark } from '@/api/appController'
import { chat } from '@/api/aiChatController'
import { SSEManager, chatService } from '@/services/chatService'
import VoiceChatBox from '@/components/VoiceChatBox.vue'
import type { AppDTO, AppVO } from '@/types/api'

// 路由和状态
const route = useRoute()
const userStore = useUserStore()

// 响应式数据
const apps = ref<AppVO[]>([])
const selectedApp = ref<AppVO | null>(null)
const messages = ref<any[]>([])
const inputMessage = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const showCreateModal = ref(false)
const createLoading = ref(false)
const messagesContainer = ref<HTMLElement>()

// 聊天模式
const chatMode = ref<'text' | 'voice'>('text')
const modeOptions = [
  { label: '文字', value: 'text' },
  { label: '语音', value: 'voice' }
]

// 语音聊天组件引用
const voiceChatBoxRef = ref<InstanceType<typeof VoiceChatBox>>()

// 表单数据
const newAppForm = ref<AppDTO>({
  appName: '',
  description: '',
  initPrompt: '',
  prologue: '',
  cover: ''
})

const formRules = {
  appName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入应用描述', trigger: 'blur' }
  ]
}

const formRef = ref()

// SSE管理器
const sseManager = new SSEManager()

// 方法
const loadApps = async () => {
  try {
    const response = await listMyAppVoByPage({
      pageNum: 1,
      pageSize: 20,
      sortField: 'create_time',
      sortOrder: 'descend'
    })
    if (response.data?.code === 0) {
      apps.value = response.data?.data?.records || []
      
      // 处理路由参数 - 优先处理来自应用广场的应用
      await handleRouteParams()
    }
  } catch (error) {
    ElMessage.error('加载应用列表失败')
  }
}

// 处理路由参数
const handleRouteParams = async () => {
  console.log('处理路由参数:', route.query)
  
  if (route.query.appId) {
    // 使用字符串处理大整数，避免JavaScript精度丢失
    const appIdStr = route.query.appId as string
    const appId = BigInt(appIdStr) // 使用BigInt处理大整数
    console.log('解析的appId:', appIdStr, '(BigInt:', appId.toString(), ')')
    
    // 统一使用 API 获取应用详情，确保数据完整性
    try {
      console.log('通过API获取应用详情，appId:', appIdStr)
      console.log('API调用参数:', { id: appIdStr }) // 传递字符串而不是数字
      
      const appResponse = await getAppVoById({ id: appIdStr })
      console.log('API完整响应:', appResponse)
      
      if (appResponse.data?.code === 0 && appResponse.data?.data) {
        const appData = appResponse.data.data
        console.log('获取到的应用数据:', appData)
        console.log('应用ID匹配检查:', appData.appId, '===', appIdStr, appData.appId == appIdStr)
        
        // 验证返回的应用ID是否匹配 - 使用字符串比较
        if ((appData.appId || appData.id) == appIdStr) {
          // 检查是否已存在该应用
          const existingAppIndex = apps.value.findIndex(app => app.appId == appIdStr)
          if (existingAppIndex >= 0) {
            // 更新现有应用信息
            apps.value[existingAppIndex] = appData
          } else {
            // 添加新应用到列表顶部
            apps.value.unshift(appData)
          }
          
          selectApp(appData)
          ElMessage.success(`已加载应用：${appData.appName}`)
        } else {
          console.error('API返回的应用ID不匹配！期望:', appIdStr, '实际:', appData.appId, '类型:', typeof appIdStr, typeof appData.appId)
          ElMessage.error(`应用ID不匹配，期望: ${appIdStr}，实际: ${appData.appId}`)
          
          // 尝试从现有列表中查找正确的应用
          const correctApp = apps.value.find(app => app.appId == appIdStr)
          if (correctApp) {
            selectApp(correctApp)
            ElMessage.success(`从本地列表加载应用：${correctApp.appName}`)
          } else {
            ElMessage.error('未找到对应的应用')
          }
        }
      } else {
        console.error('API返回错误:', appResponse)
        ElMessage.error('获取应用信息失败')
        // 如果API失败，尝试从现有列表中查找
        selectAppById(appIdStr)
      }
    } catch (error) {
      console.error('获取应用详情失败:', error)
      ElMessage.error('网络错误，无法获取应用信息')
      // 如果网络错误，尝试从现有列表中查找
      selectAppById(appIdStr)
    }
  } else if (apps.value.length > 0) {
    selectApp(apps.value[0])
  }
}

const selectApp = (app: AppVO) => {
  console.log('选择应用:', app)
  selectedApp.value = app
  loadChatHistory(app.appId)
}

const selectAppById = (appId: string | number) => {
  const app = apps.value.find(app => app.appId == appId)
  if (app) {
    selectApp(app)
  }
}

const loadChatHistory = async (appId: string | number) => {
  try {
    const response = await chatService.getChatHistory(appId)
    if (response.data?.code !== 0 || !response.data?.data?.history) {
      messages.value = []
      return
    }

    const records = response.data.data.history.records || []
    messages.value = records.map(item => ({
      id: item.id,
      type: item.messageType === 'user' ? 'user' : 'ai',
      content: item.message,
      timestamp: item.createTime || new Date()
    }))

    scrollToBottom()
  } catch (error) {
    console.error('加载聊天历史失败:', error)
    messages.value = []
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

  // 添加用户消息
  messages.value.push({
    id: Date.now(),
    type: 'user',
    content: userMessage,
    timestamp: new Date()
  })

  scrollToBottom()

  try {
    // 设置SSE回调
    sseManager.onMessage((data: string) => {
      streamingContent.value += data
      scrollToBottom()
    })

    sseManager.onDone(() => {
      if (streamingContent.value) {
        messages.value.push({
          id: Date.now() + 1,
          type: 'ai',
          content: streamingContent.value,
          timestamp: new Date()
        })
        streamingContent.value = ''
      }
      isStreaming.value = false
      scrollToBottom()
    })

    sseManager.onError((error) => {
      console.error('SSE error:', error)
      ElMessage.error('聊天发送失败')
      isStreaming.value = false
      streamingContent.value = ''
    })

    // 开始聊天
    await sseManager.startChat(selectedApp.value.appId, userMessage)
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error('发送消息失败')
    isStreaming.value = false
  }
}

// 语音相关方法已移至VoiceChatBox组件中处理

const createApp = async () => {
  if (!formRef.value) return
  
  const valid = await formRef.value.validate()
  if (!valid) return

  createLoading.value = true
  try {
    const response = await createApp1(newAppForm.value)
    if (response.data?.code === 0) {
      ElMessage.success('创建应用成功')
      showCreateModal.value = false
      resetForm()
      loadApps()
    }
  } catch (error) {
    ElMessage.error('创建应用失败')
  } finally {
    createLoading.value = false
  }
}

const handleCloseModal = () => {
  showCreateModal.value = false
  resetForm()
}

const resetForm = () => {
  newAppForm.value = {
    appName: '',
    description: '',
    initPrompt: '',
    prologue: '',
    cover: ''
  }
  formRef.value?.resetFields()
}

const handleMenuCommand = async (command: string) => {
  switch (command) {
    case 'clear':
      try {
        await ElMessageBox.confirm('确定要清空当前对话吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        messages.value = []
        ElMessage.success('对话已清空')
      } catch {
        // 用户取消
      }
      break
    case 'export':
      exportChat()
      break
    case 'settings':
      ElMessage.info('设置功能开发中...')
      break
  }
}

const exportChat = () => {
  if (!selectedApp.value || messages.value.length === 0) {
    ElMessage.warning('没有可导出的对话')
    return
  }

  const chatContent = messages.value.map(msg => 
    `${msg.type === 'user' ? '用户' : selectedApp.value?.appName}: ${msg.content}`
  ).join('\n\n')

  const blob = new Blob([chatContent], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${selectedApp.value.appName}-对话记录.txt`
  a.click()
  URL.revokeObjectURL(url)
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const formatTime = (date: Date | string) => {
  if (!date) return ''
  const dateObj = typeof date === 'string' ? new Date(date) : date
  const now = new Date()
  const diff = now.getTime() - dateObj.getTime()
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  
  return dateObj.toLocaleDateString('zh-CN')
}

const formatMessageContent = (content: string) => {
  // 简单的markdown渲染
  return content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
}

const getUnreadCount = (appId: string | number) => {
  // 这里可以实现未读消息计数逻辑
  return 0
}

const handleShiftEnter = (event: KeyboardEvent) => {
  // Shift+Enter 换行
  const target = event.target as HTMLTextAreaElement
  const start = target.selectionStart
  const end = target.selectionEnd
  inputMessage.value = inputMessage.value.substring(0, start) + '\n' + inputMessage.value.substring(end)
  nextTick(() => {
    target.selectionStart = target.selectionEnd = start + 1
  })
}

// 生命周期
onMounted(() => {
  loadApps()
})

// 监听路由变化
watch(() => route.query, (newQuery) => {
  if (newQuery.appId) {
    handleRouteParams()
  }
}, { deep: true })

// 监听聊天模式变化
watch(chatMode, async (newMode, oldMode) => {
  console.log('聊天模式变化:', { newMode, oldMode, hasApp: !!selectedApp.value, hasPrologue: !!selectedApp.value?.prologue })
  if (newMode === 'voice' && oldMode === 'text' && selectedApp.value?.prologue) {
    console.log('切换到语音模式，开始播放开场白:', selectedApp.value.prologue)
    // 切换到语音模式时播放开场白
    await playPrologueAudio()
  }
})

// 播放开场白音频
const playPrologueAudio = async () => {
  if (!selectedApp.value?.prologue) {
    console.log('没有开场白，跳过音频播放')
    return
  }
  
  console.log('开始调用开场白API:', selectedApp.value.prologue)
  
  try {
    // 调用后端API获取开场白音频
    const response = await getOpeningRemark({ prologue: selectedApp.value.prologue })
    console.log('开场白API响应:', response)
    if (response.data?.code === 0 && response.data?.data) {
      const base64Audio = response.data.data
      
      // 将Base64音频转换为AudioBuffer并播放
      const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)()
      const audioData = Uint8Array.from(atob(base64Audio), c => c.charCodeAt(0))
      const audioBuffer = await audioContext.decodeAudioData(audioData.buffer)
      
      const source = audioContext.createBufferSource()
      source.buffer = audioBuffer
      source.connect(audioContext.destination)
      source.start()
      
      // 同时显示开场白文本流式输出
      streamingContent.value = ''
      const text = selectedApp.value.prologue
      let index = 0
      
      const streamText = () => {
        if (index < text.length) {
          streamingContent.value += text[index]
          index++
          setTimeout(streamText, 50) // 每50ms输出一个字符
        } else {
          // 流式输出完成后添加到消息列表
          setTimeout(() => {
            messages.value.push({
              id: Date.now(),
              type: 'ai',
              content: text,
              timestamp: new Date()
            })
            streamingContent.value = ''
            scrollToBottom()
          }, 500)
        }
      }
      
      streamText()
      scrollToBottom()
      
    } else {
      console.error('获取开场白音频失败:', response)
    }
  } catch (error) {
    console.error('播放开场白音频失败:', error)
    // 如果音频播放失败，至少显示文本
    if (selectedApp.value?.prologue) {
      messages.value.push({
        id: Date.now(),
        type: 'ai',
        content: selectedApp.value.prologue,
        timestamp: new Date()
      })
      scrollToBottom()
    }
  }
}

// 组件卸载时清理SSE连接
onUnmounted(() => {
  sseManager.close()
})
</script>

<style scoped>
.chat-page {
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.chat-container {
  height: 100%;
  background: white;
  border-radius: 16px 16px 0 0;
  overflow: hidden;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.1);
}

/* 侧边栏样式 */
.chat-sidebar {
  background: #f8f9fa;
  border-right: 1px solid #e9ecef;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
}

.app-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.app-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  margin-bottom: 4px;
}

.app-item:hover {
  background: rgba(102, 126, 234, 0.1);
}

.app-item.active {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}

.app-item.active .app-name,
.app-item.active .app-desc {
  color: white;
}

.app-avatar {
  flex-shrink: 0;
  margin-right: 12px;
}

.app-info {
  flex: 1;
  min-width: 0;
}

.app-name {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #2c3e50;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-desc {
  font-size: 12px;
  color: #6c757d;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.unread-badge {
  position: absolute;
  top: 8px;
  right: 8px;
}

/* 当前APP信息区域 */
.current-app-info {
  padding: 16px;
  border-bottom: 1px solid #e9ecef;
  background: white;
  margin: 8px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.current-app-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.current-app-avatar {
  flex-shrink: 0;
}

.current-app-details {
  flex: 1;
  min-width: 0;
}

.current-app-name {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #2c3e50;
}

.current-app-desc {
  font-size: 13px;
  color: #6c757d;
  margin: 0 0 8px 0;
  line-height: 1.4;
}

.app-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.create-time {
  font-size: 11px;
  color: #999;
}

.welcome-message {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(118, 75, 162, 0.1));
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.welcome-icon {
  color: #667eea;
  margin-top: 2px;
  flex-shrink: 0;
}

.welcome-message p {
  margin: 0;
  font-size: 13px;
  color: #4a5568;
  line-height: 1.4;
}

/* 主聊天区域 */
.chat-main {
  padding: 0;
  display: flex;
  flex-direction: column;
}

.chat-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-details h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.app-details p {
  margin: 0;
  font-size: 12px;
  color: #6c757d;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.mode-switch {
  display: flex;
  align-items: center;
}

/* 消息区域 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f8f9fa;
}

.messages-list {
  max-width: 800px;
  margin: 0 auto;
}

.message-item {
  margin-bottom: 16px;
}

.message-item.user {
  display: flex;
  justify-content: flex-end;
}

.message-item.ai {
  display: flex;
  justify-content: flex-start;
}

.message-bubble {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 70%;
}

.user-message {
  flex-direction: row-reverse;
}

.ai-message {
  flex-direction: row;
}

.message-avatar {
  flex-shrink: 0;
}

.bubble-content {
  background: white;
  border-radius: 16px;
  padding: 12px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: relative;
}

.user-message .bubble-content {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}

.message-text {
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  text-align: right;
}

.user-message .message-time {
  color: rgba(255, 255, 255, 0.7);
}

.streaming .bubble-content {
  border: 2px solid #667eea;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #667eea;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.welcome-message {
  margin-bottom: 24px;
}

/* 输入区域 */
.input-area {
  padding: 16px 24px;
  background: white;
  border-top: 1px solid #e9ecef;
}

.input-container {
  max-width: 800px;
  margin: 0 auto;
}

.text-input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.message-input {
  flex: 1;
}

.input-actions {
  display: flex;
  align-items: center;
}

.voice-mode-container {
  width: 100%;
}

/* 空状态 */
.empty-chat {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-container {
    border-radius: 0;
  }
  
  .chat-sidebar {
    width: 280px !important;
  }
  
  .chat-header {
    padding: 12px 16px;
  }
  
  .header-right {
    gap: 8px;
  }
  
  .mode-switch {
    display: none;
  }
  
  .messages-container {
    padding: 16px;
  }
  
  .message-bubble {
    max-width: 85%;
  }
  
  .input-area {
    padding: 12px 16px;
  }
}

@media (max-width: 480px) {
  .chat-sidebar {
    width: 260px !important;
  }
  
  .app-item {
    padding: 8px;
  }
  
  .app-avatar {
    width: 32px !important;
    height: 32px !important;
  }
  
  .voice-btn {
    width: 56px;
    height: 56px;
  }
}
</style>