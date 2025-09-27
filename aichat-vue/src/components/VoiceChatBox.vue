<template>
  <div class="voice-chat-box">
    <!-- 语音聊天头部 -->
    <div class="voice-chat-header">
      <div class="header-title">
        <el-icon><Microphone /></el-icon>
        <span>语音聊天</span>
      </div>
      <div class="header-actions">
        <el-dropdown @command="handleMenuCommand">
          <el-button circle size="small">
            <el-icon><MoreFilled /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="export">导出对话</el-dropdown-item>
              <el-dropdown-item command="clear">清空对话</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    
    <!-- 聊天消息区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <!-- 历史消息 -->
      <div
        v-for="message in messages"
        :key="message.timestamp"
        :class="['message', message.type]"
      >
        <div class="message-content">
          <div class="message-text">{{ message.content }}</div>
          <div class="message-time">{{ formatTime(message.timestamp) }}</div>
        </div>
      </div>
      
      <!-- 注释掉实时ASR识别结果 -->
      <!-- <div v-if="currentAsrText" class="message user realtime">
        <div class="message-content">
          <div class="message-text">
            {{ currentAsrText }}
            <span class="typing-indicator">|</span>
          </div>
          <div class="message-time">实时识别中...</div>
        </div>
      </div> -->
      
      <!-- 注释掉流式AI回复显示，避免重复显示 -->
      <!-- <div v-if="streamingMessage" class="message ai">
        <div class="message-content">
          <div class="message-text">
            {{ streamingMessage.content }}
            <span v-if="streamingMessage.isStreaming" class="typing-indicator">|</span>
          </div>
          <div class="message-time">{{ formatTime(streamingMessage.timestamp) }}</div>
        </div>
      </div> -->

    </div>

    <!-- 连接状态指示器 -->
    <div class="connection-status" :class="{ connected, recording }">
      <div class="status-indicator"></div>
      <span>{{ getStatusText() }}</span>
    </div>

    <!-- 语音控制区域 -->
    <div class="voice-control">
      <button
        :class="['voice-btn', { active: recording, speaking: audioPlaying }]"
        @click="toggleRecording"
      >
        <div class="voice-icon">🎤</div>
        <div v-if="recording || audioPlaying" class="sound-waves">
          <div class="wave"></div>
          <div class="wave"></div>
          <div class="wave"></div>
        </div>
      </button>
      
      <div class="voice-hint">
        {{ getVoiceHint() }}
      </div>
      
      <!-- 音量指示器 -->
      <div v-if="recording" class="volume-indicator">
        <div class="volume-bar">
          <div class="volume-fill" :style="{ width: volumePercent + '%' }"></div>
        </div>
        <div class="volume-status">{{ volumeStatus }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Microphone, MoreFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import voiceChatServiceInstance from '@/services/voiceChatService'
import { chatService } from '@/services/chatService'
import { listAppChatHistory, exportChatHistory } from '@/api/chatHistoryController'
import { getOpeningRemark } from '@/api/appController'

// 使用导入的服务实例
const voiceChatService = voiceChatServiceInstance
const userStore = useUserStore()

// 定义消息接口（与服务中的接口保持一致）
interface VoiceChatMessage {
  type: 'user' | 'ai'
  content: string
  timestamp: number
  audioUrl?: string
}

// Props
interface Props {
  appId: string | number
  wsUrl?: string
  autoConnect?: boolean
  prologue?: string
}

const props = withDefaults(defineProps<Props>(), {
  wsUrl: 'ws://localhost:8080/voice-chat',
  autoConnect: false,
  prologue: ''
})

// 定义事件
const emit = defineEmits<{
  message: [message: VoiceChatMessage]
  connectionChange: [connected: boolean]
  recordingChange: [recording: boolean]
  volumeChange: [volume: number, status: string, info: string]
}>()

// 响应式数据
const connected = ref(false)
const recording = ref(false)
const audioPlaying = ref(false)
const messages = ref<VoiceChatMessage[]>([])
const messagesContainer = ref<HTMLElement>()

// 实时ASR识别状态
const streamingMessage = ref<(VoiceChatMessage & { isStreaming: boolean }) | null>(null)
const currentAsrText = ref('')
const aiReplying = ref(false)

// 音量指示器
const currentVolume = ref(0)
const volumeStatus = ref('')
const volumePercent = computed(() => Math.min(100, currentVolume.value * 1000))

// 开场白播放状态
const prologuePlayed = ref(false)

// 加载历史聊天记录
const loadChatHistory = async () => {
  try {
    console.log('🔄 加载聊天历史记录...', { 
      appId: props.appId, 
      prologue: props.prologue,
      messagesLength: messages.value.length,
      prologuePlayed: prologuePlayed.value 
    })
    const appIdStr = props.appId.toString()
    // 使用 API 层：分别拉取 ai 与 user 历史并合并
    const appIdParam = String(props.appId) // 避免 JS Number 精度丢失
    const pageSize = 50
    console.log('🔎 请求聊天历史：appId=', appIdParam, 'pageSize=', pageSize)
    
    const [aiRes, userRes] = await Promise.all([
      listAppChatHistory({ appId: appIdParam as any, messageType: 'ai', pageSize }),
      listAppChatHistory({ appId: appIdParam as any, messageType: 'user', pageSize })
    ])
    console.log('📥 aiRes=', aiRes)
    console.log('📥 userRes=', userRes)
    
    let aiRecords = aiRes?.data?.data?.history?.records ?? aiRes?.data?.history?.records ?? []
    let userRecords = userRes?.data?.data?.history?.records ?? userRes?.data?.history?.records ?? []
    let combined = [...aiRecords, ...userRecords]
    
    // 若按 messageType 拉取为空，回退为不带筛选的一次性请求
    if (combined.length === 0) {
      console.warn('⚠️ 按 messageType 拉取为空，尝试不加筛选回退请求')
      const allRes = await listAppChatHistory({ appId: appIdParam as any, pageSize })
      console.log('📥 allRes=', allRes)
      combined = allRes?.data?.data?.history?.records ?? allRes?.data?.history?.records ?? []
    }
    
    const voiceMessages: VoiceChatMessage[] = combined.map((item: any) => {
      const type = (item.messageType ?? item.type) as 'user' | 'ai'
      const content = item.message ?? item.content ?? ''
      const createTime = item.createTime ?? item.updateTime ?? item.timestamp
      const ts = typeof createTime === 'string'
        ? new Date(createTime).getTime()
        : createTime instanceof Date
          ? createTime.getTime()
          : typeof createTime === 'number'
            ? createTime
            : Date.now()
      return { type, content, timestamp: ts }
    })
    
    voiceMessages.sort((a, b) => a.timestamp - b.timestamp)
    messages.value = voiceMessages
    
    // 检查是否需要播放开场白（每次切换到语音模式都播放，但在同一次会话中只播放一次）
    console.log('🔍 检查开场白条件:', {
      messagesLength: messages.value.length,
      hasPrologue: !!props.prologue,
      prologuePlayed: prologuePlayed.value,
      shouldPlay: props.prologue && !prologuePlayed.value
    })
    
    if (props.prologue && !prologuePlayed.value) {
      console.log('📢 满足开场白条件，添加开场白到对话框并播放TTS:', props.prologue)
      
      // 开场白必须显示在语音对话界面上
      const prologueMessage = {
        type: 'ai' as const,
        content: props.prologue,
        timestamp: Date.now()
      }
      
      // 添加开场白到消息列表的最后面（最新消息位置）
      messages.value.push(prologueMessage)
      console.log('📝 开场白消息已添加到语音对话界面，当前消息总数:', messages.value.length)
      
      // 标记开场白已播放，防止重复播放
      prologuePlayed.value = true
      
      // 播放开场白TTS音频
      await playPrologueTTS(props.prologue)
    }
    
    console.log('✅ 聊天历史加载完成，共', messages.value.length, '条消息')
    
    // 滚动到底部
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('❌ 加载聊天历史失败:', error)
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 播放开场白TTS音频
const playPrologueTTS = async (prologue: string) => {
  // 防止重复播放
  if (audioPlaying.value) {
    console.log('⚠️ 音频正在播放中，跳过开场白TTS')
    return
  }
  
  try {
    console.log('🔊 开始播放开场白TTS:', prologue)
    audioPlaying.value = true
    
    // 调用开场白TTS API
    const response = await getOpeningRemark({ prologue })
    console.log('🎵 开场白TTS API响应:', response)
    
    if (response.data?.code === 0 && response.data?.data) {
      const base64Audio = response.data.data
      
      // 将Base64音频转换为ArrayBuffer
      const audioData = atob(base64Audio)
      const audioArray = new Uint8Array(audioData.length)
      for (let i = 0; i < audioData.length; i++) {
        audioArray[i] = audioData.charCodeAt(i)
      }
      
      // 使用Web Audio API播放
      const audioContext = new AudioContext()
      try {
        const audioBuffer = await audioContext.decodeAudioData(audioArray.buffer.slice(0))
        const source = audioContext.createBufferSource()
        source.buffer = audioBuffer
        source.connect(audioContext.destination)
        
        source.onended = () => {
          audioContext.close()
          audioPlaying.value = false
          console.log('🔊 开场白TTS播放完成')
        }
        
        source.start()
        console.log('🔊 开场白TTS开始播放，时长:', audioBuffer.duration.toFixed(2), '秒')
        
      } catch (decodeError) {
        console.error('❌ 音频解码失败，尝试备选方案:', decodeError)
        audioContext.close()
        
        // 备选方案：使用Audio元素
        const blob = new Blob([audioArray.buffer], { type: 'audio/wav' })
        const audioUrl = URL.createObjectURL(blob)
        const audio = new Audio(audioUrl)
        
        audio.onended = () => {
          URL.revokeObjectURL(audioUrl)
          audioPlaying.value = false
          console.log('🔊 开场白TTS播放完成（备选方案）')
        }
        
        audio.onerror = (error) => {
          URL.revokeObjectURL(audioUrl)
          audioPlaying.value = false
          console.error('❌ 开场白TTS播放失败（备选方案）:', error)
        }
        
        await audio.play()
      }
      
    } else {
      console.error('❌ 获取开场白TTS失败:', response)
      audioPlaying.value = false
    }
  } catch (error) {
    console.error('❌ 播放开场白TTS失败:', error)
    audioPlaying.value = false
  }
}

// 格式化时间
const formatTime = (timestamp: number) => {
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取状态文本
const getStatusText = () => {
  if (!connected.value) return '未连接'
  if (recording.value) return '录音中'
  if (audioPlaying.value) return 'AI回复中'
  return '已连接'
}

// 获取语音提示文本
const getVoiceHint = () => {
  if (audioPlaying.value) return 'AI正在回复中...'
  if (recording.value) return '正在录音，点击停止并断开'
  if (connected.value) return '已连接，点击开始录音'
  return '点击连接并开始录音'
}

// 处理菜单命令
const handleMenuCommand = async (command: string) => {
  switch (command) {
    case 'export':
      await exportVoiceChat()
      break
    case 'clear':
      await clearVoiceChat()
      break
  }
}

// 导出语音对话
const exportVoiceChat = async () => {
  if (!props.appId) {
    ElMessage.warning('应用ID不存在，无法导出')
    return
  }
  
  if (!userStore.user?.id) {
    ElMessage.warning('用户信息不存在，无法导出')
    return
  }
  
  try {
    ElMessage.info('正在导出语音对话历史...')
    
    // 调用后端导出API
    const response = await exportChatHistory({
      appId: props.appId as number,
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
    link.download = `语音聊天记录_${props.appId}_${new Date().toISOString().split('T')[0]}.txt`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('语音对话历史导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败，请稍后重试')
  }
}

// 清空语音对话
const clearVoiceChat = async () => {
  try {
    await ElMessageBox.confirm('确定要清空当前语音对话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    messages.value = []
    streamingMessage.value = null
    currentAsrText.value = ''
    prologuePlayed.value = false
    
    ElMessage.success('语音对话已清空')
  } catch {
    // 用户取消
  }
}


// 方法
const toggleRecording = async () => {
  if (audioPlaying.value) return // AI回复中不允许操作
  
  try {
    console.log('🔄 切换录音状态，当前状态:', { 
      recording: recording.value, 
      connected: connected.value 
    })
    
    // 使用新的切换方法，自动管理连接和录音状态
    const isRecordingNow = await voiceChatService.toggleRecording(props.appId)
    
    // 更新本地状态
    recording.value = isRecordingNow
    connected.value = voiceChatService.getConnectionStatus()
    
    if (isRecordingNow) {
      // 开始录音
      console.log('✅ 录音已开始，连接已建立')
      aiReplying.value = false
      currentAsrText.value = '' // 清空之前的实时识别文本
    } else {
      // 停止录音并断开连接
      console.log('✅ 录音已停止，连接已断开')
      currentAsrText.value = '' // 清空实时ASR文本
      aiReplying.value = true // 等待最终结果
    }
    
  } catch (error) {
    console.error('❌ 切换录音状态失败:', error)
    // 发生错误时，同步实际状态
    recording.value = voiceChatService.getRecordingStatus()
    connected.value = voiceChatService.getConnectionStatus()
  }
}


// 设置回调
const setupCallbacks = () => {
  // 连接状态变化回调
  voiceChatService.onConnectionChange = (isConnected: boolean) => {
    connected.value = isConnected
    emit('connectionChange', isConnected)
    console.log('连接状态变化:', isConnected)
  }

  // 音量变化回调
  voiceChatService.onVolumeChange = (volume: number, status: string, info: string) => {
    currentVolume.value = volume
    volumeStatus.value = info
    
    emit('volumeChange', volume, status, info)
  }

  // 消息回调
  voiceChatService.onMessage = (message: VoiceChatMessage) => {
    console.log('收到消息:', message)
    
    if (message.type === 'user') {
      // ASR识别结果 - 添加到消息列表
      currentAsrText.value = '' // 清空实时显示
      messages.value.push(message)
      aiReplying.value = true // 开始等待AI回复
    } else if (message.type === 'ai') {
      // AI回复完成 - 添加到消息列表
      aiReplying.value = false
      audioPlaying.value = true // 开始播放TTS
      messages.value.push(message)
      
      // 清空流式显示
      streamingMessage.value = null
      
      // TTS播放完成后重置状态（这里简化处理，实际应该监听音频播放完成）
      setTimeout(() => {
        audioPlaying.value = false
      }, 3000) // 假设3秒后播放完成
    }
    
    emit('message', message)
    
    // 滚动到底部
    nextTick(() => {
      scrollToBottom()
    })
  }

  // 流式消息回调
  voiceChatService.onStreamingMessage = (message: VoiceChatMessage & { isStreaming: boolean }) => {
    console.log('收到流式消息:', message)
    
    if (message.type === 'ai') {
      // 更新流式显示
      streamingMessage.value = message
      aiReplying.value = message.isStreaming
      
      // 滚动到底部
      nextTick(() => {
        scrollToBottom()
      })
    }
  }

  // 静音检测回调
  voiceChatService.onSilenceDetected = () => {
    console.log('检测到静音，但继续保持录音连接')
  }

  // ASR partial结果回调 - 实时显示识别过程
  voiceChatService.onAsrPartial = (text: string) => {
    console.log('🎤 收到ASR部分结果:', text)
    currentAsrText.value = text // 实时更新显示
    
    // 滚动到底部以显示实时识别
    nextTick(() => {
      scrollToBottom()
    })
  }

  // ASR final结果回调 - 最终确认结果
  voiceChatService.onAsrFinal = (text: string) => {
    console.log('🎤 收到ASR最终结果:', text)
    currentAsrText.value = '' // 清空实时显示，因为最终结果会通过onMessage添加到消息列表
  }
}

// 生命周期
onMounted(async () => {
  console.log('🚀 VoiceChatBox组件挂载，props:', { appId: props.appId, prologue: props.prologue })
  setupCallbacks()
  
  // 加载历史聊天记录
  await loadChatHistory()
})

onUnmounted(() => {
  voiceChatService.disconnect()
})

// 暴露方法给父组件
defineExpose({
  connect: () => voiceChatService.connect(props.appId),
  disconnect: () => voiceChatService.disconnect(),
  startRecording: () => voiceChatService.startRecording(),
  stopRecording: () => voiceChatService.stopRecording(),
  toggleRecording: () => voiceChatService.toggleRecording(props.appId), // 新增切换方法
  getConnectionStatus: () => voiceChatService.getConnectionStatus(),
  getRecordingStatus: () => voiceChatService.getRecordingStatus(),
  getStats: () => voiceChatService.getStats(),
  isActive: () => voiceChatService.isActive(), // 新增活跃状态检查
  connected: computed(() => connected.value),
  recording: computed(() => recording.value),
  loadHistory: loadChatHistory
})
</script>

<style scoped>
.voice-chat-box {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-height: 600px;
  background: #f8f9fa;
  border-radius: 12px;
  overflow: hidden;
}

/* 语音聊天头部 */
.voice-chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: white;
  border-bottom: 1px solid #e9ecef;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #2c3e50;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 聊天消息区域 */
.chat-messages {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: white;
  border-bottom: 1px solid #e9ecef;
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
  border-radius: 18px;
  position: relative;
}

.message.user .message-content {
  background: #409eff;
  color: white;
  border-bottom-right-radius: 4px;
}

.message.ai .message-content {
  background: #e9ecef;
  color: #333;
  border-bottom-left-radius: 4px;
}

/* 注释掉实时ASR识别气泡样式 */
/* .message.realtime .message-content {
  background: #fff3cd;
  border: 1px dashed #ffc107;
} */

/* 注释掉流式AI回复气泡样式 */
/* .message.streaming .message-content {
  background: #d1ecf1;
  border: 1px solid #bee5eb;
} */



.message-text {
  font-size: 14px;
  line-height: 1.4;
  word-wrap: break-word;
}

.message-time {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 4px;
}

.typing-indicator {
  animation: blink 1s infinite;
  font-weight: bold;
}



/* 连接状态指示器 */
.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  background: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
  font-size: 12px;
  color: #6c757d;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dc3545;
  transition: background-color 0.3s ease;
}

.connection-status.connected .status-indicator {
  background: #28a745;
}

.connection-status.recording .status-indicator {
  background: #ffc107;
  animation: pulse 1s infinite;
}

/* 语音控制区域 */
.voice-control {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px;
  background: #f8f9fa;
}

.voice-btn {
  position: relative;
  width: 80px;
  height: 80px;
  border: none;
  border-radius: 50%;
  background: #409eff;
  color: white;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.voice-btn:disabled {
  background: #6c757d;
  cursor: not-allowed;
  box-shadow: none;
}

.voice-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
}

.voice-btn.active {
  background: #f56c6c;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
  animation: pulse-red 1.5s infinite;
}

.voice-btn.speaking {
  background: #67c23a;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
  animation: pulse-green 1.5s infinite;
}

.voice-icon {
  font-size: 32px;
  z-index: 2;
}

.sound-waves {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  gap: 3px;
  z-index: 1;
}

.wave {
  width: 3px;
  height: 20px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 2px;
  animation: wave-animation 1s infinite ease-in-out;
}

.wave:nth-child(2) {
  animation-delay: 0.1s;
}

.wave:nth-child(3) {
  animation-delay: 0.2s;
}

.voice-hint {
  font-size: 14px;
  color: #666;
  text-align: center;
  min-height: 20px;
}

/* 音量指示器 */
.volume-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 200px;
}

.volume-bar {
  width: 100%;
  height: 6px;
  background: #e9ecef;
  border-radius: 3px;
  overflow: hidden;
}

.volume-fill {
  height: 100%;
  background: linear-gradient(90deg, #28a745, #ffc107, #dc3545);
  transition: width 0.1s ease;
}

.volume-status {
  font-size: 11px;
  color: #6c757d;
  text-align: center;
}

/* 动画 */
@keyframes pulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes pulse-red {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(245, 108, 108, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0);
  }
}

@keyframes pulse-green {
  0% {
    box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(103, 194, 58, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(103, 194, 58, 0);
  }
}



@keyframes wave-animation {
  0%, 40%, 100% {
    transform: scaleY(0.4);
  }
  20% {
    transform: scaleY(1);
  }
}

@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}



/* 滚动条样式 */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 响应式 */
@media (max-width: 768px) {
  .voice-btn {
    width: 70px;
    height: 70px;
  }
  
  .voice-icon {
    font-size: 28px;
  }
  
  .message-content {
    max-width: 85%;
  }
  
  .volume-indicator {
    width: 150px;
  }
}
</style>