import { request } from '@shared/api/http'

/**
 * 对话开放接口：使用应用 API Key 鉴权（请求头 X-API-Key），不依赖后台登录态。
 * 供 chat 入口（含 iframe 嵌入场景）调用。
 */
const CHAT_BASE = '/api/chat'
const OPEN_BASE = '/api/open'

/**
 * 审批决策请求体。
 * resumeToken 由 approval_required 事件下发，单次生效，必须原样回传。
 */
export interface ApprovalDecision {
  approved: boolean
  resumeToken: string
  sessionId?: string
  remark?: string
  decidedBy?: string
}

/** 待审批项（SSE approval_required 帧与恢复结果 pendingApproval 字段共用） */
export interface PendingApproval {
  approvalId: string
  runId: string
  toolName?: string
  /** SSE 帧里工具名字段是 tool，恢复结果里是 toolName，两者都兼容 */
  tool?: string
  toolType?: string
  arguments?: string
  reason?: string
  riskLevel?: string
  expireAt: number
  resumeToken: string
}

/** 审批决策后恢复执行的产出 */
export interface ApprovalResumeResult {
  runId: string
  sessionId?: string
  /** completed / waiting_approval / failed / cancelled */
  status: string
  message?: string
  errorMessage?: string
  finishReason?: string
  rounds?: number
  toolCallCount?: number
  tokens?: number
  promptTokens?: number
  completionTokens?: number
  duration?: number
  pendingApproval?: PendingApproval
}

/** 待浏览器执行的本地工具调用（SSE local_tool_required 帧） */
export interface PendingLocalTool {
  runId: string
  callId: string
  /** SSE 帧里工具名字段是 tool */
  tool?: string
  toolName?: string
  /** 模型给出的参数（JSON 字符串） */
  arguments?: string
  resumeToken: string
}

/** 本地工具结果回传请求体 */
export interface LocalToolResult {
  runId: string
  callId: string
  resumeToken: string
  sessionId?: string
  failed: boolean
  /** 成功时的结果（JSON 字符串） */
  result?: string
  /** 失败时的错误信息 */
  errorMessage?: string
}

/** 本地工具结果回传后恢复执行的产出 */
export interface LocalToolResumeResult {
  runId: string
  sessionId?: string
  /** completed / waiting_local / waiting_approval / failed / cancelled */
  status: string
  message?: string
  errorMessage?: string
  finishReason?: string
  rounds?: number
  toolCallCount?: number
  tokens?: number
  promptTokens?: number
  completionTokens?: number
  duration?: number
  pendingLocalTool?: PendingLocalTool
  pendingApproval?: PendingApproval
}

/**
 * 恢复执行以同步方式跑完剩余轮次（多次模型调用 + 工具执行），远超默认 60s 超时
 */
const RESUME_TIMEOUT = 300000

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
  /** 网页匿名聊天：应用配置（仅 appId，免 Key） */
  getWebConfig(applicationId: string) {
    return request<any>({
      baseURL: OPEN_BASE,
      method: 'GET',
      url: `/chat/config/${applicationId}`
    })
  },
  /** 网页匿名聊天：会话列表 */
  webSessions(applicationId: string, userId?: string) {
    return request<any[]>({
      baseURL: OPEN_BASE,
      method: 'GET',
      url: '/chat/sessions',
      params: { applicationId, userId }
    })
  },
  /** 网页匿名聊天：会话消息历史 */
  webMessages(sessionId: string) {
    return request<any[]>({
      baseURL: OPEN_BASE,
      method: 'GET',
      url: `/chat/sessions/${sessionId}/messages`
    })
  },
  /** 网页匿名聊天：删除会话 */
  webDeleteSession(sessionId: string) {
    return request<void>({
      baseURL: OPEN_BASE,
      method: 'DELETE',
      url: `/chat/sessions/${sessionId}`
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
  /** 审批决策并恢复运行（API Key 模式） */
  decideApproval(apikey: string, approvalId: string, data: ApprovalDecision) {
    return request<ApprovalResumeResult>({
      baseURL: CHAT_BASE,
      method: 'POST',
      url: `/approval/${approvalId}/decide`,
      headers: withKey(apikey),
      timeout: RESUME_TIMEOUT,
      data
    })
  },
  /** 网页匿名聊天：会话下待审批单（无则 null） */
  webPendingApproval(sessionId: string) {
    return request<PendingApproval | null>({
      baseURL: OPEN_BASE,
      method: 'GET',
      url: '/chat/approval/pending',
      params: { sessionId }
    })
  },
  /** 网页匿名聊天：审批决策并恢复运行 */
  webDecideApproval(approvalId: string, data: ApprovalDecision) {
    return request<ApprovalResumeResult>({
      baseURL: OPEN_BASE,
      method: 'POST',
      url: `/chat/approval/${approvalId}/decide`,
      timeout: RESUME_TIMEOUT,
      data
    })
  },
  /** 网页匿名聊天：回传浏览器侧本地工具执行结果并恢复运行 */
  webLocalToolResult(data: LocalToolResult) {
    return request<LocalToolResumeResult>({
      baseURL: OPEN_BASE,
      method: 'POST',
      url: '/chat/local-tool/result',
      timeout: RESUME_TIMEOUT,
      data
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
  },
  /** 网页匿名聊天：流式对话（免 Key，仅 appId） */
  webSendStream(
    data: { applicationId: string; message: string; sessionId?: string; userId?: string }
  ): AsyncIterable<{ event: string; data: any }> {
    return (async function* () {
      const res = await fetch(`${OPEN_BASE}/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
