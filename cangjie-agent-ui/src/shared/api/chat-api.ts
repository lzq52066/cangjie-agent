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
  getConfig(apikey: string, applicationId: string) {
    return request<any>({
      baseURL: CHAT_BASE,
      method: 'GET',
      url: `/config/${applicationId}`,
      headers: withKey(apikey)
    })
  },
  send(apikey: string, data: { applicationId: string; message: string; sessionId?: string }) {
    return request<any>({
      baseURL: CHAT_BASE,
      method: 'POST',
      url: '/send',
      headers: withKey(apikey),
      data
    })
  },
  listSessions(apikey: string, applicationId: string, userId?: string) {
    return request<any[]>({
      baseURL: CHAT_BASE,
      method: 'GET',
      url: `/sessions/${applicationId}`,
      headers: withKey(apikey),
      params: userId ? { userId } : undefined
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
  },
  deleteSession(apikey: string, sessionId: string) {
    return request<void>({
      baseURL: CHAT_BASE,
      method: 'DELETE',
      url: `/sessions/${sessionId}/delete`,
      headers: withKey(apikey)
    })
  },
  sendStream(
    apikey: string,
    data: { applicationId: string; message: string; sessionId?: string; userId?: string }
  ): AsyncIterable<{ event: string; data: any }> {
    return (async function* () {
      const res = await fetch('/api/chat/send-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-API-Key': apikey
        },
        body: JSON.stringify(data)
      })
      if (!res.ok) {
        const txt = await res.text()
        throw new Error(txt || `HTTP ${res.status}`)
      }
      const reader = (res.body as ReadableStream<Uint8Array>).getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let idx
        while ((idx = buffer.indexOf('\n\n')) !== -1) {
          const raw = buffer.slice(0, idx).trim()
          buffer = buffer.slice(idx + 2)
          if (!raw) continue

          // 解析 SSE 字段：event:xxx / data:xxx
          let eventName = 'message'
          let dataStr = ''
          for (const line of raw.split('\n')) {
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim() || 'message'
            } else if (line.startsWith('data:')) {
              dataStr = line.slice(5).trim()
            }
          }
          if (!dataStr) continue
          if (eventName === 'message' && dataStr === '[DONE]') return

          let parsed: any = dataStr
          try {
            parsed = JSON.parse(dataStr)
          } catch {
            // keep raw string
          }
          yield { event: eventName, data: parsed }
        }
      }
    })()
  }
}
