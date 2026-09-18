import { request } from '@shared/api/http'
import type { PageResult } from '@shared/types'

/** 长期记忆实体（后端 LongTermMemoryEntity，表 memory） */
export interface LongTermMemory {
  id?: string
  userId: string
  applicationId: string
  /** preference / background / convention / goal */
  dimension: string
  content: string
  confidence?: number
  /** inferred 自动提取 / explicit 人工录入 / import 导入 */
  source?: string
  triggerCount?: number
  lastTriggeredAt?: string
  isActive?: boolean
  /** user 用户画像（跨会话）/ scene 场景事实（会话内） */
  memoryType?: string
  sessionId?: string
  createBy?: string
  updateBy?: string
  createTime?: string
  updateTime?: string
}

/** 列表项：后端按强度评分排序，额外附带 score */
export interface MemoryItem {
  memory: LongTermMemory
  score: number
}

export interface MemoryQuery {
  /** 管理员不传时查全部用户；普通用户后端强制限定为本人 */
  userId?: string
  applicationId?: string
  dimension?: string
  memoryType?: string
  includeInactive?: boolean
  pageNum?: number
  pageSize?: number
}

export const memoryApi = {
  list(params: MemoryQuery) {
    return request<PageResult<MemoryItem>>({ method: 'GET', url: '/memory', params })
  },
  create(data: Partial<LongTermMemory>) {
    return request<LongTermMemory>({ method: 'POST', url: '/memory', data })
  },
  update(id: string, data: Pick<Partial<LongTermMemory>, 'content' | 'confidence' | 'dimension'>) {
    return request<LongTermMemory>({ method: 'PUT', url: `/memory/${id}`, data })
  },
  deactivate(id: string) {
    return request<void>({ method: 'POST', url: `/memory/${id}/deactivate` })
  },
  reactivate(id: string) {
    return request<void>({ method: 'POST', url: `/memory/${id}/reactivate` })
  },
  remove(id: string) {
    return request<void>({ method: 'DELETE', url: `/memory/${id}` })
  }
}
