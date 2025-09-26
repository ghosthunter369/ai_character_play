interface VADResult {
  shouldSend: boolean
  shouldStop: boolean
  volume: number
  status: 'silence' | 'speech' | 'waiting'
  info: string
}

interface VoiceChatMessage {
  type: 'user' | 'ai'
  content: string
  timestamp: number
  audioUrl?: string
}

class VoiceChatService {
  private websocket: WebSocket | null = null
  private audioContext: AudioContext | null = null
  private mediaStream: MediaStream | null = null
  private processor: ScriptProcessorNode | null = null
  private source: MediaStreamAudioSourceNode | null = null
  private isRecording = false
  private isConnected = false
  private audioBuffer = new Float32Array(0)
  private playbackQueue: ArrayBuffer[] = []
  private isPlaying = false
  
  // VAD相关状态
  private vadState: 'silence' | 'speech' | 'waiting' = 'silence'
  private speechStartTime: number | null = null
  private silenceStartTime: number | null = null
  private currentVolume = 0
  private volumeHistory: number[] = []
  private hasDetectedSpeech = false

  // 智能断句状态
  private currentSentence = ''
  private lastPartialResult = ''
  private sentenceEndTimer: NodeJS.Timeout | null = null

  // 统计信息
  private frameCount = 0
  private silentFrameCount = 0
  private speechFrameCount = 0

  // 流式AI回复相关
  private currentStreamingMessage: {
    id: string
    content: string
    timestamp: number
    isComplete: boolean
  } | null = null
  private streamingTimer: NodeJS.Timeout | null = null

  // 回调函数
  public onMessage?: (message: VoiceChatMessage) => void
  public onStreamingMessage?: (message: VoiceChatMessage & { isStreaming: boolean }) => void
  public onConnectionChange?: (connected: boolean) => void
  public onVolumeChange?: (volume: number, status: string, info: string) => void
  public onSilenceDetected?: () => void
  public onAsrPartial?: (text: string) => void  // 新增：处理partial ASR结果
  public onAsrFinal?: (text: string) => void    // 新增：处理final ASR结果

  async connect(appId: string | number): Promise<void> {
    if (this.isConnected) return

    try {
      // 建立WebSocket连接 - 使用正确的端口和路径
      const wsUrl = `ws://localhost:8123/api/ws/audio?appId=${appId}`
      console.log('🔗 正在连接WebSocket:', wsUrl)
      this.websocket = new WebSocket(wsUrl)

      this.websocket.onopen = () => {
        console.log('✅ WebSocket连接已建立')
        this.isConnected = true
        this.onConnectionChange?.(true)
      }

      this.websocket.onmessage = (event) => {
        this.handleWebSocketMessage(event)
      }

      this.websocket.onclose = () => {
        console.log('❌ WebSocket连接已关闭')
        this.isConnected = false
        this.onConnectionChange?.(false)
      }

      this.websocket.onerror = (error) => {
        console.error('❌ WebSocket错误:', error)
        this.isConnected = false
        this.onConnectionChange?.(false)
      }

      // 等待连接建立
      await new Promise((resolve, reject) => {
        const timeout = setTimeout(() => reject(new Error('连接超时')), 5000)
        this.websocket!.onopen = () => {
          clearTimeout(timeout)
          this.isConnected = true
          this.onConnectionChange?.(true)
          console.log('✅ WebSocket连接成功建立')
          resolve(void 0)
        }
        this.websocket!.onerror = () => {
          clearTimeout(timeout)
          reject(new Error('连接失败'))
        }
      })

    } catch (error) {
      console.error('❌ 连接失败:', error)
      throw error
    }
  }

  async disconnect(): Promise<void> {
    console.log('🔌 开始断开连接...')
    
    if (this.isRecording) {
      await this.stopRecording()
    }

    if (this.websocket) {
      this.websocket.close()
      this.websocket = null
    }

    this.isConnected = false
    this.onConnectionChange?.(false)
    console.log('✅ 连接已断开')
  }

  async startRecording(): Promise<void> {
    if (this.isRecording) return

    try {
      console.log('🎤 开始启动录音...')
      
      // 获取麦克风权限
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: 16000,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        }
      })

      // 创建音频上下文
      this.audioContext = new AudioContext({ sampleRate: 16000 })
      this.source = this.audioContext.createMediaStreamSource(this.mediaStream)
      
      // 创建音频处理器
      const bufferSize = 4096
      const frameSize = 1024 // 每帧1024个样本，约64ms
      this.processor = this.audioContext.createScriptProcessor(bufferSize, 1, 1)

      // 重置VAD状态和统计
      this.vadState = 'silence'
      this.speechStartTime = null
      this.silenceStartTime = null
      this.hasDetectedSpeech = false
      this.audioBuffer = new Float32Array(0)
      this.frameCount = 0
      this.silentFrameCount = 0
      this.speechFrameCount = 0

      console.log('🎵 音频处理器配置完成，开始处理音频流...')

      this.processor.onaudioprocess = (event) => {
        if (!this.isRecording) return

        const inputBuffer = event.inputBuffer
        const inputData = inputBuffer.getChannelData(0)

        // 智能语音活动检测
        const vadResult = this.processVAD(inputData)

        // 更新音量指示器
        this.onVolumeChange?.(vadResult.volume, vadResult.status, vadResult.info)

        // 累积音频数据 - 只在有语音或刚开始静音时发送
        const shouldSendAudio = vadResult.status === 'speech' || 
                               (vadResult.status === 'silence' && this.hasDetectedSpeech)

        if (shouldSendAudio) {
          const newBuffer = new Float32Array(this.audioBuffer.length + inputData.length)
          newBuffer.set(this.audioBuffer)
          newBuffer.set(inputData, this.audioBuffer.length)
          this.audioBuffer = newBuffer

          // 按固定帧大小发送
          while (this.audioBuffer.length >= frameSize) {
            const frameData = this.audioBuffer.slice(0, frameSize)
            this.audioBuffer = this.audioBuffer.slice(frameSize)

            // 优化的16位PCM转换
            const pcmData = new Int16Array(frameSize)
            for (let i = 0; i < frameSize; i++) {
              const sample = Math.max(-1, Math.min(1, frameData[i]))
              pcmData[i] = sample < 0 ? Math.round(sample * 32768) : Math.round(sample * 32767)
            }

            const frameType = vadResult.status === 'silence' ? '静音帧' : '语音帧'
            this.frameCount++
            
            if (vadResult.status === 'silence') {
              this.silentFrameCount++
            } else {
              this.speechFrameCount++
            }
            
            if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
              this.websocket.send(pcmData.buffer)
              console.log(`🎵 发送${frameType}: 音量:${vadResult.volume.toFixed(4)}, 状态:${vadResult.status}`)
            }
          }
        }

        // 检查是否需要发送段落结束信号
        if (vadResult.shouldStop && this.hasDetectedSpeech) {
          this.sendSegmentEnd()
        }
      }

      // 连接音频节点
      this.source.connect(this.processor)
      this.processor.connect(this.audioContext.destination)

      this.isRecording = true
      console.log('✅ 录音已启动，开始持续推流')

    } catch (error) {
      console.error('❌ 启动录音失败:', error)
      throw error
    }
  }

  async stopRecording(): Promise<void> {
    if (!this.isRecording) return

    console.log('🛑 停止录音...')
    this.isRecording = false

    // 发送剩余的音频数据
    if (this.audioBuffer.length > 0 && this.websocket && this.websocket.readyState === WebSocket.OPEN) {
      const pcmData = new Int16Array(this.audioBuffer.length)
      for (let i = 0; i < this.audioBuffer.length; i++) {
        const sample = Math.max(-1, Math.min(1, this.audioBuffer[i]))
        pcmData[i] = sample < 0 ? Math.round(sample * 32768) : Math.round(sample * 32767)
      }
      this.websocket.send(pcmData.buffer)
      console.log('📤 发送剩余音频数据:', pcmData.buffer.byteLength, '字节')
    }

    // 发送结束标记
    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
      this.websocket.send(JSON.stringify({ type: 'end' }))
      console.log('📤 发送录音结束标记')
    }

    // 清理音频资源
    if (this.processor) {
      this.processor.disconnect()
      this.processor = null
    }

    if (this.source) {
      this.source.disconnect()
      this.source = null
    }

    if (this.audioContext) {
      await this.audioContext.close()
      this.audioContext = null
    }

    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(track => track.stop())
      this.mediaStream = null
    }

    this.audioBuffer = new Float32Array(0)
    
    // 输出最终统计
    console.log('📊 录音会话统计:')
    console.log(`   总帧数: ${this.frameCount}`)
    console.log(`   静音帧: ${this.silentFrameCount} (${((this.silentFrameCount/this.frameCount)*100).toFixed(1)}%)`)
    console.log(`   语音帧: ${this.speechFrameCount} (${((this.speechFrameCount/this.frameCount)*100).toFixed(1)}%)`)
    console.log('✅ 录音已停止')
  }

  private processVAD(audioData: Float32Array): VADResult {
    // 计算音量
    let sum = 0
    for (let i = 0; i < audioData.length; i++) {
      sum += audioData[i] * audioData[i]
    }
    const rms = Math.sqrt(sum / audioData.length)
    this.currentVolume = rms

    // 更新音量历史
    this.volumeHistory.push(rms)
    if (this.volumeHistory.length > 10) {
      this.volumeHistory.shift()
    }

    // 动态阈值
    const avgVolume = this.volumeHistory.reduce((a, b) => a + b, 0) / this.volumeHistory.length
    const speechThreshold = Math.max(0.01, avgVolume * 2)
    const silenceThreshold = speechThreshold * 0.3

    // 配置参数
    const maxSilenceDuration = 1500 // 1.5秒静音后发送段落结束
    const minSpeechDuration = 500   // 最少500ms语音才算有效
    const segmentEndDelay = 800     // 段落结束延迟，等待可能的继续说话

    const currentTime = Date.now()
    let shouldSend = true
    let shouldStop = false
    let statusInfo = ''

    // 状态机处理 - 智能断句检测
    switch (this.vadState) {
      case 'silence':
        if (this.currentVolume > speechThreshold) {
          this.vadState = 'speech'
          this.speechStartTime = currentTime
          this.silenceStartTime = null
          this.hasDetectedSpeech = true

          statusInfo = '检测到语音开始'
          console.log('🎤 VAD状态变更: silence -> speech')
          
          // 清除可能存在的段落结束定时器
          if (this.sentenceEndTimer) {
            clearTimeout(this.sentenceEndTimer)
            this.sentenceEndTimer = null
          }
        } else {
          statusInfo = this.hasDetectedSpeech ? '等待下一句话...' : '等待语音输入'
          shouldSend = false // 纯静音时不发送音频
        }
        break

      case 'speech':
        if (this.currentVolume > silenceThreshold) {
          const speechDuration = currentTime - (this.speechStartTime || 0)
          statusInfo = `语音进行中 (${Math.round(speechDuration / 100) / 10}s)`
          
          // 清除段落结束定时器（继续说话）
          if (this.sentenceEndTimer) {
            clearTimeout(this.sentenceEndTimer)
            this.sentenceEndTimer = null
          }
        } else {
          if (!this.silenceStartTime) {
            this.silenceStartTime = currentTime
            console.log('🔇 检测到语音转静音，开始静音计时')
          }

          const speechDuration = currentTime - (this.speechStartTime || 0)
          const silenceDuration = currentTime - this.silenceStartTime

          if (speechDuration >= minSpeechDuration && silenceDuration >= maxSilenceDuration) {
            // 设置段落结束定时器，延迟发送以防用户继续说话
            if (!this.sentenceEndTimer) {
              this.sentenceEndTimer = setTimeout(() => {
                console.log('⏰ 段落结束定时器触发，发送段落结束信号')
                shouldStop = true
                this.vadState = 'waiting' // 等待AI回复
                this.speechStartTime = null
                this.silenceStartTime = null
              }, segmentEndDelay)
            }
            statusInfo = `语音段即将结束 (静音${Math.round(silenceDuration / 100) / 10}s)`
          } else {
            statusInfo = `语音中静音 (${Math.round(silenceDuration / 100) / 10}s)`
          }
        }
        break

      case 'waiting':
        statusInfo = 'AI正在回复中...'
        shouldSend = false // AI回复时不发送音频
        break
    }

    return {
      shouldSend,
      shouldStop,
      volume: this.currentVolume,
      status: this.vadState === 'silence' ? 'silence' : 
              this.vadState === 'speech' ? 'speech' : 'waiting',
      info: statusInfo
    }
  }

  // 发送段落结束信号
  private sendSegmentEnd(): void {
    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
      // 发送段落结束标记
      this.websocket.send(JSON.stringify({ type: 'segment_end' }))
      console.log('📤 发送段落结束信号')
      
      // 重置状态
      this.hasDetectedSpeech = false
      this.currentSentence = ''
      this.lastPartialResult = ''
      
      // 清除定时器
      if (this.sentenceEndTimer) {
        clearTimeout(this.sentenceEndTimer)
        this.sentenceEndTimer = null
      }
    }
  }

  // 重置VAD状态（AI回复完成后调用）
  private resetVADState(): void {
    this.vadState = 'silence'
    this.speechStartTime = null
    this.silenceStartTime = null
    this.hasDetectedSpeech = false
    this.currentSentence = ''
    this.lastPartialResult = ''
    
    if (this.sentenceEndTimer) {
      clearTimeout(this.sentenceEndTimer)
      this.sentenceEndTimer = null
    }
    
    console.log('🔄 VAD状态已重置，准备接收新的语音输入')
  }

  private handleWebSocketMessage(event: MessageEvent): void {
    const timestamp = new Date().toLocaleTimeString()
    
    // 检查消息类型
    if (event.data instanceof ArrayBuffer) {
      // 处理二进制音频数据（PCM格式）
      console.log('🔊 收到ArrayBuffer音频数据:', {
        timestamp: timestamp,
        messageType: 'ArrayBuffer音频',
        byteLength: event.data.byteLength
      })
      this.playbackQueue.push(event.data)
      this.processPlaybackQueue()
      return
    }

    // 处理Blob类型消息
    if (event.data instanceof Blob) {
      console.log('🔊 收到Blob音频数据:', {
        timestamp: timestamp,
        messageType: 'Blob音频',
        size: event.data.size,
        type: event.data.type
      })
      // 将Blob转换为ArrayBuffer
      event.data.arrayBuffer().then(arrayBuffer => {
        this.playbackQueue.push(arrayBuffer)
        this.processPlaybackQueue()
      }).catch(error => {
        console.error('❌ Blob→ArrayBuffer转换失败:', error)
      })
      return
    }

    // 确保消息是字符串类型
    if (typeof event.data !== 'string') {
      console.warn('⚠️ 收到非字符串消息:', typeof event.data, event.data)
      return
    }

    const message = event.data
    
    // 添加详细的消息调试信息
    console.log('🔍 收到WebSocket消息:', {
      timestamp: timestamp,
      messageType: message.startsWith('REPLY:') ? 'AI回复' :
        message.startsWith('AUDIO:') ? 'TTS音频' : 'ASR识别',
      messageLength: message.length,
      messagePreview: message.substring(0, 50) + (message.length > 50 ? '...' : '')
    })

    if (message.startsWith('REPLY:')) {
      // AI回复文本 - 实现流式拼接显示
      const replyText = message.substring(6)
      console.log('📝 处理AI回复片段:', replyText)
      
      // 检查是否是新的回复开始
      if (!this.currentStreamingMessage) {
        // 开始新的流式回复
        this.currentStreamingMessage = {
          id: `ai_${Date.now()}`,
          content: replyText,
          timestamp: Date.now(),
          isComplete: false
        }
        
        // 立即显示第一个片段
        this.onStreamingMessage?.({
          type: 'ai',
          content: this.currentStreamingMessage.content,
          timestamp: this.currentStreamingMessage.timestamp,
          isStreaming: true
        })
      } else {
        // 拼接到现有回复
        this.currentStreamingMessage.content += replyText
        
        // 更新流式显示
        this.onStreamingMessage?.({
          type: 'ai',
          content: this.currentStreamingMessage.content,
          timestamp: this.currentStreamingMessage.timestamp,
          isStreaming: true
        })
      }
      
      // 重置完成定时器
      if (this.streamingTimer) {
        clearTimeout(this.streamingTimer)
      }
      
      // 设置完成定时器（500ms内没有新片段则认为完成）
      this.streamingTimer = setTimeout(() => {
        if (this.currentStreamingMessage) {
          console.log('🤖 AI回复完成:', this.currentStreamingMessage.content)
          
          // 发送最终完整消息
          this.onMessage?.({
            type: 'ai',
            content: this.currentStreamingMessage.content,
            timestamp: this.currentStreamingMessage.timestamp
          })
          
          // 标记流式显示完成
          this.onStreamingMessage?.({
            type: 'ai',
            content: this.currentStreamingMessage.content,
            timestamp: this.currentStreamingMessage.timestamp,
            isStreaming: false
          })
          
          // 清理流式状态
          this.currentStreamingMessage = null
          this.streamingTimer = null
          
          // 重置VAD状态，但继续保持录音连接
          this.resetVADState()
          console.log('🤖 AI回复完成，VAD状态已重置，继续录音')
        }
      }, 500)
    } else if (message.startsWith('AUDIO:')) {
      // TTS音频数据 (Base64格式)
      const audioContent = message.substring(6) // 去掉"AUDIO:"前缀
      try {
        const audioData = atob(audioContent)
        const audioArray = new Uint8Array(audioData.length)
        for (let i = 0; i < audioData.length; i++) {
          audioArray[i] = audioData.charCodeAt(i)
        }
        console.log('✅ Base64解码成功')
        this.playbackQueue.push(audioArray.buffer)
        this.processPlaybackQueue()
      } catch (error) {
        console.error('❌ Base64解码失败:', error)
      }
    } else if (message.startsWith('PARTIAL:')) {
      // 处理partial ASR结果
      const partialText = message.substring(8) // 去掉"PARTIAL:"前缀
      console.log('🎤 处理ASR部分结果:', partialText)
      
      // 更新当前句子和上次结果
      this.lastPartialResult = partialText
      this.currentSentence = partialText
      
      // 调用回调显示实时结果
      this.onAsrPartial?.(partialText)
      
    } else if (message.startsWith('FINAL:')) {
      // 处理final ASR结果
      const finalText = message.substring(6) // 去掉"FINAL:"前缀
      console.log('🎤 处理ASR最终结果:', finalText)
      
      // 清空实时显示
      this.onAsrFinal?.(finalText)
      
      // 添加到消息列表（只有非空的最终结果才添加）
      if (finalText.trim()) {
        this.onMessage?.({
          type: 'user',
          content: finalText,
          timestamp: Date.now()
        })
      }
    } else {
      // 尝试解析JSON消息（用于控制消息）
      try {
        const data = JSON.parse(message)
        console.log('📋 JSON控制消息:', data)
        
        if (data.type === 'asr_result') {
          this.onMessage?.({
            type: 'user',
            content: data.text,
            timestamp: Date.now()
          })
        } else if (data.type === 'ai_response') {
          this.onMessage?.({
            type: 'ai',
            content: data.text,
            timestamp: Date.now()
          })
          this.resetVADState()
        }
      } catch (jsonError) {
        // 不是JSON格式，当作普通ASR识别文本处理（兼容旧格式）
        console.log('🎤 处理ASR识别（兼容格式）:', message)
        this.onMessage?.({
          type: 'user',
          content: message,
          timestamp: Date.now()
        })
      }
    }
  }

  private async processPlaybackQueue(): Promise<void> {
    if (this.isPlaying || this.playbackQueue.length === 0) return

    this.isPlaying = true
    console.log('🔊 开始播放TTS音频队列，队列长度:', this.playbackQueue.length)

    try {
      while (this.playbackQueue.length > 0) {
        const audioData = this.playbackQueue.shift()!
        await this.playAudio(audioData)
      }
    } finally {
      this.isPlaying = false
      console.log('🔊 TTS音频播放完成')
    }
  }

  private async playAudio(audioData: ArrayBuffer): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        // 检查音频数据大小
        if (audioData.byteLength === 0) {
          console.warn('⚠️ 收到空音频数据，跳过播放')
          resolve()
          return
        }

        // 检查音频数据格式
        const uint8Array = new Uint8Array(audioData)
        const isWAV = uint8Array[0] === 0x52 && uint8Array[1] === 0x49 && uint8Array[2] === 0x46 && uint8Array[3] === 0x46
        const isMP3 = uint8Array[0] === 0xFF && (uint8Array[1] & 0xE0) === 0xE0
        const isOGG = uint8Array[0] === 0x4F && uint8Array[1] === 0x67 && uint8Array[2] === 0x67 && uint8Array[3] === 0x53

        let processedAudioData = audioData

        // 如果不是标准音频格式，假设是PCM数据并转换为WAV
        if (!isWAV && !isMP3 && !isOGG) {
          console.log('🔧 检测到PCM数据，转换为WAV格式')
          processedAudioData = this.convertPcmToWav(audioData)
        }

        const audioContext = new AudioContext()
        
        audioContext.decodeAudioData(processedAudioData.slice(0))
          .then(audioBuffer => {
            console.log('🔊 播放TTS音频片段:', audioBuffer.duration.toFixed(2), '秒')
            const source = audioContext.createBufferSource()
            source.buffer = audioBuffer
            source.connect(audioContext.destination)
            
            source.onended = () => {
              audioContext.close()
              resolve()
            }
            
            source.start()
          })
          .catch(error => {
            console.error('❌ Web Audio API解码失败:', error)
            audioContext.close()
            // 尝试使用Audio元素作为备选方案
            this.playAudioWithAudioElement(processedAudioData).then(resolve).catch(reject)
          })
      } catch (error) {
        console.error('❌ 音频播放失败:', error)
        reject(error)
      }
    })
  }

  // PCM转WAV格式
  private convertPcmToWav(pcmData: ArrayBuffer): ArrayBuffer {
    const pcmArray = new Int16Array(pcmData)
    const sampleRate = 16000
    const numChannels = 1
    const bitsPerSample = 16
    
    const wavHeaderLength = 44
    const wavBuffer = new ArrayBuffer(wavHeaderLength + pcmData.byteLength)
    const view = new DataView(wavBuffer)
    
    // WAV文件头
    const writeString = (offset: number, string: string) => {
      for (let i = 0; i < string.length; i++) {
        view.setUint8(offset + i, string.charCodeAt(i))
      }
    }
    
    writeString(0, 'RIFF')
    view.setUint32(4, wavBuffer.byteLength - 8, true)
    writeString(8, 'WAVE')
    writeString(12, 'fmt ')
    view.setUint32(16, 16, true) // fmt chunk size
    view.setUint16(20, 1, true) // PCM format
    view.setUint16(22, numChannels, true)
    view.setUint32(24, sampleRate, true)
    view.setUint32(28, sampleRate * numChannels * bitsPerSample / 8, true) // byte rate
    view.setUint16(32, numChannels * bitsPerSample / 8, true) // block align
    view.setUint16(34, bitsPerSample, true)
    writeString(36, 'data')
    view.setUint32(40, pcmData.byteLength, true)
    
    // 复制PCM数据
    const wavArray = new Int16Array(wavBuffer, wavHeaderLength)
    wavArray.set(pcmArray)
    
    console.log('🔧 PCM转WAV完成:', {
      原始大小: pcmData.byteLength,
      WAV大小: wavBuffer.byteLength,
      采样率: sampleRate,
      声道数: numChannels
    })
    
    return wavBuffer
  }

  // 备选音频播放方法
  private async playAudioWithAudioElement(audioData: ArrayBuffer): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        const blob = new Blob([audioData], { type: 'audio/wav' })
        const audioUrl = URL.createObjectURL(blob)
        const audio = new Audio(audioUrl)
        
        audio.onended = () => {
          URL.revokeObjectURL(audioUrl)
          console.log('🔊 Audio元素播放完成')
          resolve()
        }
        
        audio.onerror = (error) => {
          URL.revokeObjectURL(audioUrl)
          console.error('❌ Audio元素播放失败:', error)
          reject(error)
        }
        
        audio.play().catch(error => {
          URL.revokeObjectURL(audioUrl)
          console.error('❌ Audio元素播放启动失败:', error)
          reject(error)
        })
      } catch (error) {
        console.error('❌ 创建Audio元素失败:', error)
        reject(error)
      }
    })
  }

  // 获取连接状态
  getConnectionStatus(): boolean {
    return this.isConnected
  }

  // 获取录音状态
  getRecordingStatus(): boolean {
    return this.isRecording
  }

  // 获取统计信息
  getStats() {
    return {
      frameCount: this.frameCount,
      silentFrameCount: this.silentFrameCount,
      speechFrameCount: this.speechFrameCount,
      vadState: this.vadState,
      currentVolume: this.currentVolume
    }
  }
}

export default new VoiceChatService()