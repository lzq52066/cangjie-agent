import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'

export interface AdminUser {
  id: string
  username: string
  nickname?: string
  email?: string
  phone?: string
  role?: string
  isActive?: boolean
  source?: string
  language?: string
  avatar?: string
  createTime?: string
  updateTime?: string
}

export interface UserQuery extends PageQuery {
  keyword?: string
  isActive?: boolean
  source?: string
}

export interface UserSaveDTO {
  username?: string
  password?: string
  nickname?: string
  email?: string
  phone?: string
  isActive?: boolean
  roleIds?: string[]
}

export interface UserDetail {
  user: AdminUser
  roleIds: string[]
}

export const userApi = {
  page(params: UserQuery) {
    return request<PageResult<AdminUser>>({ method: 'GET', url: '/user', params })
  },
  get(id: string) {
    return request<UserDetail>({ method: 'GET', url: `/user/${id}` })
  },
  create(data: UserSaveDTO) {
    return request<AdminUser>({ method: 'POST', url: '/user', data })
  },
  update(id: string, data: UserSaveDTO) {
    return request<void>({ method: 'PUT', url: `/user/${id}`, data })
  },
  resetPassword(id: string, password: string) {
    return request<void>({ method: 'PUT', url: `/user/${id}/password`, data: { password } })
  },
  updateStatus(id: string, isActive: boolean) {
    return request<void>({ method: 'PUT', url: `/user/${id}/status`, data: { isActive } })
  }
}
