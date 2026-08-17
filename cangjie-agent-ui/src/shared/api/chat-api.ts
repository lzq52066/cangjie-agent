import { request } from '@shared/api/http'

/**
 * 对话开放接口：使用应用 API Key 鉴权（请求头 X-API-Key），不依赖后台登录态。
 * 供 chat 入口（含 iframe 嵌入场景）调用。
 */
const CHAT_BASE = '/api/chat'

function withKey(apikey: string) {
  return { 'X-API-Key': apikey }
}

export const chatApi = {
  send(apikey: string, data: { applicationId: string; message: string; sessionId?: string }) {
    return request<any>({
      baseURL: CHAT_BASE,
      method: 'POST',
      url: '/send',
      headers: withKey(apikey),
      data
    })
  },
  listSessions(apikey: string, applicationId: string) {
    return request<any[]>({
      baseURL: CHAT_BASE,
      method: 'GET',
      url: `/sessions/${applicationId}`,
      headers: withKey(apikey)
    })
  },
  listMessages(apikey: string, sessionId: string) {
    return request<any[]>({
      baseURL: CHAT_BASE,
      method: 'GET',
      url: `/messages/${sessionId}`,
      headers: withKey(apikey)
    })
  },
  closeSession(apikey: string, sessionId: string) {
    return request<any>({
      baseURL: CHAT_BASE,
      method: 'DELETE',
      url: `/sessions/${sessionId}`,
      headers: withKey(apikey)
    })
  }
}
