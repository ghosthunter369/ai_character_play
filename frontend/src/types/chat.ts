// Chat related type definitions

export interface ChatMessage {
  id: string
  type: 'user' | 'assistant'
  content: string
  timestamp: number
  status: 'partial' | 'final'
}

export interface TokenEvent {
  type: 'token' | 'done'
  text: string
  seq: number
}

export interface StreamEvent {
  type: 'token' | 'done' | 'error'
  data: string
  timestamp: number
  messageId?: string
}

export interface ChatSession {
  sessionId: string
  messages: ChatMessage[]
  startTime: number
  lastActivity: number
}

export interface ASRResult {
  text: string
  confidence: number
  isFinal: boolean
  timestamp: number
}

export type ChatState = 'idle' | 'listening' | 'thinking' | 'speaking'
export type MessageType = 'user' | 'assistant' | 'system'
export type MessageStatus = 'partial' | 'final' | 'error'