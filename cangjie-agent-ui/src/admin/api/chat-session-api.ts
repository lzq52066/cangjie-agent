import { request } from '@shared/api/http'
import type { PageQuery, PageResult } from '@shared/types'

export interface SessionQuery extends PageQuery {
  applicationId?: string
  userId?: string
  sessionId?: string
  source?: string
  status?: string
  keyword?: string
}

export interface MessageQuery extends PageQuery {
  role?: string
  feedback?: string
  keyword?: string
}

export const chatSessionApi = {
  /** 会话分页 */
  sessions(params: SessionQuery) {
    return request<PageResult<any>>({ method: 'GET', url: '/chat/sessions', params })
  },
  /** 某会话的消息明细分页（时间正序） */
  messages(sessionId: string, params: MessageQuery) {
    return request<PageResult<any>>({
      method: 'GET',
      url: `/chat/sessions/${encodeURIComponent(sessionId)}/messages`,
      params
    })
  }
}
