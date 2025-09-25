import { ref, computed } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { errorManager } from '../services/ErrorManager'
import { ErrorType, ErrorSeverity } from '../types/error'

/**
 * TTS超时处理器
 * 提供TTS服务超时检测、降级处理和用户反馈
 */
export class TTSTimeoutHandler {
  private timeouts = new Map<string, NodeJS.Timeout>()
  private fallbackMode = ref(false)
  private timeoutCount = ref(0)
  private lastTimeoutTime = ref(0)
  private readonly maxTimeouts = 3
  private readonly timeoutThreshold = 10000 // 10秒超时
  private readonly fallbackDuration = 60000 // 1分钟后重试TTS

  constructor() {
    this.setupFallbackRecovery()
  }

  /**
   * 开始TTS超时监控
   */
  startTimeout(requestId: string, customTimeout?: number): void {
    const timeout = customTimeout || this.timeoutThreshold
    
    const timeoutId = setTimeout(() => {
      this.handleTimeout(requestId)
    }, timeout)

    this.timeouts.set(requestId, timeoutId)
  }

  /**
   * 清除超时监控
   */
  clearTimeout(requestId: string): void {
    const timeoutId = this.timeouts.get(requestId)
    if (timeoutId) {
      clearTimeout(timeoutId)
      this.timeouts.delete(requestId)
    }
  }

  /**
   * 处理TTS超时
   */
  private async handleTimeout(requestId: string): Promise<void> {
    this.timeoutCount.value++
    this.lastTimeoutTime.value = Date.now()

    // 清除超时ID
    this.timeouts.delete(requestId)

    // 检查是否需要进入降级模式
    if (this.timeoutCount.value >= this.maxTimeouts) {
      await this.enterFallbackMode()
    } else {
      await this.showTimeoutWarning(requestId)
    }

    // 创建并处理超时错误
    const error = errorManager.createError(
      ErrorType.TTS_TIMEOUT,
      'TTS服务响应超时',
      ErrorSeverity.MEDIUM,
      {
        requestId,
        timeoutCount: this.timeoutCount.value,
        fallbackMode: this.fallbackMode.value
      }
    )

    await errorManager.handleError(error)
  }

  /**
   * 进入降级模式（纯文本模式）
   */
  private async enterFallbackMode(): Promise<void> {
    this.fallbackMode.value = true

    ElNotification({
      title: 'TTS服务异常',
      message: '语音合成服务响应较慢，已切换到纯文本模式。系统将在稍后自动尝试恢复语音功能。',
      type: 'warning',
      duration: 8000,
      position: 'top-right'
    })

    // 触发降级模式事件
    this.emitFallbackEvent('entered')
  }

  /**
   * 退出降级模式
   */
  private async exitFallbackMode(): Promise<void> {
    this.fallbackMode.value = false
    this.timeoutCount.value = 0

    ElMessage({
      message: '语音功能已恢复',
      type: 'success',
      duration: 3000
    })

    // 触发降级模式事件
    this.emitFallbackEvent('exited')
  }

  /**
   * 显示超时警告
   */
  private async showTimeoutWarning(requestId: string): Promise<void> {
    ElMessage({
      message: `语音生成较慢 (${this.timeoutCount.value}/${this.maxTimeouts})，请稍候...`,
      type: 'warning',
      duration: 4000,
      grouping: true
    })
  }

  /**
   * 设置降级模式自动恢复
   */
  private setupFallbackRecovery(): void {
    setInterval(() => {
      if (this.fallbackMode.value && this.shouldAttemptRecovery()) {
        this.attemptRecovery()
      }
    }, this.fallbackDuration)
  }

  /**
   * 判断是否应该尝试恢复
   */
  private shouldAttemptRecovery(): boolean {
    const timeSinceLastTimeout = Date.now() - this.lastTimeoutTime.value
    return timeSinceLastTimeout >= this.fallbackDuration
  }

  /**
   * 尝试恢复TTS功能
   */
  private async attemptRecovery(): Promise<void> {
    try {
      // 发送测试请求检查TTS服务状态
      const isServiceAvailable = await this.testTTSService()
      
      if (isServiceAvailable) {
        await this.exitFallbackMode()
      } else {
        // 延长恢复尝试间隔
        this.lastTimeoutTime.value = Date.now()
      }
    } catch (error) {
      console.error('TTS recovery attempt failed:', error)
    }
  }

  /**
   * 测试TTS服务可用性
   */
  private async testTTSService(): Promise<boolean> {
    try {
      // 这里应该发送一个简单的TTS测试请求
      // 由于没有具体的API端点，这里模拟测试
      const testPromise = new Promise<boolean>((resolve) => {
        // 模拟网络请求
        setTimeout(() => {
          // 简单的网络连通性检查
          resolve(navigator.onLine)
        }, 1000)
      })

      const result = await Promise.race([
        testPromise,
        new Promise<boolean>((_, reject) => {
          setTimeout(() => reject(new Error('Test timeout')), 3000)
        })
      ])

      return result
    } catch (error) {
      return false
    }
  }

  /**
   * 触发降级模式事件
   */
  private emitFallbackEvent(type: 'entered' | 'exited'): void {
    const event = new CustomEvent('tts-fallback', {
      detail: {
        type,
        fallbackMode: this.fallbackMode.value,
        timeoutCount: this.timeoutCount.value
      }
    })
    window.dispatchEvent(event)
  }

  /**
   * 获取当前状态
   */
  getStatus() {
    return {
      fallbackMode: this.fallbackMode.value,
      timeoutCount: this.timeoutCount.value,
      activeTimeouts: this.timeouts.size,
      lastTimeoutTime: this.lastTimeoutTime.value
    }
  }

  /**
   * 手动切换降级模式
   */
  toggleFallbackMode(enabled: boolean): void {
    if (enabled && !this.fallbackMode.value) {
      this.enterFallbackMode()
    } else if (!enabled && this.fallbackMode.value) {
      this.exitFallbackMode()
    }
  }

  /**
   * 重置超时计数
   */
  resetTimeoutCount(): void {
    this.timeoutCount.value = 0
    this.lastTimeoutTime.value = 0
  }

  /**
   * 清除所有超时监控
   */
  clearAllTimeouts(): void {
    this.timeouts.forEach((timeoutId) => {
      clearTimeout(timeoutId)
    })
    this.timeouts.clear()
  }

  /**
   * 销毁处理器
   */
  destroy(): void {
    this.clearAllTimeouts()
  }
}

/**
 * TTS超时处理 Composable
 */
export function useTTSTimeout() {
  const handler = new TTSTimeoutHandler()

  // 响应式状态
  const status = computed(() => handler.getStatus())
  const isFallbackMode = computed(() => status.value.fallbackMode)
  const timeoutCount = computed(() => status.value.timeoutCount)

  /**
   * 监听TTS请求
   */
  const monitorTTSRequest = (requestId: string, timeout?: number) => {
    handler.startTimeout(requestId, timeout)
    
    return {
      complete: () => handler.clearTimeout(requestId),
      cancel: () => handler.clearTimeout(requestId)
    }
  }

  /**
   * 监听降级模式事件
   */
  const onFallbackChange = (callback: (fallbackMode: boolean) => void) => {
    const handleEvent = (event: CustomEvent) => {
      callback(event.detail.fallbackMode)
    }

    window.addEventListener('tts-fallback', handleEvent as EventListener)
    
    return () => {
      window.removeEventListener('tts-fallback', handleEvent as EventListener)
    }
  }

  /**
   * 手动控制降级模式
   */
  const setFallbackMode = (enabled: boolean) => {
    handler.toggleFallbackMode(enabled)
  }

  /**
   * 重置状态
   */
  const reset = () => {
    handler.resetTimeoutCount()
    handler.clearAllTimeouts()
  }

  return {
    // 状态
    status,
    isFallbackMode,
    timeoutCount,

    // 方法
    monitorTTSRequest,
    onFallbackChange,
    setFallbackMode,
    reset,

    // 生命周期
    destroy: () => handler.destroy()
  }
}

// 导出全局实例
export const ttsTimeoutHandler = new TTSTimeoutHandler()