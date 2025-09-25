import api from './api'
import type { 
  BaseResponse, 
  UserLoginRequest, 
  UserRegisterRequest, 
  UserAddRequest, 
  UserUpdateRequest, 
  UserQueryRequest, 
  LoginUserVO, 
  UserVO, 
  PageWrapper,
  DeleteRequest 
} from '../types/api'

export const userService = {
  // 用户注册
  async register(data: UserRegisterRequest): Promise<BaseResponse<number>> {
    return await api.post('/user/register', data)
  },

  // 用户登录
  async login(data: UserLoginRequest): Promise<BaseResponse<LoginUserVO>> {
    return await api.post('/user/login', data)
  },

  // 获取当前登录用户
  async getCurrentUser(): Promise<BaseResponse<LoginUserVO>> {
    return await api.get('/user/get/login')
  },

  // 用户退出登录
  async logout(): Promise<BaseResponse<boolean>> {
    return await api.post('/user/logout')
  },

  // 添加用户（管理员）
  async addUser(data: UserAddRequest): Promise<BaseResponse<number>> {
    return await api.post('/user/add', data)
  },

  // 根据ID获取用户（管理员）
  async getUserById(id: number): Promise<BaseResponse<UserVO>> {
    return await api.get(`/user/get/vo?id=${id}`)
  },

  // 删除用户（管理员）
  async deleteUser(data: DeleteRequest): Promise<BaseResponse<boolean>> {
    return await api.post('/user/delete', data)
  },

  // 更新用户（管理员）
  async updateUser(data: UserUpdateRequest): Promise<BaseResponse<boolean>> {
    return await api.post('/user/update', data)
  },

  // 分页获取用户列表（管理员）
  async listUsers(data: UserQueryRequest): Promise<BaseResponse<PageWrapper<UserVO>>> {
    return await api.post('/user/list/page/vo', data)
  }
}