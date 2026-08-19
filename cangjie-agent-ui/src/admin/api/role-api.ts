import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'

/** 角色状态：1-激活 0-停用 */
export const ROLE_STATUS = {
  ACTIVE: 1,
  INACTIVE: 0
} as const

export interface Role {
  id: string
  roleName: string
  roleCode: string
  description?: string
  status: number
  createTime?: string
  updateTime?: string
}

export interface RoleQuery extends PageQuery {
  roleName?: string
  roleCode?: string
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
  assignUsers(roleId: string, userIds: string[]) {
    return request<void>({ method: 'PUT', url: `/role/${roleId}/users`, data: { userIds } })
  },
  assignMenus(roleId: string, menuIds: string[]) {
    return request<void>({ method: 'PUT', url: `/role/${roleId}/menus`, data: { menuIds } })
  },
  userOptions(keyword?: string) {
    return request<UserOption[]>({ method: 'GET', url: '/user/options', params: { keyword } })
  },
  menuTree() {
    return request<MenuTreeItem[]>({ method: 'GET', url: '/menu/tree' })
  }
}