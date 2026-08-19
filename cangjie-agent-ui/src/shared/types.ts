export interface R<T = any> {
  code: number
  msg: string
  data: T
  timestamp: number
}

export interface PageQuery {
  page?: number
  size?: number
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
  tenantId: string
  workspaceId: string
  permissions?: string[]
  menus?: MenuNode[]
}

export interface LoginResult {
  token: string
  tokenName: string
  user: UserIdentity
}
