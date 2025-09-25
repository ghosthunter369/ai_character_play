// Type definitions index - exports all types for easy importing

// Audio types
export type {
  AudioConfig,
  AudioChunk,
  AudioFormatSpec,
  AudioBufferData,
  AudioProcessingOptions,
  AudioState,
  AudioFormatType
} from './audio'

// Chat types
export type {
  ChatMessage,
  TokenEvent,
  StreamEvent,
  ChatSession,
  ASRResult,
  ChatState,
  MessageType,
  MessageStatus
} from './chat'

// Connection types
export type {
  ConnectionState,
  SessionConfig,
  WebSocketConfig,
  SSEConfig,
  ConnectionError,
  ServiceStatus,
  ConnectionStatus,
  ServiceType
} from './connection'

// Error types
export type {
  AppError,
  ErrorHandler,
  RetryConfig,
  ErrorManagerConfig
} from './error'

export {
  ErrorType,
  ErrorSeverity
} from './error'