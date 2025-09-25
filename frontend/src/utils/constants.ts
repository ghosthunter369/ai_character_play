// Application constants

import type { AudioConfig, AudioFormatSpec } from '@/types/audio'
import type { WebSocketConfig, SSEConfig } from '@/types/connection'

// Audio configuration constants - PCM 16-bit, 16kHz, mono (Requirement 6.1)
export const AUDIO_CONFIG: AudioConfig = {
  sampleRate: 16000,
  channels: 1,
  bitDepth: 16,
  frameSize: 640 // 40ms at 16kHz (640 samples)
}

// Alternative frame size for 20ms chunks (Requirement 6.2)
export const AUDIO_CONFIG_20MS: AudioConfig = {
  sampleRate: 16000,
  channels: 1,
  bitDepth: 16,
  frameSize: 320 // 20ms at 16kHz (320 samples)
}

// Audio format specification
export const AUDIO_FORMAT: AudioFormatSpec = {
  sampleRate: 16000,
  channels: 1,
  bitDepth: 16,
  encoding: 'PCM'
}

// API endpoints configuration
export const API_ENDPOINTS = {
  BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  AUDIO_WS: import.meta.env.VITE_AUDIO_WS_URL || 'ws://localhost:8080/audio',
  TEXT_SSE: import.meta.env.VITE_TEXT_SSE_URL || 'http://localhost:8080/chat/stream',
  TTS_WS: import.meta.env.VITE_TTS_WS_URL || 'ws://localhost:8080/tts'
}

// WebSocket configuration
export const WEBSOCKET_CONFIG: WebSocketConfig = {
  url: '',
  reconnect: true,
  maxRetries: 5,
  retryDelay: 1000
}

// SSE configuration
export const SSE_CONFIG: SSEConfig = {
  url: '',
  withCredentials: false,
  reconnect: true,
  maxRetries: 5
}

// Connection retry configuration
export const CONNECTION_CONFIG = {
  MAX_RETRIES: 5,
  RETRY_DELAY: 1000,
  RETRY_BACKOFF_MULTIPLIER: 2,
  MAX_RETRY_DELAY: 30000,
  HEARTBEAT_INTERVAL: 30000,
  CONNECTION_TIMEOUT: 10000
}

// Audio processing constants (Requirement 6.2, 6.3)
export const AUDIO_CONSTANTS = {
  CHUNK_SIZE_40MS: 1280, // bytes for 40ms at 16kHz 16-bit (640 samples * 2 bytes)
  CHUNK_SIZE_20MS: 640,  // bytes for 20ms at 16kHz 16-bit (320 samples * 2 bytes)
  SAMPLES_PER_40MS: 640, // samples for 40ms at 16kHz
  SAMPLES_PER_20MS: 320, // samples for 20ms at 16kHz
  MAX_QUEUE_SIZE: 100,
  PLAYBACK_BUFFER_SIZE: 5,
  MAX_AUDIO_BUFFER_DURATION: 10000, // 10 seconds in ms
  AUDIO_CONTEXT_SAMPLE_RATE: 16000
}

// Session management constants
export const SESSION_CONFIG = {
  SESSION_TIMEOUT: 300000, // 5 minutes
  IDLE_TIMEOUT: 60000,     // 1 minute
  MAX_MESSAGE_HISTORY: 100
}

// Error handling constants
export const ERROR_CONFIG = {
  NETWORK_ERROR_RETRY_DELAY: 2000,
  PERMISSION_ERROR_RETRY_DELAY: 5000,
  MAX_ERROR_RETRIES: 3,
  ERROR_DISPLAY_DURATION: 5000
}

// UI constants
export const UI_CONFIG = {
  ANIMATION_DURATION: 300,
  DEBOUNCE_DELAY: 300,
  PROGRESS_UPDATE_INTERVAL: 100,
  STATUS_UPDATE_INTERVAL: 1000
}