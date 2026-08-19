import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'

/** 菜单类型 */
export const MENU_TYPE = {
  DIRECTORY: 'directory',
  MENU: 'menu',
  BUTTON: 'button'
} as const

export interface Menu {
  id: string
  parentId: string
  name: string
  code?: string
  path?: string
  component?: string
  icon?: string
  type: string
  sort: number
  status: string
  children?: Menu[]
}

export interface MenuQuery extends PageQuery {
  name?: string
}

export const menuApi = {
  list(query: MenuQuery) {
    return request<PageResult<Menu>>({ method: 'GET', url: '/menu', params: query })
  },
  tree() {
    return request<Menu[]>({ method: 'GET', url: '/menu/tree' })
  },
  get(id: string) {
    return request<Menu>({ method: 'GET', url: `/menu/${id}` })
  },
  create(data: Partial<Menu>) {
    return request<Menu>({ method: 'POST', url: '/menu', data })
  },
  update(id: string, data: Partial<Menu>) {
    return request<Menu>({ method: 'PUT', url: `/menu/${id}`, data })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/menu/${id}` })
  }
}