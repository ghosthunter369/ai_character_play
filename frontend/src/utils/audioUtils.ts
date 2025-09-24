// Audio processing utility functions

import type { AudioChunk, AudioConfig } from '@/types/audio'
import { AUDIO_CONFIG } from './constants'

/**
 * Convert Float32Array to Int16Array for audio processing (Requirement 6.6)
 * Clamps values to [-1, 1] range and converts to 16-bit signed integers
 * @param float32 - Input Float32Array audio data
 * @returns Int16Array converted audio data
 */
export function convertFloat32ToInt16(float32: Float32Array): Int16Array {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    // Clamp to [-1, 1] range to prevent overflow
    const s = Math.max(-1, Math.min(1, float32[i]))
    // Convert to 16-bit signed integer range
    int16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
  }
  return int16
}

/**
 * Convert Int16Array to Float32Array for audio processing (Requirement 6.6)
 * Normalizes 16-bit signed integers to [-1, 1] range
 * @param int16 - Input Int16Array audio data
 * @returns Float32Array converted audio data
 */
export function convertInt16ToFloat32(int16: Int16Array): Float32Array {
  const float32 = new Float32Array(int16.length)
  for (let i = 0; i < int16.length; i++) {
    // Normalize to [-1, 1] range
    float32[i] = int16[i] / 0x7FFF
  }
  return float32
}

/**
 * Generate a unique ID for audio chunks or sessions
 * @returns Unique string ID
 */
export function generateUniqueId(): string {
  return `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

/**
 * AudioChunkProcessor class for handling audio data chunking and processing
 * Manages sequence numbers, timestamps, and audio format conversions (Requirements 6.1, 6.3, 6.4)
 */
export class AudioChunkProcessor {
  private seq: number = 0
  private config: AudioConfig
  private startTime: number = 0

  constructor(config: AudioConfig = AUDIO_CONFIG) {
    this.config = config
    this.startTime = Date.now()
  }

  /**
   * Create an audio chunk with sequence number and timestamp (Requirement 6.3)
   * @param pcmData - PCM audio data as Int16Array
   * @returns AudioChunk with metadata
   */
  createChunk(pcmData: Int16Array): AudioChunk {
    const chunk: AudioChunk = {
      seq: this.seq++,
      timestamp: Date.now(),
      data: pcmData.buffer.slice(0) // Create a copy of the buffer
    }
    return chunk
  }

  /**
   * Create audio chunk from Float32Array data
   * @param float32Data - Float32 audio data
   * @returns AudioChunk with converted Int16 data
   */
  createChunkFromFloat32(float32Data: Float32Array): AudioChunk {
    const int16Data = convertFloat32ToInt16(float32Data)
    return this.createChunk(int16Data)
  }

  /**
   * Process large audio buffer into multiple chunks (Requirement 6.2)
   * @param audioData - Large audio buffer as Float32Array
   * @returns Array of AudioChunk objects
   */
  processLargeAudio(audioData: Float32Array): AudioChunk[] {
    const chunks: AudioChunk[] = []
    const frameSize = this.config.frameSize
    
    for (let i = 0; i < audioData.length; i += frameSize) {
      const end = Math.min(i + frameSize, audioData.length)
      const frameData = audioData.slice(i, end)
      
      // Pad with zeros if frame is smaller than expected
      if (frameData.length < frameSize) {
        const paddedFrame = new Float32Array(frameSize)
        paddedFrame.set(frameData)
        chunks.push(this.createChunkFromFloat32(paddedFrame))
      } else {
        chunks.push(this.createChunkFromFloat32(frameData))
      }
    }
    
    return chunks
  }

  /**
   * Validate audio chunk data integrity
   * @param chunk - AudioChunk to validate
   * @returns boolean indicating if chunk is valid
   */
  validateChunk(chunk: AudioChunk): boolean {
    if (!chunk || typeof chunk.seq !== 'number' || typeof chunk.timestamp !== 'number') {
      return false
    }
    
    if (!chunk.data || !(chunk.data instanceof ArrayBuffer)) {
      return false
    }
    
    // Check if data size matches expected frame size
    const expectedBytes = this.config.frameSize * (this.config.bitDepth / 8)
    return chunk.data.byteLength === expectedBytes
  }

  /**
   * Convert AudioChunk back to Int16Array
   * @param chunk - AudioChunk to convert
   * @returns Int16Array audio data
   */
  chunkToInt16Array(chunk: AudioChunk): Int16Array {
    return new Int16Array(chunk.data)
  }

  /**
   * Convert AudioChunk to Float32Array
   * @param chunk - AudioChunk to convert
   * @returns Float32Array audio data
   */
  chunkToFloat32Array(chunk: AudioChunk): Float32Array {
    const int16Data = this.chunkToInt16Array(chunk)
    return convertInt16ToFloat32(int16Data)
  }

  /**
   * Reset sequence counter and start time
   */
  reset(): void {
    this.seq = 0
    this.startTime = Date.now()
  }

  /**
   * Get current sequence number
   * @returns Current sequence number
   */
  getCurrentSeq(): number {
    return this.seq
  }

  /**
   * Get elapsed time since processor creation
   * @returns Elapsed time in milliseconds
   */
  getElapsedTime(): number {
    return Date.now() - this.startTime
  }

  /**
   * Calculate expected chunk duration in milliseconds
   * @returns Duration in milliseconds
   */
  getChunkDuration(): number {
    return (this.config.frameSize / this.config.sampleRate) * 1000
  }

  /**
   * Get audio configuration
   * @returns Current AudioConfig
   */
  getConfig(): AudioConfig {
    return { ...this.config }
  }

  /**
   * Update audio configuration
   * @param newConfig - New AudioConfig to use
   */
  updateConfig(newConfig: AudioConfig): void {
    this.config = { ...newConfig }
  }
}