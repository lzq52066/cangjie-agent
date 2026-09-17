export interface R<T = any> {
  code: number
  msg: string
  data: T
  timestamp: number
}

/** 下拉选项一次取足量数据用的分页大小（与后端分页插件 maxLimit 保持一致或更小） */
export const OPTION_PAGE_SIZE = 500

export interface PageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface MenuNode {
  id: string
  name: string
  path?: string
  component?: string
  icon?: string
  type?: string
  status?: string
  sort?: number
  parentId?: string
  children?: MenuNode[]
}

export interface UserIdentity {
  userId: string
  username: string
  nickname: string
  email: string
  phone: string
  role: string
  workspaceId: string
  permissions?: string[]
  menus?: MenuNode[]
}

export interface LoginResult {
  token: string
  tokenName: string
  user: UserIdentity
}
