import { ref, computed } from 'vue'
import type { RetryConfig } from '../types/error'

/**
 * 重试机制 Composable
 * 提供指数退避重试策略和网络恢复检测
 */
export function useRetryMechanism(config?: Partial<RetryConfig>) {
  const defaultConfig: RetryConfig = {
    maxRetries: 3,
    initialDelay: 1000,
    maxDelay: 10000,
    backoffFactor: 2
  }

  const retryConfig = { ...defaultConfig, ...config }
  const currentAttempt = ref(0)
  const isRetrying = ref(false)
  const lastRetryTime = ref(0)

  // 计算属性
  const canRetry = computed(() => currentAttempt.value < retryConfig.maxRetries)
  const nextRetryDelay = computed(() => {
    if (!canRetry.value) return 0
    return Math.min(
      retryConfig.initialDelay * Math.pow(retryConfig.backoffFactor, currentAttempt.value),
      retryConfig.maxDelay
    )
  })

  /**
   * 执行重试操作
   */
  const executeWithRetry = async <T>(
    operation: () => Promise<T>,
    onRetry?: (attempt: number, delay: number) => void
  ): Promise<T> => {
    currentAttempt.value = 0
    isRetrying.value = false

    while (currentAttempt.value <= retryConfig.maxRetries) {
      try {
        const result = await operation()
        reset()
        return result
      } catch (error) {
        currentAttempt.value++
        
        if (currentAttempt.value > retryConfig.maxRetries) {
          reset()
          throw error
        }

        const delay = nextRetryDelay.value
        lastRetryTime.value = Date.now()
        isRetrying.value = true

        if (onRetry) {
          onRetry(currentAttempt.value, delay)
        }

        await sleep(delay)
      }
    }

    throw new Error('Max retries exceeded')
  }

  /**
   * 网络连接重试
   */
  const retryConnection = async (
    connectFn: () => Promise<void>,
    onProgress?: (attempt: number, maxAttempts: number, nextDelay: number) => void
  ): Promise<boolean> => {
    try {
      await executeWithRetry(connectFn, (attempt, delay) => {
        if (onProgress) {
          onProgress(attempt, retryConfig.maxRetries, delay)
        }
      })
      return true
    } catch (error) {
      console.error('Connection retry failed after max attempts:', error)
      return false
    }
  }

  /**
   * WebSocket重连机制
   */
  const retryWebSocket = async (
    wsUrl: string,
    onConnect?: (ws: WebSocket) => void,
    onProgress?: (attempt: number, maxAttempts: number) => void
  ): Promise<WebSocket | null> => {
    try {
      const ws = await executeWithRetry(
        () => createWebSocketConnection(wsUrl),
        (attempt) => {
          if (onProgress) {
            onProgress(attempt, retryConfig.maxRetries)
          }
        }
      )

      if (onConnect) {
        onConnect(ws)
      }

      return ws
    } catch (error) {
      console.error('WebSocket retry failed:', error)
      return null
    }
  }

  /**
   * SSE重连机制
   */
  const retrySSE = async (
    sseUrl: string,
    onConnect?: (eventSource: EventSource) => void,
    onProgress?: (attempt: number, maxAttempts: number) => void
  ): Promise<EventSource | null> => {
    try {
      const eventSource = await executeWithRetry(
        () => createSSEConnection(sseUrl),
        (attempt) => {
          if (onProgress) {
            onProgress(attempt, retryConfig.maxRetries)
          }
        }
      )

      if (onConnect) {
        onConnect(eventSource)
      }

      return eventSource
    } catch (error) {
      console.error('SSE retry failed:', error)
      return null
    }
  }

  /**
   * 网络状态检测重试
   */
  const retryWithNetworkCheck = async (
    operation: () => Promise<void>,
    checkUrl: string = '/api/health'
  ): Promise<boolean> => {
    // 首先检查网络连接
    const isNetworkAvailable = await checkNetworkConnectivity(checkUrl)
    if (!isNetworkAvailable) {
      console.warn('Network not available, skipping retry')
      return false
    }

    try {
      await executeWithRetry(operation)
      return true
    } catch (error) {
      console.error('Retry with network check failed:', error)
      return false
    }
  }

  /**
   * 重置重试状态
   */
  const reset = (): void => {
    currentAttempt.value = 0
    isRetrying.value = false
    lastRetryTime.value = 0
  }

  /**
   * 获取重试统计信息
   */
  const getRetryStats = () => ({
    currentAttempt: currentAttempt.value,
    maxRetries: retryConfig.maxRetries,
    isRetrying: isRetrying.value,
    canRetry: canRetry.value,
    nextRetryDelay: nextRetryDelay.value,
    lastRetryTime: lastRetryTime.value
  })

  return {
    // 状态
    currentAttempt: readonly(currentAttempt),
    isRetrying: readonly(isRetrying),
    canRetry,
    nextRetryDelay,

    // 方法
    executeWithRetry,
    retryConnection,
    retryWebSocket,
    retrySSE,
    retryWithNetworkCheck,
    reset,
    getRetryStats
  }
}

/**
 * 创建WebSocket连接
 */
async function createWebSocketConnection(url: string): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url)
    
    const timeout = setTimeout(() => {
      ws.close()
      reject(new Error('WebSocket connection timeout'))
    }, 5000)

    ws.onopen = () => {
      clearTimeout(timeout)
      resolve(ws)
    }

    ws.onerror = (error) => {
      clearTimeout(timeout)
      reject(new Error(`WebSocket connection failed: ${error}`))
    }
  })
}

/**
 * 创建SSE连接
 */
async function createSSEConnection(url: string): Promise<EventSource> {
  return new Promise((resolve, reject) => {
    const eventSource = new EventSource(url)
    
    const timeout = setTimeout(() => {
      eventSource.close()
      reject(new Error('SSE connection timeout'))
    }, 5000)

    eventSource.onopen = () => {
      clearTimeout(timeout)
      resolve(eventSource)
    }

    eventSource.onerror = (error) => {
      clearTimeout(timeout)
      eventSource.close()
      reject(new Error(`SSE connection failed: ${error}`))
    }
  })
}

/**
 * 检查网络连通性
 */
async function checkNetworkConnectivity(url: string): Promise<boolean> {
  try {
    const response = await fetch(url, {
      method: 'HEAD',
      mode: 'no-cors',
      cache: 'no-cache'
    })
    return true
  } catch (error) {
    // 检查navigator.onLine作为备选
    return navigator.onLine
  }
}

/**
 * 延迟函数
 */
function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}