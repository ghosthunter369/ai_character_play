import api from './api'
import type { BaseResponse, ChatHistoryResponse } from '../types/api'

export const chatService = {
  // AI聊天（流式响应）
  async chat(appId: string | number, message: string): Promise<EventSource> {
    const url = `http://localhost:8123/api/chat/chat?appId=${appId}&message=${encodeURIComponent(message)}`
    return new EventSource(url)
  },

  // 获取用户消息历史
  async getUserMessages(
    appId: string | number, 
    pageSize: number = 50, 
    lastCreateTime?: string
  ): Promise<BaseResponse<ChatHistoryResponse>> {
    let url = `/chatHistory/app/${appId}?pageSize=${pageSize}&messageType=user`
    if (lastCreateTime) {
      url += `&lastCreateTime=${encodeURIComponent(lastCreateTime)}`
    }
    return await api.get(url)
  },

  // 获取AI消息历史
  async getAiMessages(
    appId: string | number, 
    pageSize: number = 50, 
    lastCreateTime?: string
  ): Promise<BaseResponse<ChatHistoryResponse>> {
    let url = `/chatHistory/app/${appId}?pageSize=${pageSize}&messageType=ai`
    if (lastCreateTime) {
      url += `&lastCreateTime=${encodeURIComponent(lastCreateTime)}`
    }
    return await api.get(url)
  },

  // 获取完整聊天历史（并行获取用户和AI消息，前端合并排序）
  async getChatHistory(
    appId: string | number, 
    pageSize: number = 25
  ): Promise<{
    userMessages: any[],
    aiMessages: any[],
    allMessages: any[]
  }> {
    try {
      // 并行获取用户消息和AI消息
      const [userResponse, aiResponse] = await Promise.all([
        this.getUserMessages(appId, pageSize),
        this.getAiMessages(appId, pageSize)
      ])

      const userMessages = userResponse?.data?.data?.records || []
      const aiMessages = aiResponse?.data?.data?.records || []

      // 合并并按时间排序
      const allMessages = [...userMessages, ...aiMessages]
        .map(record => ({
          id: record.id,
          type: record.messageType === 'user' ? 'user' : 'ai',
          content: record.message,
          timestamp: new Date(record.createTime),
          createTime: record.createTime
        }))
        .sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime())

      return {
        userMessages,
        aiMessages,
        allMessages
      }
    } catch (error) {
      console.error('获取聊天历史失败:', error)
      return {
        userMessages: [],
        aiMessages: [],
        allMessages: []
      }
    }
  }
}

// SSE事件处理工具
export class SSEManager {
  private eventSource: EventSource | null = null
  private messageCallback: ((data: string) => void) | null = null
  private doneCallback: (() => void) | null = null
  private errorCallback: ((error: Event) => void) | null = null

  // 开始聊天
  async startChat(appId: string | number, message: string) {
    this.close()
    
    this.eventSource = await chatService.chat(appId, message)
    
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