<template>
  <div class="floating-chat-box">
    <!-- 聊天框触发按钮 -->
    <div 
      v-if="!isExpanded" 
      class="chat-trigger" 
      @click="toggleChat"
    >
      <el-icon class="chat-icon"><ChatDotRound /></el-icon>
      <div class="pulse-ring"></div>
    </div>

    <!-- 展开的聊天框 -->
    <transition name="chat-expand">
      <div v-if="isExpanded" class="chat-container">
        <!-- 聊天框头部 -->
        <div class="chat-header">
          <div class="header-left">
            <el-avatar :size="32" class="bot-avatar">
              <el-icon><User /></el-icon>
            </el-avatar>
            <div class="bot-info">
              <h4 class="bot-name">AI助手</h4>
              <span class="bot-status">在线</span>
            </div>
          </div>
          <div class="header-actions">
            <!-- 聊天模式切换 -->
            <el-tooltip content="切换聊天模式" placement="top">
              <el-button 
                :type="chatMode === 'voice' ? 'primary' : 'default'"
                size="small" 
                circle
                @click="toggleChatMode"
              >
                <el-icon v-if="chatMode === 'voice'"><Microphone /></el-icon>
                <el-icon v-else><ChatLineRound /></el-icon>
              </el-button>
            </el-tooltip>
            
            <!-- 最小化按钮 -->
            <el-button 
              type="info" 
              size="small" 
              circle 
              @click="toggleChat"
            >
              <el-icon><Minus /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 聊天模式指示器 -->
        <div class="chat-mode-indicator">
          <el-tag 
            :type="chatMode === 'voice' ? 'warning' : 'primary'" 
            size="small"
            class="mode-tag"
          >
            <el-icon>
              <Microphone v-if="chatMode === 'voice'" />
              <ChatLineRound v-else />
            </el-icon>
            {{ chatMode === 'voice' ? '语音聊天' : '文字聊天' }}
          </el-tag>
          
          <el-button 
            type="primary" 
            size="small" 
            text
            @click="startQuickChat"
          >
            快速开始
          </el-button>
        </div>

        <!-- 聊天内容区域 -->
        <div class="chat-content">
          <div class="message-list" ref="messageListRef">
            <div 
              v-for="message in messages" 
              :key="message.id"
              :class="['message-item', message.type]"
            >
              <div class="message-avatar">
                <el-avatar :size="28">
                  <el-icon v-if="message.type === 'bot'"><User /></el-icon>
                  <el-icon v-else><User /></el-icon>
                </el-avatar>
              </div>
              <div class="message-content">
                <div class="message-bubble">
                  {{ message.content }}
                </div>
                <div class="message-time">
                  {{ formatTime(message.timestamp) }}
                </div>
              </div>
            </div>
          </div>

          <!-- 空状态 -->
          <div v-if="messages.length === 0" class="empty-chat">
            <el-icon class="empty-icon"><ChatDotRound /></el-icon>
            <p class="empty-text">开始你的AI对话之旅</p>
            <div class="quick-actions">
              <el-button size="small" @click="sendQuickMessage('你好')">
                👋 打个招呼
              </el-button>
              <el-button size="small" @click="sendQuickMessage('帮我写一首诗')">
                ✍️ 写首诗
              </el-button>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="chat-input">
          <!-- 语音模式 -->
          <div v-if="chatMode === 'voice'" class="voice-input">
            <el-button 
              :type="isRecording ? 'danger' : 'primary'"
              :loading="isRecording"
              size="large"
              circle
              class="voice-btn"
              @mousedown="startRecording"
              @mouseup="stopRecording"
              @mouseleave="stopRecording"
            >
              <el-icon><Microphone /></el-icon>
            </el-button>
            <div class="voice-hint">
              {{ isRecording ? '正在录音...' : '按住说话' }}
            </div>
          </div>

          <!-- 文字模式 -->
          <div v-else class="text-input">
            <el-input
              v-model="inputMessage"
              placeholder="输入消息..."
              @keyup.enter="sendMessage"
              class="message-input"
            >
              <template #append>
                <el-button 
                  type="primary" 
                  :disabled="!inputMessage.trim()"
                  @click="sendMessage"
                >
                  <el-icon><Promotion /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound, User, Microphone, ChatLineRound, Minus,
  Promotion
} from '@element-plus/icons-vue'

const router = useRouter()

// 响应式数据
const isExpanded = ref(false)
const chatMode = ref<'voice' | 'text'>('text')
const inputMessage = ref('')
const isRecording = ref(false)
const messageListRef = ref<HTMLElement>()

interface Message {
  id: string
  type: 'user' | 'bot'
  content: string
  timestamp: number
}

const messages = ref<Message[]>([])

// 方法
const toggleChat = () => {
  isExpanded.value = !isExpanded.value
}

const toggleChatMode = () => {
  chatMode.value = chatMode.value === 'voice' ? 'text' : 'voice'
  ElMessage.success(`已切换到${chatMode.value === 'voice' ? '语音' : '文字'}聊天模式`)
}

const startQuickChat = () => {
  router.push('/chat')
}

const sendMessage = () => {
  if (!inputMessage.value.trim()) return

  const userMessage: Message = {
    id: Date.now().toString(),
    type: 'user',
    content: inputMessage.value,
    timestamp: Date.now()
  }

  messages.value.push(userMessage)
  
  // 模拟AI回复
  setTimeout(() => {
    const botMessage: Message = {
      id: (Date.now() + 1).toString(),
      type: 'bot',
      content: '这是一个演示回复，实际使用时会连接到真实的AI服务。',
      timestamp: Date.now()
    }
    messages.value.push(botMessage)
    scrollToBottom()
  }, 1000)

  inputMessage.value = ''
  scrollToBottom()
}

const sendQuickMessage = (message: string) => {
  inputMessage.value = message
  sendMessage()
}

const startRecording = () => {
  isRecording.value = true
  ElMessage.info('开始录音...')
}

const stopRecording = () => {
  if (!isRecording.value) return
  
  isRecording.value = false
  ElMessage.success('录音结束，正在识别...')
  
  // 模拟语音识别
  setTimeout(() => {
    const recognizedText = '这是语音识别的结果'
    inputMessage.value = recognizedText
    chatMode.value = 'text' // 临时切换到文字模式显示识别结果
    setTimeout(() => {
      sendMessage()
      chatMode.value = 'voice' // 发送后切回语音模式
    }, 500)
  }, 1500)
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

const formatTime = (timestamp: number) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}
</script>

<style scoped>
.floating-chat-box {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 1000;
}

/* 触发按钮 */
.chat-trigger {
  position: relative;
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
}

.chat-trigger:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(102, 126, 234, 0.4);
}

.chat-icon {
  font-size: 24px;
  color: white;
}

.pulse-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 2px solid rgba(102, 126, 234, 0.5);
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(1.5);
    opacity: 0;
  }
}

/* 聊天容器 */
.chat-container {
  width: 360px;
  height: 500px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 聊天头部 */
.chat-header {
  padding: 16px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bot-avatar {
  background: rgba(255, 255, 255, 0.2);
}

.bot-info h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.bot-status {
  font-size: 12px;
  opacity: 0.8;
}

.header-actions {
  display: flex;
  gap: 8px;
}

/* 模式指示器 */
.chat-mode-indicator {
  padding: 12px 16px;
  background: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mode-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 聊天内容 */
.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.message-list {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item {
  display: flex;
  gap: 8px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-content {
  max-width: 70%;
}

.message-item.user .message-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-bubble {
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.4;
}

.message-item.bot .message-bubble {
  background: #f1f3f4;
  color: #333;
}

.message-item.user .message-bubble {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}

/* 空状态 */
.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  color: #ccc;
  margin-bottom: 16px;
}

.empty-text {
  color: #666;
  margin-bottom: 20px;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 输入区域 */
.chat-input {
  padding: 16px;
  border-top: 1px solid #e9ecef;
  background: white;
}

.voice-input {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.voice-btn {
  width: 60px;
  height: 60px;
}

.voice-hint {
  font-size: 12px;
  color: #666;
}

.text-input {
  width: 100%;
}

.message-input :deep(.el-input-group__append) {
  padding: 0;
}

.message-input :deep(.el-input-group__append .el-button) {
  margin: 0;
  border-radius: 0 4px 4px 0;
}

/* 动画 */
.chat-expand-enter-active,
.chat-expand-leave-active {
  transition: all 0.3s ease;
  transform-origin: bottom right;
}

.chat-expand-enter-from {
  opacity: 0;
  transform: scale(0.8) translateY(20px);
}

.chat-expand-leave-to {
  opacity: 0;
  transform: scale(0.8) translateY(20px);
}

/* 响应式设计 */
@media (max-width: 480px) {
  .floating-chat-box {
    bottom: 16px;
    right: 16px;
  }
  
  .chat-container {
    width: calc(100vw - 32px);
    height: calc(100vh - 100px);
    max-width: 360px;
  }
  
  .chat-trigger {
    width: 50px;
    height: 50px;
  }
  
  .chat-icon {
    font-size: 20px;
  }
}

/* 滚动条样式 */
.message-list::-webkit-scrollbar {
  width: 4px;
}

.message-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 2px;
}

.message-list::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 2px;
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: #999;
}
</style>