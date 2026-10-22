<template>
  <div class="chat-page">
    <label class="mobile-role-picker">当前角色
      <select :value="selectedApp?.appId || ''" @change="selectFromPicker">
        <option disabled value="">选择一个角色</option>
        <option v-for="app in apps" :key="app.appId" :value="app.appId">{{ app.appName }}</option>
      </select>
    </label>
    <el-container class="chat-container">
      <!-- 侧边栏 -->
      <el-aside class="chat-sidebar" width="240px">
        <div class="sidebar-header">
          <div class="header-title">
            <el-icon size="24"><ChatDotRound /></el-icon>
            <h3>对话角色</h3>
          </div>
          <el-button type="primary" @click="showCreateModal = true" circle aria-label="创建角色">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
        
        <div class="app-list">
          <div
            v-for="app in apps"
            :key="app.appId"
            :class="['app-item', { active: selectedApp?.appId === app.appId }]"
            @click="selectApp(app)"
            @keydown.enter="selectApp(app)"
            @keydown.space.prevent="selectApp(app)"
            tabindex="0" role="button" :aria-pressed="selectedApp?.appId === app.appId"
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
              <el-dropdown @command="handleMenuCommand">
                <el-button circle aria-label="对话选项">
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

          <!-- 聊天模式标签页 -->
          <div class="chat-tabs">
            <div class="tabs-container">
              <button 
                class="tab-button"
                :class="{ active: chatMode === 'text' }"
                @click="chatMode = 'text'"
              >
                <el-icon><ChatDotRound /></el-icon>
                <span>文字聊天</span>
              </button>
              <button 
                class="tab-button"
                :class="{ active: chatMode === 'voice' }"
                @click="chatMode = 'voice'"
              >
                <el-icon><Microphone /></el-icon>
                <span>语音聊天</span>
              </button>
            </div>
          </div>

          <!-- 标签页内容区域 -->
          <div class="tab-content-area">
            <!-- 文字聊天标签页 -->
            <div v-show="chatMode === 'text'" class="tab-content text-chat-tab">
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

              <!-- 文字输入区域 -->
              <div class="text-input-area">
                <div class="input-container">
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
                      @click="toggleVoiceInput"
                      :type="isVoiceInputMode ? 'danger' : 'default'"
                      circle
                      class="voice-toggle-btn"
                      aria-label="切换语音输入"
                    >
                      <el-icon><Microphone /></el-icon>
                    </el-button>
                    <el-button
                      type="primary"
                      @click="sendMessage"
                      :loading="isStreaming"
                      :disabled="!inputMessage.trim()"
                      aria-label="发送消息"
                      circle
                    >
                      <el-icon><Promotion /></el-icon>
                    </el-button>
                  </div>
                </div>
                
                <!-- 语音输入模式 -->
                <div v-if="isVoiceInputMode" class="voice-input-panel">
                  <div class="voice-controls">
                    <button 
                      @click="toggleRecording" 
                      class="record-button"
                      :class="{ recording: isRecording }"
                    >
                      {{ isRecording ? '停止录音' : '开始录音' }}
                    </button>
                    <div v-if="isRecording" class="recording-indicator">
                      <div class="recording-animation"></div>
                      <span>正在录音中...</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 语音聊天标签页 -->
            <div v-show="chatMode === 'voice'" class="tab-content voice-chat-tab">
              <!-- 完整的VoiceChatBox组件，包含自己的消息显示和交互 -->
              <VoiceChatBox 
                v-if="chatMode === 'voice'"
                :app-id="selectedApp.appId"
                :auto-connect="true"
                :prologue="selectedApp.prologue"
                :key="`voice-${selectedApp.appId}`"
                ref="voiceChatBoxRef"
                class="full-voice-chat"
                @message="handleVoiceMessage"
                @connectionChange="handleVoiceConnectionChange"
              />
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
import { listAppChatHistory, exportChatHistory } from '@/api/chatHistoryController'
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
const voiceMessagesContainer = ref<HTMLElement>()

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

const selectApp = async (app: AppVO) => {
  console.log('选择应用:', app)
  selectedApp.value = app
  messages.value = []
  streamingContent.value = ''
  
  if (chatMode.value === 'text') {
    // 文字模式加载对话历史
    await loadChatHistory(app.appId)
  } else if (chatMode.value === 'voice') {
    // 语音模式让VoiceChatBox处理初始化
    console.log('选择应用后，语音模式初始化VoiceChatBox')
    await nextTick(() => {
      if (voiceChatBoxRef.value) {
        voiceChatBoxRef.value.loadHistory?.()
      }
    })
  }
}

const selectAppById = (appId: string | number) => {
  const app = apps.value.find(app => app.appId == appId)
  if (app) {
    selectApp(app)
  }
}

const loadChatHistory = async (appId: string | number) => {
  console.log('🔄 开始加载文字聊天历史，appId:', appId)
  try {
    // 调用聊天历史API
    const response = await listAppChatHistory({ 
      appId: appId as any, 
      pageSize: 50 
    })
    
    console.log('📥 文字聊天历史API响应:', response)
    
    // 解析响应数据
    const records = response?.data?.data?.history?.records ?? response?.data?.history?.records ?? []
    
    if (!records || records.length === 0) {
      console.log('📝 没有文字聊天历史数据，清空消息列表')
      messages.value = []
      return
    }

    // 处理历史数据：按时间排序，区分AI和用户消息
    const chatMessages = records.map((item: any) => {
      const messageType = (item.messageType ?? item.type) as 'user' | 'ai'
      const content = item.message ?? item.content ?? ''
      const createTime = item.createTime ?? item.updateTime ?? item.timestamp
      
      // 处理时间戳
      let timestamp: Date
      if (typeof createTime === 'string') {
        timestamp = new Date(createTime)
      } else if (createTime instanceof Date) {
        timestamp = createTime
      } else if (typeof createTime === 'number') {
        timestamp = new Date(createTime)
      } else {
        timestamp = new Date()
      }

      return {
        id: item.id ?? Date.now() + Math.random(),
        content: content,
        type: messageType,
        timestamp: timestamp
      }
    })

    // 按时间排序（从早到晚）
    chatMessages.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime())
    
    messages.value = chatMessages
    console.log('✅ 文字聊天历史加载完成，共', messages.value.length, '条消息')
    
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('❌ 加载文字聊天历史失败:', error)
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
    // 构建SSE请求URL - 后端端口是8123
    const sseUrl = `/api/chat/chat?appId=${selectedApp.value.appId}&message=${encodeURIComponent(userMessage)}`
    console.log('🚀 开始SSE聊天请求:', sseUrl)
    
    // 创建EventSource连接
    const eventSource = new EventSource(sseUrl, { withCredentials: true })
    
    // 处理SSE消息
    eventSource.onmessage = (event) => {
      try {
        console.log('📥 收到SSE数据:', event.data)
        
        // 解析 {"d": "chunk"} 格式的数据
        const parsedData = JSON.parse(event.data)
        if (parsedData && parsedData.d) {
          streamingContent.value += parsedData.d
          scrollToBottom()
        }
      } catch (error) {
        console.error('❌ 解析SSE数据失败:', error, '原始数据:', event.data)
      }
    }
    
    // 处理完成事件
    eventSource.addEventListener('done', () => {
      console.log('✅ SSE流式响应完成')
      
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
      eventSource.close()
      scrollToBottom()
    })
    
    // 处理错误
    eventSource.onerror = (error) => {
      console.error('❌ SSE连接错误:', error)
      ElMessage.error('聊天连接失败')
      isStreaming.value = false
      streamingContent.value = ''
      eventSource.close()
    }
    
  } catch (error) {
    console.error('❌ 发送消息失败:', error)
    ElMessage.error('发送消息失败')
    isStreaming.value = false
  }
}

// 语音输入相关
const isVoiceInputMode = ref(false)
const isRecording = ref(false)

// 语音输入相关方法
const toggleVoiceInput = () => {
  isVoiceInputMode.value = !isVoiceInputMode.value
  if (!isVoiceInputMode.value && isRecording.value) {
    stopRecording()
  }
}

const toggleRecording = () => {
  if (isRecording.value) {
    stopRecording()
  } else {
    startRecording()
  }
}

const startRecording = () => {
  isRecording.value = true
  ElMessage.info('开始录音...')
  
  // 模拟录音过程，实际项目中需要集成语音识别API
  setTimeout(() => {
    if (isRecording.value) {
      stopRecording()
      // 模拟语音转文字结果
      inputMessage.value += '这是语音转换的文字内容'
    }
  }, 3000)
}

const stopRecording = () => {
  isRecording.value = false
  ElMessage.success('录音结束')
}

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

const exportChat = async () => {
  if (!selectedApp.value) {
    ElMessage.warning('请先选择一个应用')
    return
  }
  
  if (!userStore.user?.id) {
    ElMessage.warning('用户信息不存在，无法导出')
    return
  }
  
  try {
    ElMessage.info('正在导出对话历史...')
    
    // 调用后端导出API
    const response = await exportChatHistory({
      appId: selectedApp.value.appId as number,
      userId: userStore.user.id
    })
    
    console.log('导出API响应:', response)
    
    // 提取实际的文本内容
    let textContent = ''
    if (response.data?.code === 0 && response.data?.data) {
      textContent = response.data.data
    } else if (typeof response.data === 'string') {
      textContent = response.data
    } else if (typeof response === 'string') {
      textContent = response
    } else {
      console.error('无法解析导出数据:', response)
      ElMessage.error('导出数据格式错误')
      return
    }
    
    // 创建下载链接
    const blob = new Blob([textContent], { type: 'text/plain;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${selectedApp.value.appName}_聊天记录_${new Date().toISOString().split('T')[0]}.txt`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('对话历史导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败，请稍后重试')
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    // 只处理文字聊天的滚动，语音聊天由VoiceChatBox自己处理
    if (chatMode.value === 'text' && messagesContainer.value) {
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

const selectFromPicker = (event: Event) => {
  const app = apps.value.find(item => String(item.appId) === (event.target as HTMLSelectElement).value)
  if (app) selectApp(app)
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
  console.log('聊天模式变化:', { newMode, oldMode, hasApp: !!selectedApp.value })
  
  if (newMode === 'text' && selectedApp.value) {
    // 切换到文字模式时加载文字聊天历史
    console.log('切换到文字模式，加载文字聊天历史')
    await loadChatHistory(selectedApp.value.appId)
  } else if (newMode === 'voice' && selectedApp.value) {
    // 切换到语音模式时，VoiceChatBox会自动处理历史加载
    console.log('切换到语音模式，VoiceChatBox将自动处理历史和开场白')
    await nextTick(() => {
      if (voiceChatBoxRef.value) {
        // 调用VoiceChatBox的历史加载方法
        voiceChatBoxRef.value.loadHistory?.()
      }
    })
  }
})

// 处理语音消息事件
const handleVoiceMessage = (message: any) => {
  console.log('收到语音消息:', message)
  // 语音消息由VoiceChatBox自己处理，这里只做日志记录
}

// 处理语音连接状态变化
const handleVoiceConnectionChange = (connected: boolean) => {
  console.log('语音连接状态变化:', connected)
  // 可以在这里更新UI状态或显示连接提示
}

// 组件卸载时清理SSE连接
onUnmounted(() => {
  sseManager.close()
})
</script>
<style scoped>

.chat-page {height:calc(100dvh - var(--shell-header-height));display:flex;flex-direction:column;background:white;min-height:400px}
.chat-container {flex:1;min-height:0;overflow:hidden}
.chat-sidebar {background:#f8f9f5;border-right:1px solid var(--ui-border);display:flex;flex-direction:column}
.sidebar-header {padding:24px 18px 20px;display:flex;justify-content:space-between;align-items:center;gap:12px}
.header-title {display:flex;align-items:center;gap:8px;color:var(--ui-muted)}
.header-title h3 {font-size:12px;font-weight:600;letter-spacing:.3px}
.header-title>.el-icon {display:none}
.sidebar-header>.el-button {width:27px;height:27px;background:transparent;border-color:#d8e0d2;color:var(--ui-accent);border-radius:6px}
.app-list {overflow:auto;padding:0 10px 20px}
.app-item {display:flex;align-items:center;padding:12px 10px;border:1px solid transparent;border-radius:8px;cursor:pointer;position:relative;margin-bottom:6px;gap:10px}
.app-item:hover {background:#eef1e9}
.app-item.active {background:#e9eee3;border-color:#dce4d5}
.app-avatar {flex-shrink:0}.app-info {min-width:0;flex:1}
.app-name {font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.app-desc {font-size:11px;color:var(--ui-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:3px}
.unread-badge {position:absolute;right:5px;top:5px}
.chat-main {padding:0;min-width:0;display:flex;flex-direction:column}
.chat-content {display:flex;flex-direction:column;flex:1;min-height:0}
.chat-header {padding:20px 32px;display:flex;align-items:center;justify-content:space-between;gap:12px;border-bottom:1px solid #eef0ea}
.chat-header .header-left {display:flex;align-items:center;gap:12px;min-width:0;flex:1}
.chat-header .header-right {flex-shrink:0}
.app-details {min-width:0}.app-details h3 {font-size:16px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.app-details p {font-size:12px;color:var(--ui-muted);margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:50vw}
.chat-tabs {padding:14px 32px 0}
.tabs-container {display:inline-flex;background:#f0f2ec;padding:3px;border-radius:8px;gap:3px}
.tab-button {display:flex;align-items:center;gap:7px;cursor:pointer;border:0;border-radius:6px;padding:7px 16px;background:transparent;color:var(--ui-muted);font-size:12px}
.tab-button.active {background:white;color:var(--ui-accent);box-shadow:0 1px 3px rgb(28 43 29 / 8%);font-weight:600}
.tab-content-area {flex:1;min-height:0;display:flex;flex-direction:column}
.tab-content {height:100%;min-height:0;flex:1;display:flex;flex-direction:column}
.messages-container {overflow-y:auto;flex:1;min-height:0;padding:28px 32px}
.messages-list {max-width:820px;margin:0 auto}
.message-item {margin-bottom:24px;display:flex}.message-item.user {justify-content:flex-end}
.message-bubble {display:flex;gap:12px;align-items:flex-start;max-width:88%;min-width:0}
.message-avatar {flex-shrink:0;margin-top:2px}
.bubble-content {padding:14px 18px;border-radius:10px;background:#f3f5ef;border:1px solid #e9ede4;min-width:0;overflow-wrap:anywhere}
.user-message .bubble-content {background:#eaf1e5;border-color:#dfe8d8}
.message-text {line-height:1.85;font-size:14px;color:#354239}.message-time {margin-top:7px;font-size:10px;color:var(--ui-muted)}
.welcome-message {margin:12px 0 28px}
.typing-indicator {display:flex;gap:4px;padding-top:8px}.typing-indicator span {width:4px;height:4px;border-radius:50%;background:#92a48b}
.text-input-area {padding:16px 32px 22px;border-top:1px solid #edf0e7;background:white}
.input-container {display:flex;align-items:flex-end;gap:12px;max-width:820px;margin:auto;padding:12px;border:1px solid #dce3d6;border-radius:10px;background:#fafbf8}
.message-input {flex:1;min-width:0}.message-input :deep(.el-textarea__inner) {box-shadow:none;background:transparent;resize:none;padding:5px 4px;font-size:14px}
.input-actions {display:flex;gap:7px;align-items:center}.input-actions .el-button {margin:0;border-radius:7px}
.voice-input-panel {max-width:820px;margin:12px auto 0}.voice-controls {display:flex;align-items:center;gap:12px}
.record-button {padding:7px 16px;border:1px solid var(--ui-border);background:var(--ui-tint);border-radius:7px;cursor:pointer;color:var(--ui-accent)}
.record-button.recording {color:#a34d43;background:#fff2ed}.recording-indicator {font-size:12px;color:#a34d43}
.empty-chat {display:grid;place-items:center;flex:1}
.full-voice-chat {flex:1;min-height:0}
.mobile-role-picker {display:none}
@media(min-width:1440px) {.chat-header {padding-left:48px;padding-right:48px}.chat-tabs {padding-left:48px}}
@media(max-width:760px) {
 .chat-sidebar {display:none}.chat-page {min-height:420px}
 .mobile-role-picker {display:flex;align-items:center;gap:12px;padding:10px 20px;background:#f8f9f5;border-bottom:1px solid var(--ui-border);font-size:12px;color:var(--ui-muted)}
 .mobile-role-picker select {flex:1;min-width:0;border:1px solid var(--ui-border);background:white;color:var(--ui-text);padding:5px 8px;border-radius:5px;font-size:12px}
 .chat-header {padding:14px 20px}.app-details p {max-width:65vw;font-size:11px}.chat-tabs {padding:12px 20px 0}
 .messages-container {padding:20px 16px}.text-input-area {padding:12px 16px 16px}.input-container {gap:7px;padding:8px}
 .message-bubble {max-width:96%;gap:8px}.bubble-content {padding:12px 14px}.message-text {font-size:13px}
}

</style>
