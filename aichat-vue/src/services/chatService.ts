import api from './api'
import type { BaseResponse, ChatHistoryResponse } from '../types/api'

export const chatService = {
  // AI聊天（流式响应）
  async chat(appId: number, message: string): Promise<EventSource> {
    const url = `http://localhost:8123/api/chat/chat?appId=${appId}&message=${encodeURIComponent(message)}`
    return new EventSource(url)
  },

  // 获取应用聊天历史
  async getChatHistory(
    appId: number, 
    pageSize: number = 10, 
    lastCreateTime?: string
  ): Promise<BaseResponse<ChatHistoryResponse>> {
    let url = `/chatHistory/app/${appId}?pageSize=${pageSize}`
    if (lastCreateTime) {
      url += `&lastCreateTime=${encodeURIComponent(lastCreateTime)}`
    }
    return await api.get(url)
  }
}

// SSE事件处理工具
export class SSEManager {
  private eventSource: EventSource | null = null
  private messageCallback: ((data: string) => void) | null = null
  private doneCallback: (() => void) | null = null
  private errorCallback: ((error: Event) => void) | null = null

  // 开始聊天
  startChat(appId: number, message: string) {
    this.close()
    
    this.eventSource = chatService.chat(appId, message)
    
    this.eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.d && this.messageCallback) {
          this.messageCallback(data.d)
        }
      } catch (error) {
        console.error('SSE message parse error:', error)
      }
    }

    this.eventSource.addEventListener('done', () => {
      if (this.doneCallback) {
        this.doneCallback()
      }
      this.close()
    })

    this.eventSource.onerror = (error) => {
      if (this.errorCallback) {
        this.errorCallback(error)
      }
      this.close()
    }
  }

  // 设置消息回调
  onMessage(callback: (data: string) => void) {
    this.messageCallback = callback
  }

  // 设置完成回调
  onDone(callback: () => void) {
    this.doneCallback = callback
  }

  // 设置错误回调
  onError(callback: (error: Event) => void) {
    this.errorCallback = callback
  }

  // 关闭连接
  close() {
    if (this.eventSource) {
      this.eventSource.close()
      this.eventSource = null
    }
  }

  // 获取连接状态
  get readyState(): number {
    return this.eventSource?.readyState || 0
  }
}