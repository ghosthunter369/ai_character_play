// Audio related type definitions

export interface AudioConfig {
  sampleRate: number
  channels: number
  bitDepth: number
  frameSize: number
}

export interface AudioChunk {
  seq: number
  timestamp: number
  data: ArrayBuffer
}

export interface AudioFormatSpec {
  sampleRate: number
  channels: number
  bitDepth: number
  encoding: 'PCM'
}

export interface AudioBufferData {
  data: Float32Array | Int16Array
  sampleRate: number
  channels: number
  duration: number
}

export interface AudioProcessingOptions {
  enableBackpressure: boolean
  maxQueueSize: number
  dropFramesOnOverflow: boolean
}

export type AudioState = 'idle' | 'capturing' | 'playing' | 'error'
export type AudioFormatType = 'PCM_16' | 'PCM_32' | 'FLOAT32'