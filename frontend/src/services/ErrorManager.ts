import { ElMessage } from 'element-plus'
import type { 
  AppError, 
  ErrorHandler, 
  ErrorManagerConfig, 
  RetryConfig 
} from '../types/error'
import { ErrorType, ErrorSeverity } from '../types/error'

/**
 * 错误管理器 - 统一处理应用中的各种错误
 * 提供错误分类、重试机制、用户提示和降级处理
 */
export class ErrorManager {
  private handlers = new Map<ErrorType, ErrorHandler>()
  private retryAttempts = new Map<string, number>()
  private config: ErrorManagerConfig

  constructor(config?: Partial<ErrorManagerConfig>) {
    this.config = {
      showToast: true,
      logErrors: true,
      retryConfig: {
        maxRetries: 3,
        initialDelay: 1000,
        maxDelay: 10000,
        backoffFactor: 2
      },
      ...config
    }

    this.initializeDefaultHandlers()
  }

  /**
   * 注册错误处理器
   */
  registerHandler(type: ErrorType, handler: ErrorHandler): void {
    this.handlers.set(type, handler)
  }

  /**
   * 处理错误
   */
  async handleError(error: AppError): Promise<void> {
    if (this.config.logErrors) {
      console.error(`[ErrorManager] ${error.type}:`, error)
    }

    const handler = this.handlers.get(error.type)
    if (!handler) {
      this.showGenericError(error)
      return
    }

    // 显示用户提示
    if (this.config.showToast) {
      this.showErrorMessage(handler, error)
    }

    // 尝试重试或执行降级处理
    if (handler.retry && this.shouldRetry(error.type)) {
      await this.executeWithRetry(error.type, handler.retry)
    } else if (handler.fallback) {
      handler.fallback()
    }
  }

  /**
   * 创建错误对象
   */
  createError(
    type: ErrorType,
    message: string,
    severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    details?: Record<string, any>
  ): AppError {
    return {
      type,
      message,
      severity,
      timestamp: Date.now(),
      details,
      stack: new Error().stack
    }
  }

  /**
   * 重置重试计数
   */
  resetRetryCount(errorType: ErrorType): void {
    this.retryAttempts.delete(errorType)
  }

  /**
   * 获取重试次数
   */
  getRetryCount(errorType: ErrorType): number {
    return this.retryAttempts.get(errorType) || 0
  }

  /**
   * 初始化默认错误处理器
   */
  private initializeDefaultHandlers(): void {
    // 网络错误处理
    this.registerHandler(ErrorType.NETWORK_ERROR, {
      type: ErrorType.NETWORK_ERROR,
      message: '网络连接失败，请检查网络设置',
      severity: ErrorSeverity.HIGH,
      userGuidance: '请检查网络连接并重试'
    })

    // 麦克风权限错误处理
    this.registerHandler(ErrorType.PERMISSION_DENIED, {
      type: ErrorType.PERMISSION_DENIED,
      message: '需要麦克风权限才能使用语音功能',
      severity: ErrorSeverity.HIGH,
      userGuidance: '请在浏览器设置中允许麦克风权限，然后刷新页面重试'
    })

    // WebSocket连接错误
    this.registerHandler(ErrorType.WEBSOCKET_ERROR, {
      type: ErrorType.WEBSOCKET_ERROR,
      message: 'WebSocket连接失败',
      severity: ErrorSeverity.HIGH,
      userGuidance: '连接服务器失败，请稍后重试'
    })

    // SSE连接错误
    this.registerHandler(ErrorType.SSE_ERROR, {
      type: ErrorType.SSE_ERROR,
      message: '文本流连接失败',
      severity: ErrorSeverity.MEDIUM,
      userGuidance: '文本接收出现问题，正在尝试重新连接'
    })

    // TTS超时错误
    this.registerHandler(ErrorType.TTS_TIMEOUT, {
      type: ErrorType.TTS_TIMEOUT,
      message: '语音合成超时',
      severity: ErrorSeverity.MEDIUM,
      userGuidance: '语音生成较慢，将显示纯文本回复',
      fallback: () => {
        console.log('[ErrorManager] TTS timeout, falling back to text-only mode')
      }
    })

    // 音频上下文错误
    this.registerHandler(ErrorType.AUDIO_CONTEXT_ERROR, {
      type: ErrorType.AUDIO_CONTEXT_ERROR,
      message: '音频系统初始化失败',
      severity: ErrorSeverity.HIGH,
      userGuidance: '音频功能不可用，请刷新页面重试'
    })

    // 会话超时错误
    this.registerHandler(ErrorType.SESSION_TIMEOUT, {
      type: ErrorType.SESSION_TIMEOUT,
      message: '会话已超时',
      severity: ErrorSeverity.MEDIUM,
      userGuidance: '会话已过期，请重新开始对话'
    })

    // 连接丢失错误
    this.registerHandler(ErrorType.CONNECTION_LOST, {
      type: ErrorType.CONNECTION_LOST,
      message: '连接已断开',
      severity: ErrorSeverity.HIGH,
      userGuidance: '连接中断，正在尝试重新连接'
    })
  }

  /**
   * 显示错误消息
   */
  private showErrorMessage(handler: ErrorHandler, error: AppError): void {
    const messageType = this.getMessageType(handler.severity)
    
    ElMessage({
      message: handler.userGuidance || handler.message,
      type: messageType,
      duration: this.getMessageDuration(handler.severity),
      showClose: true,
      grouping: true
    })
  }

  /**
   * 显示通用错误
   */
  private showGenericError(error: AppError): void {
    if (this.config.showToast) {
      ElMessage({
        message: `发生未知错误: ${error.message}`,
        type: 'error',
        duration: 5000,
        showClose: true
      })
    }
  }

  /**
   * 获取消息类型
   */
  private getMessageType(severity: ErrorSeverity): 'success' | 'warning' | 'info' | 'error' {
    switch (severity) {
      case ErrorSeverity.LOW:
        return 'info'
      case ErrorSeverity.MEDIUM:
        return 'warning'
      case ErrorSeverity.HIGH:
      case ErrorSeverity.CRITICAL:
        return 'error'
      default:
        return 'warning'
    }
  }

  /**
   * 获取消息显示时长
   */
  private getMessageDuration(severity: ErrorSeverity): number {
    switch (severity) {
      case ErrorSeverity.LOW:
        return 3000
      case ErrorSeverity.MEDIUM:
        return 5000
      case ErrorSeverity.HIGH:
        return 8000
      case ErrorSeverity.CRITICAL:
        return 0 // 不自动关闭
      default:
        return 5000
    }
  }

  /**
   * 判断是否应该重试
   */
  private shouldRetry(errorType: ErrorType): boolean {
    const retryCount = this.retryAttempts.get(errorType) || 0
    return retryCount < this.config.retryConfig.maxRetries
  }

  /**
   * 执行重试
   */
  private async executeWithRetry(errorType: ErrorType, retryFn: () => Promise<void>): Promise<void> {
    const retryCount = this.retryAttempts.get(errorType) || 0
    this.retryAttempts.set(errorType, retryCount + 1)

    const delay = Math.min(
      this.config.retryConfig.initialDelay * Math.pow(this.config.retryConfig.backoffFactor, retryCount),
      this.config.retryConfig.maxDelay
    )

    setTimeout(async () => {
      try {
        await retryFn()
        // 重试成功，重置计数
        this.retryAttempts.delete(errorType)
      } catch (error) {
        console.error(`[ErrorManager] Retry failed for ${errorType}:`, error)
        // 重试失败，如果还有重试次数则继续，否则执行降级处理
        if (this.shouldRetry(errorType)) {
          await this.executeWithRetry(errorType, retryFn)
        }
      }
    }, delay)
  }
}

// 创建全局错误管理器实例
export const errorManager = new ErrorManager()