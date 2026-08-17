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

export interface UserIdentity {
  userId: string
  username: string
  nickname: string
  email: string
  phone: string
  role: string
  tenantId: string
  workspaceId: string
}

export interface LoginResult {
  token: string
  tokenName: string
  user: UserIdentity
}
