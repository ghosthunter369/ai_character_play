// Connection related type definitions

export interface ConnectionState {
  audio: 'disconnected' | 'connecting' | 'connected' | 'error'
  text: 'disconnected' | 'connecting' | 'connected' | 'error'
  tts: 'disconnected' | 'connecting' | 'connected' | 'error'
}

export interface SessionConfig {
  sessionId: string
  audioWsUrl: string
  textSseUrl: string
  ttsWsUrl: string
}

export interface WebSocketConfig {
  url: string
  protocols?: string[]
  reconnect: boolean
  maxRetries: number
  retryDelay: number
}

export interface SSEConfig {
  url: string
  withCredentials: boolean
  reconnect: boolean
  maxRetries: number
}

export interface ConnectionError {
  type: 'network' | 'permission' | 'timeout' | 'server'
  message: string
  code?: number
  timestamp: number
}

export interface ServiceStatus {
  asr: boolean
  chat: boolean
  tts: boolean
  lastCheck: number
}

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error'
export type ServiceType = 'audio' | 'text' | 'tts'