// API 响应基础类型
export interface BaseResponse<T = any> {
  code: number
  data: T
  message?: string
}

// 分页请求参数
export interface PageRequest {
  pageNum: number
  pageSize: number
}

// 删除请求参数
export interface DeleteRequest {
  id: number
}

// 用户相关类型
export interface UserLoginRequest {
  userAccount: string
  userPassword: string
}

export interface UserRegisterRequest {
  userAccount: string
  userPassword: string
  checkPassword: string
}

export interface UserAddRequest {
  userAccount: string
  userName?: string
  userAvatar?: string
  userProfile?: string
  userRole?: string
}

export interface UserUpdateRequest {
  id: number
  userAccount?: string
  userName?: string
  userAvatar?: string
  userProfile?: string
  userRole?: string
}

export interface UserQueryRequest extends PageRequest {
  userAccount?: string
  userName?: string
  userRole?: string
}

export interface LoginUserVO {
  id: number
  userAccount: string
  userName: string
  userAvatar: string
  userProfile: string
  userRole: string
  createTime: string
  updateTime: string
}

export interface UserVO {
  id: number
  userAccount: string
  userName: string
  userAvatar: string
  userProfile: string
  userRole: string
  createTime: string
  updateTime: string
}

// 应用相关类型
export interface AppDTO {
  appName: string
  description?: string
  initPrompt?: string
  prologue?: string
  cover?: string
}

export interface AppUpdateRequest {
  appId: number
  appName: string
}

export interface AppQueryRequest extends PageRequest {
  appName?: string
  description?: string
  userId?: number
  priority?: number
}

export interface AppVO {
  id: number
  appId: number  // 保持向后兼容
  appName: string
  description: string
  initPrompt: string
  prologue: string
  cover: string
  userId: number
  userName: string
  userAvatar?: string
  priority: number
  createTime: string
  editTime?: string
  updateTime?: string
}

// 聊天历史相关类型
export interface ChatHistoryResponse {
  records: ChatHistoryRecord[]
  hasMore: boolean
  lastCreateTime?: string
}

export interface ChatHistoryRecord {
  id: number
  appId: number
  userId: number
  messageType: string
  content: string
  createTime: string
}

// 分页包装类
export interface PageWrapper<T> {
  records: T[]
  total: number
  size: number
  current: number
}