// Connection state management store
import { defineStore } from 'pinia'
import { ref, computed, readonly } from 'vue'
import type { ConnectionState, ConnectionStatus } from '@/types/connection'

export const useConnectionStore = defineStore('connection', () => {
  // State
  const connections = ref<ConnectionState>({
    audio: 'disconnected',
    text: 'disconnected',
    tts: 'disconnected'
  })
  
  const sessionId = ref<string>('')
  const errors = ref<Record<string, string>>({})

  // Getters
  const isAllConnected = computed(() => {
    return Object.values(connections.value).every(state => state === 'connected')
  })

  const hasErrors = computed(() => {
    return Object.keys(errors.value).length > 0
  })

  const connectionStatus = computed(() => {
    if (isAllConnected.value) return 'connected'
    if (Object.values(connections.value).some(state => state === 'connecting')) return 'connecting'
    if (Object.values(connections.value).some(state => state === 'error')) return 'error'
    return 'disconnected'
  })

  // Actions
  const updateConnectionState = (
    type: keyof ConnectionState, 
    state: ConnectionStatus
  ) => {
    connections.value[type] = state
  }

  const setError = (type: string, error: string) => {
    errors.value[type] = error
  }

  const clearError = (type: string) => {
    delete errors.value[type]
  }

  const clearAllErrors = () => {
    errors.value = {}
  }

  const setSessionId = (id: string) => {
    sessionId.value = id
  }

  const resetConnections = () => {
    connections.value = {
      audio: 'disconnected',
      text: 'disconnected',
      tts: 'disconnected'
    }
    sessionId.value = ''
    errors.value = {}
  }

  return {
    // Readonly state
    connections: readonly(connections),
    sessionId: readonly(sessionId),
    errors: readonly(errors),
    
    // Computed
    isAllConnected,
    hasErrors,
    connectionStatus,
    
    // Actions
    updateConnectionState,
    setError,
    clearError,
    clearAllErrors,
    setSessionId,
    resetConnections
  }
})