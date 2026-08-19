import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'

export interface Role {
  id: string
  name: string
  code: string
  description?: string
  status: string
  createTime?: string
  updateTime?: string
}

export interface RoleQuery extends PageQuery {
  keyword?: string
  status?: string
}

export interface UserOption {
  userId: string
  username: string
  nickname: string
  email?: string
  phone?: string
}

export interface MenuTreeItem {
  id: string
  name: string
  parentId: string
  children?: MenuTreeItem[]
}

export const roleApi = {
  list(query: RoleQuery) {
    return request<PageResult<Role>>({ method: 'GET', url: '/role', params: query })
  },
  get(id: string) {
    return request<Role>({ method: 'GET', url: `/role/${id}` })
  },
  create(data: Partial<Role>) {
    return request<Role>({ method: 'POST', url: '/role', data })
  },
  update(id: string, data: Partial<Role>) {
    return request<Role>({ method: 'PUT', url: `/role/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/role/${id}` })
  },
  assignUsers(userIds: string[], roleId: string) {
    return request<void>({ method: 'POST', url: '/role/assignUsers', data: { roleId, userIds } })
  },
  assignMenus(menuIds: string[], roleId: string) {
    return request<void>({ method: 'POST', url: '/menu/assignMenus', data: { roleId, menuIds } })
  },
  userOptions(keyword?: string) {
    return request<UserOption[]>({ method: 'GET', url: '/user/options', params: { keyword } })
  },
  menuTree() {
    return request<MenuTreeItem[]>({ method: 'GET', url: '/menu/tree' })
  }
}