import api from './api'
import type { 
  BaseResponse, 
  AppDTO, 
  AppUpdateRequest, 
  AppQueryRequest, 
  AppVO, 
  PageWrapper,
  DeleteRequest 
} from '../types/api'

export const appService = {
  // 创建应用
  async createApp(data: AppDTO): Promise<BaseResponse<string>> {
    return await api.post('/app/create', data)
  },

  // 删除应用
  async deleteApp(data: DeleteRequest): Promise<BaseResponse<boolean>> {
    return await api.post('/app/delete', data)
  },

  // 更新应用
  async updateApp(data: AppUpdateRequest): Promise<BaseResponse<boolean>> {
    return await api.post('/app/update', data)
  },

  // 根据ID获取应用详情
  async getAppById(id: number): Promise<BaseResponse<AppVO>> {
    return await api.get(`/app/get/vo?id=${id}`)
  },

  // 分页获取当前用户的应用列表
  async listMyApps(data: AppQueryRequest): Promise<BaseResponse<PageWrapper<AppVO>>> {
    return await api.post('/app/my/list/page/vo', data)
  },

  // 分页获取所有应用列表（管理员）
  async listAllApps(data: AppQueryRequest): Promise<BaseResponse<PageWrapper<AppVO>>> {
    return await api.post('/app/all/list/page/vo', data)
  },

  // 分页获取精选应用列表
  async listGoodApps(data: AppQueryRequest): Promise<BaseResponse<PageWrapper<AppVO>>> {
    return await api.post('/app/good/list/page/vo', data)
  },

  // 管理员删除应用
  async deleteAppByAdmin(data: DeleteRequest): Promise<BaseResponse<boolean>> {
    return await api.post('/app/admin/delete', data)
  },

  // 设置精选应用（管理员）
  async setPriorityApp(appId: number): Promise<BaseResponse<boolean>> {
    return await api.put(`/app/setPriorityApp?appId=${appId}`)
  },

  // 取消精选应用（管理员）
  async cancelPriorityApp(appId: number): Promise<BaseResponse<boolean>> {
    return await api.put(`/app/cancelPriorityApp?appId=${appId}`)
  }
}