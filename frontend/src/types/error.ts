// Error handling related type definitions

export enum ErrorType {
  NETWORK_ERROR = 'network_error',
  PERMISSION_DENIED = 'permission_denied',
  AUDIO_CONTEXT_ERROR = 'audio_context_error',
  WEBSOCKET_ERROR = 'websocket_error',
  SSE_ERROR = 'sse_error',
  SESSION_TIMEOUT = 'session_timeout',
  TTS_TIMEOUT = 'tts_timeout',
  MICROPHONE_ERROR = 'microphone_error',
  AUDIO_PLAYBACK_ERROR = 'audio_playback_error',
  CONNECTION_LOST = 'connection_lost'
}

export enum ErrorSeverity {
  LOW = 'low',
  MEDIUM = 'medium',
  HIGH = 'high',
  CRITICAL = 'critical'
}

export interface AppError {
  type: ErrorType
  message: string
  severity: ErrorSeverity
  timestamp: number
  details?: Record<string, any>
  stack?: string
}

export interface ErrorHandler {
  type: ErrorType
  message: string
  severity: ErrorSeverity
  retry?: () => Promise<void>
  fallback?: () => void
  userGuidance?: string
}

export interface RetryConfig {
  maxRetries: number
  initialDelay: number
  maxDelay: number
  backoffFactor: number
}

export interface ErrorManagerConfig {
  showToast: boolean
  logErrors: boolean
  retryConfig: RetryConfig
}