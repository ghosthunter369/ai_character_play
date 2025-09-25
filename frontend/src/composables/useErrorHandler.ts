import { ref, computed } from 'vue'
import { errorManager } from '../services/ErrorManager'
import type { AppError, ErrorHandler } from '../types/error'
import { ErrorType, ErrorSeverity } from '../types/error'

/**
 * 错误处理 Composable
 * 提供统一的错误处理接口和状态管理
 */
export function useErrorHandler() {
  const currentError = ref<AppError | null>(null)
  const isRetrying = ref(false)
  const retryCount = ref(0)

  // 计算属性
  const hasError = computed(() => currentError.value !== null)
  const canRetry = computed(() => {
    if (!currentError.value) return false
    return retryCount.value < 3 && isRetryableError(currentError.value.type)
  })

  /**
   * 处理错误
   */
  const handleError = async (
    type: ErrorType,
    message: string,
    severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    details?: Record<string, any>
  ): Promise<void> => {
    const error = errorManager.createError(type, message, severity, details)
    currentError.value = error
    retryCount.value = errorManager.getRetryCount(type)
    
    await errorManager.handleError(error)
  }

  /**
   * 处理网络错误
   */
  const handleNetworkError = async (
    message: string = '网络连接失败',
    retryFn?: () => Promise<void>
  ): Promise<void> => {
    if (retryFn) {
      errorManager.registerHandler(ErrorType.NETWORK_ERROR, {
        type: ErrorType.NETWORK_ERROR,
        message,
        severity: ErrorSeverity.HIGH,
        retry: retryFn,
        userGuidance: '网络连接失败，正在尝试重新连接...'
      })
    }
    
    await handleError(ErrorType.NETWORK_ERROR, message, ErrorSeverity.HIGH)
  }

  /**
   * 处理麦克风权限错误
   */
  const handleMicrophonePermissionError = async (): Promise<void> => {
    await handleError(
      ErrorType.PERMISSION_DENIED,
      '麦克风权限被拒绝',
      ErrorSeverity.HIGH,
      {
        guidance: [
          '1. 点击地址栏左侧的锁图标',
          '2. 选择"允许"麦克风权限',
          '3. 刷新页面重试'
        ]
      }
    )
  }

  /**
   * 处理WebSocket连接错误
   */
  const handleWebSocketError = async (
    wsType: 'audio' | 'tts',
    reconnectFn?: () => Promise<void>
  ): Promise<void> => {
    if (reconnectFn) {
      errorManager.registerHandler(ErrorType.WEBSOCKET_ERROR, {
        type: ErrorType.WEBSOCKET_ERROR,
        message: `${wsType === 'audio' ? '音频' : 'TTS'}WebSocket连接失败`,
        severity: ErrorSeverity.HIGH,
        retry: reconnectFn,
        userGuidance: '连接服务器失败，正在尝试重新连接...'
      })
    }

    await handleError(
      ErrorType.WEBSOCKET_ERROR,
      `${wsType === 'audio' ? '音频' : 'TTS'}WebSocket连接失败`,
      ErrorSeverity.HIGH,
      { wsType }
    )
  }

  /**
   * 处理SSE连接错误
   */
  const handleSSEError = async (reconnectFn?: () => Promise<void>): Promise<void> => {
    if (reconnectFn) {
      errorManager.registerHandler(ErrorType.SSE_ERROR, {
        type: ErrorType.SSE_ERROR,
        message: 'SSE连接失败',
        severity: ErrorSeverity.MEDIUM,
        retry: reconnectFn,
        userGuidance: '文本流连接失败，正在尝试重新连接...'
      })
    }

    await handleError(
      ErrorType.SSE_ERROR,
      'SSE连接失败',
      ErrorSeverity.MEDIUM
    )
  }

  /**
   * 处理TTS超时错误
   */
  const handleTTSTimeout = async (fallbackToText?: () => void): Promise<void> => {
    if (fallbackToText) {
      errorManager.registerHandler(ErrorType.TTS_TIMEOUT, {
        type: ErrorType.TTS_TIMEOUT,
        message: 'TTS服务响应超时',
        severity: ErrorSeverity.MEDIUM,
        fallback: fallbackToText,
        userGuidance: '语音生成较慢，已切换到纯文本模式'
      })
    }

    await handleError(
      ErrorType.TTS_TIMEOUT,
      'TTS服务响应超时',
      ErrorSeverity.MEDIUM,
      { fallbackMode: 'text-only' }
    )
  }

  /**
   * 处理音频上下文错误
   */
  const handleAudioContextError = async (error: Error): Promise<void> => {
    await handleError(
      ErrorType.AUDIO_CONTEXT_ERROR,
      '音频系统初始化失败',
      ErrorSeverity.HIGH,
      {
        originalError: error.message,
        guidance: [
          '1. 确保浏览器支持Web Audio API',
          '2. 检查音频设备是否正常工作',
          '3. 尝试刷新页面重新初始化'
        ]
      }
    )
  }

  /**
   * 处理会话超时错误
   */
  const handleSessionTimeout = async (restartSessionFn?: () => Promise<void>): Promise<void> => {
    if (restartSessionFn) {
      errorManager.registerHandler(ErrorType.SESSION_TIMEOUT, {
        type: ErrorType.SESSION_TIMEOUT,
        message: '会话已超时',
        severity: ErrorSeverity.MEDIUM,
        retry: restartSessionFn,
        userGuidance: '会话已过期，正在重新建立连接...'
      })
    }

    await handleError(
      ErrorType.SESSION_TIMEOUT,
      '会话已超时',
      ErrorSeverity.MEDIUM
    )
  }

  /**
   * 手动重试
   */
  const retry = async (): Promise<void> => {
    if (!currentError.value || !canRetry.value) return

    isRetrying.value = true
    try {
      const handler = errorManager['handlers'].get(currentError.value.type)
      if (handler?.retry) {
        await handler.retry()
        clearError()
      }
    } catch (error) {
      console.error('Manual retry failed:', error)
    } finally {
      isRetrying.value = false
    }
  }

  /**
   * 清除错误状态
   */
  const clearError = (): void => {
    if (currentError.value) {
      errorManager.resetRetryCount(currentError.value.type)
    }
    currentError.value = null
    retryCount.value = 0
    isRetrying.value = false
  }

  /**
   * 判断错误是否可重试
   */
  const isRetryableError = (errorType: ErrorType): boolean => {
    const retryableErrors = [
      ErrorType.NETWORK_ERROR,
      ErrorType.WEBSOCKET_ERROR,
      ErrorType.SSE_ERROR,
      ErrorType.CONNECTION_LOST,
      ErrorType.SESSION_TIMEOUT
    ]
    return retryableErrors.includes(errorType)
  }

  /**
   * 获取错误指导信息
   */
  const getErrorGuidance = (errorType: ErrorType): string[] => {
    const guidanceMap: Record<ErrorType, string[]> = {
      [ErrorType.PERMISSION_DENIED]: [
        '点击地址栏左侧的锁图标',
        '选择"允许"麦克风权限',
        '刷新页面重试'
      ],
      [ErrorType.NETWORK_ERROR]: [
        '检查网络连接是否正常',
        '尝试刷新页面',
        '如问题持续，请联系技术支持'
      ],
      [ErrorType.AUDIO_CONTEXT_ERROR]: [
        '确保浏览器支持Web Audio API',
        '检查音频设备是否正常',
        '尝试使用其他浏览器'
      ],
      [ErrorType.WEBSOCKET_ERROR]: [
        '检查网络连接',
        '确认服务器状态正常',
        '稍后重试'
      ],
      [ErrorType.SSE_ERROR]: [
        '检查网络连接',
        '刷新页面重试',
        '如问题持续，请联系技术支持'
      ],
      [ErrorType.TTS_TIMEOUT]: [
        '网络较慢，已切换到文本模式',
        '可以继续使用文字对话',
        '网络恢复后语音功能将自动恢复'
      ],
      [ErrorType.SESSION_TIMEOUT]: [
        '会话已过期',
        '点击重新开始按钮',
        '重新建立连接'
      ],
      [ErrorType.CONNECTION_LOST]: [
        '连接已断开',
        '正在尝试重新连接',
        '请稍等片刻'
      ],
      [ErrorType.MICROPHONE_ERROR]: [
        '检查麦克风设备连接',
        '确认麦克风权限已开启',
        '尝试重新插拔麦克风'
      ],
      [ErrorType.AUDIO_PLAYBACK_ERROR]: [
        '检查音频输出设备',
        '调整系统音量设置',
        '尝试刷新页面'
      ]
    }

    return guidanceMap[errorType] || ['发生未知错误，请刷新页面重试']
  }

  return {
    // 状态
    currentError: readonly(currentError),
    hasError,
    canRetry,
    isRetrying: readonly(isRetrying),
    retryCount: readonly(retryCount),

    // 方法
    handleError,
    handleNetworkError,
    handleMicrophonePermissionError,
    handleWebSocketError,
    handleSSEError,
    handleTTSTimeout,
    handleAudioContextError,
    handleSessionTimeout,
    retry,
    clearError,
    getErrorGuidance
  }
}