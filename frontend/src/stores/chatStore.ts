// Chat state management store
import { defineStore } from 'pinia'
import { ref, computed, readonly } from 'vue'
import type { ChatMessage, ChatState } from '@/types/chat'

export const useChatStore = defineStore('chat', () => {
  // State
  const messages = ref<ChatMessage[]>([])
  const currentMessage = ref<ChatMessage | null>(null)
  const chatState = ref<ChatState>('idle')

  // Getters
  const messageCount = computed(() => messages.value.length)
  const isActive = computed(() => chatState.value !== 'idle')

  // Actions
  const addMessage = (message: Omit<ChatMessage, 'id' | 'timestamp'>) => {
    const newMessage: ChatMessage = {
      ...message,
      id: generateId(),
      timestamp: Date.now()
    }
    messages.value.push(newMessage)
    return newMessage
  }

  const updateCurrentMessage = (text: string, status: 'partial' | 'final') => {
    if (!currentMessage.value) {
      currentMessage.value = addMessage({
        type: 'assistant',
        content: text,
        status
      })
    } else {
      currentMessage.value.content = text
      currentMessage.value.status = status
    }
  }

  const finalizeCurrent = () => {
    if (currentMessage.value) {
      currentMessage.value.status = 'final'
      currentMessage.value = null
    }
  }

  const setChatState = (state: ChatState) => {
    chatState.value = state
  }

  const clearMessages = () => {
    messages.value = []
    currentMessage.value = null
    chatState.value = 'idle'
  }

  // Helper function to generate unique IDs
  const generateId = (): string => {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  return {
    // Readonly state
    messages: readonly(messages),
    currentMessage: readonly(currentMessage),
    chatState: readonly(chatState),
    
    // Computed
    messageCount,
    isActive,
    
    // Actions
    addMessage,
    updateCurrentMessage,
    finalizeCurrent,
    setChatState,
    clearMessages
  }
})