<template>
  <div class="chat-app" :class="{ embedded, 'sidebar-open': showHistory }">
    <!-- Sidebar -->
    <Transition name="slide">
      <aside v-if="showHistory && !embedded" class="sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">对话记录</span>
          <button class="icon-btn" @click="showHistory = false" title="关闭">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
        </div>
        <button class="new-chat-btn" @click="newChat">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
          新建对话
        </button>
        <div class="session-list">
          <div
            v-for="s in sessions" :key="s.sessionId"
            class="session-card" :class="{ active: s.sessionId === sessionId }"
            @click="openSession(s)"
          >
            <div class="session-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
            </div>
            <div class="session-info">
              <div class="session-title">{{ s.title || '未命名对话' }}</div>
              <div class="session-time">{{ formatTime(s.updateTime || s.createTime) }}</div>
            </div>
            <button class="session-delete-btn" title="删除对话" @click.stop="deleteSession(s)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            </button>
          </div>
          <div v-if="sessions.length === 0" class="empty-sessions">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.3"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
            <span>暂无历史对话</span>
          </div>
        </div>
      </aside>
    </Transition>

    <!-- Main -->
    <main class="main">
      <!-- Header -->
      <header class="chat-header">
        <div class="header-left">
          <button v-if="!embedded && sessions.length" class="icon-btn" @click="showHistory = !showHistory" title="对话记录">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 12h18M3 6h18M3 18h18"/></svg>
          </button>
          <div class="brand">
            <div class="logo-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/></svg>
            </div>
            <div>
              <div class="title">{{ title }}</div>
              <div class="status-line">
                <span class="status-dot" :class="{ online: !configError }"></span>
                <span class="subtitle">{{ subtitle }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="header-actions">
          <button v-if="!embedded" class="action-btn" @click="newChat" title="新对话">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
          </button>
        </div>
      </header>

      <!-- Config Error -->
      <div v-if="configError" class="config-error">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>
        {{ configError }}
      </div>

      <!-- Chat Body -->
      <div class="chat-body" ref="bodyRef">
        <!-- Welcome -->
        <div v-if="messages.length === 0 && !typing" class="welcome">
          <div class="welcome-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/></svg>
          </div>
          <h2 class="welcome-title">{{ welcomeTitle }}</h2>
          <p class="welcome-sub">{{ welcomeSub }}</p>
          <div v-if="!configError && suggestionCards.length" class="suggestions">
            <button class="suggestion-card" v-for="(s, i) in suggestionCards" :key="i" @click="useSuggestion(s)">
              <span class="suggestion-icon">{{ s.icon }}</span>
              <span class="suggestion-text">{{ s.text }}</span>
            </button>
          </div>
        </div>

        <!-- Messages -->
        <div v-for="(m, i) in messages" :key="i" class="message-row" :class="m.role">
          <div class="message-avatar">
            <div v-if="m.role === 'user'" class="avatar user-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            </div>
            <div v-else class="avatar ai-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/></svg>
            </div>
          </div>
          <div class="message-content">
            <div class="message-role">{{ m.role === 'user' ? '你' : title }}</div>
            <div class="message-bubble" :class="{ 'user-bubble': m.role === 'user' }">
              <div class="message-text" v-html="renderMarkdown(m.content)"></div>
              <div v-if="m.error" class="message-error">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                <span>{{ m.error }}</span>
              </div>
            </div>
            <!-- Sources (默认折叠) -->
            <div v-if="m.sources?.length" class="sources-card">
              <div class="sources-header" @click="toggleSources(m)">
                <div class="sources-header-left">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><path d="M14 2v6h6M16 13H8M16 17H8M10 9H8"/></svg>
                  <span>引用来源 ({{ m.sources.length }})</span>
                </div>
                <svg :class="['chevron', { open: !m._sourcesCollapsed }]" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
              </div>
              <Transition name="collapse">
                <div v-show="!m._sourcesCollapsed" class="sources-body">
                  <div v-for="(s, si) in m.sources" :key="si" class="source-item">
                    <span class="source-badge">{{ si + 1 }}</span>
                    <span class="source-text">{{ sourceText(s) }}</span>
                  </div>
                </div>
              </Transition>
            </div>
            <!-- Meta -->
            <div v-if="m.duration != null" class="message-meta">
              <span>{{ m.duration }}ms</span>
              <span v-if="m.tokens" class="meta-dot">·</span>
              <span v-if="m.tokens">{{ m.tokens }} tokens</span>
            </div>
          </div>
        </div>

        <!-- 审批卡片：引擎挂起等待人工放行 -->
        <div v-if="pendingApproval" class="message-row assistant">
          <div class="message-avatar">
            <div class="avatar approval-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M12 8v4M12 16h.01"/></svg>
            </div>
          </div>
          <div class="message-content">
            <div class="message-role">{{ title }}</div>
            <div class="approval-card" :class="{ 'is-expired': approvalExpired }">
              <div class="approval-head">
                <span class="approval-risk" :class="approvalRiskClass">{{ approvalRiskText }}</span>
                <span class="approval-title">需要你的确认</span>
                <span class="approval-count">
                  <template v-if="approvalExpired">已超时</template>
                  <template v-else-if="pendingApproval.expireAt">剩余 {{ approvalCountdown }}</template>
                </span>
              </div>
              <div class="approval-desc">
                助手请求执行工具 <code class="approval-tool">{{ approvalToolName }}</code>
                <span v-if="pendingApproval.toolType" class="approval-type">· {{ pendingApproval.toolType }}</span>
              </div>
              <div v-if="pendingApproval.reason" class="approval-reason">{{ pendingApproval.reason }}</div>
              <div v-if="pendingApproval.arguments" class="approval-args">
                <div class="approval-args-label">执行参数</div>
                <pre class="approval-args-body">{{ prettyArgs(pendingApproval.arguments) }}</pre>
              </div>
              <div class="approval-actions">
                <button
                  class="approval-btn deny"
                  :disabled="approvalBusy"
                  @click="resolveApproval(false)"
                >拒绝</button>
                <button
                  class="approval-btn allow"
                  :disabled="approvalBusy"
                  @click="resolveApproval(true)"
                >
                  <span v-if="approvalSubmitting" class="approval-spinner"></span>
                  允许执行
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 本地工具：引擎挂起，浏览器在授权目录内执行 -->
        <div v-if="pendingLocalTool" class="message-row assistant">
          <div class="message-avatar">
            <div class="avatar local-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>
            </div>
          </div>
          <div class="message-content">
            <div class="message-role">{{ title }}</div>
            <div class="local-tool-card">
              <div class="local-tool-head">
                <span v-if="localToolBusy" class="local-tool-spinner"></span>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><path d="M22 4L12 14.01l-3-3"/></svg>
                <span class="local-tool-title">{{ localToolStatusText }}</span>
              </div>
              <div class="local-tool-desc">
                需要在你的电脑上执行本地操作
                <code class="local-tool-name">{{ localToolDisplayName }}</code>
              </div>
              <div v-if="localToolTargetPath" class="local-tool-target">
                <span class="local-tool-target-label">目标：</span>
                <code class="local-tool-target-path">{{ localToolTargetPath }}</code>
              </div>
              <details v-if="pendingLocalTool.arguments" class="local-tool-args">
                <summary class="local-tool-args-label">查看完整参数</summary>
                <pre class="local-tool-args-body">{{ prettyArgs(pendingLocalTool.arguments) }}</pre>
              </details>
              <!-- 写/改/删二次确认区 -->
              <div
                v-if="localToolConfirm"
                class="local-confirm"
                :class="localToolConfirm.riskLevel === 'high' ? 'danger' : 'normal'"
              >
                <div class="local-confirm-title">
                  <svg v-if="localToolConfirm.riskLevel === 'high'" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                  <span>{{ localToolConfirm.title }}</span>
                </div>
                <ul class="local-confirm-warnings">
                  <li v-for="(w, i) in localToolConfirm.warnings" :key="i">{{ w }}</li>
                </ul>
                <div class="local-tool-actions">
                  <button
                    class="local-tool-btn"
                    :class="localToolConfirm.riskLevel === 'high' ? 'danger' : 'primary'"
                    :disabled="localToolBusy"
                    @click="confirmLocalTool"
                  >{{ localToolConfirm.confirmText }}</button>
                  <button
                    class="local-tool-btn ghost"
                    :disabled="localToolBusy"
                    @click="rejectLocalTool"
                  >取消</button>
                </div>
              </div>
              <div v-if="localToolHint" class="local-tool-hint">{{ localToolHint }}</div>
              <div class="local-tool-actions">
                <button
                  v-if="localToolNeedAuth && localFsSupported"
                  class="local-tool-btn primary"
                  :disabled="localToolBusy"
                  @click="authorizeAndRunLocal"
                >选择文件夹并执行</button>
                <button
                  v-if="localToolFailed && localToolNotFound && localFsSupported"
                  class="local-tool-btn primary"
                  :disabled="localToolBusy"
                  @click="reauthorizeAndRunLocal"
                >重新选择文件夹并执行</button>
                <button
                  v-if="localToolFailed && !localToolNotFound"
                  class="local-tool-btn primary"
                  :disabled="localToolBusy"
                  @click="prepareLocalTool"
                >重试</button>
              </div>
            </div>
          </div>
        </div>

        <!-- Typing indicator（仅流式开始前显示） -->
        <div v-if="typing && !streamingStarted" class="message-row assistant">
          <div class="message-avatar">
            <div class="avatar ai-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/></svg>
            </div>
          </div>
          <div class="message-content">
            <div class="message-role">{{ title }}</div>
            <div class="typing-indicator">
              <div class="typing-dot"></div>
              <div class="typing-dot"></div>
              <div class="typing-dot"></div>
            </div>
          </div>
        </div>

        <div class="scroll-anchor" ref="scrollAnchor"></div>
      </div>

      <!-- Footer Input -->
      <div class="chat-footer">
        <div class="input-wrapper" @click="focusInput">
          <textarea
            v-model="input"
            class="chat-input"
            :disabled="!!configError || typing"
            placeholder="输入消息..."
            rows="1"
            @keydown.enter.exact.prevent="send"
            @input="autoResize"
            ref="inputRef"
          ></textarea>
          <button
            class="send-btn"
            :class="{ active: input.trim() && !typing && !configError }"
            :disabled="!input.trim() || typing || !!configError"
            @click="send"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/></svg>
          </button>
        </div>
        <div class="footer-hint">Enter 发送 / Shift+Enter 换行</div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  chatApi,
  type ApprovalResumeResult,
  type LocalToolResumeResult,
  type PendingApproval,
  type PendingLocalTool
} from '@shared/api/chat-api'
import {
  authorizeDirectory,
  executeLocalTool,
  getRootHandle,
  isLocalFsSupported,
  isMutatingLocalTool,
  planLocalTool,
  type LocalToolConfirmPlan
} from './local-fs'
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js/lib/core'
// 按需注册常用语言，避免引入全量包（全量约 1MB）
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import scss from 'highlight.js/lib/languages/scss'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import shell from 'highlight.js/lib/languages/shell'
import sql from 'highlight.js/lib/languages/sql'
import python from 'highlight.js/lib/languages/python'
import go from 'highlight.js/lib/languages/go'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'
import ini from 'highlight.js/lib/languages/ini'
import properties from 'highlight.js/lib/languages/properties'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import diff from 'highlight.js/lib/languages/diff'
import plaintext from 'highlight.js/lib/languages/plaintext'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/atom-one-dark.css'

const REGISTERED: Record<string, Parameters<typeof hljs.registerLanguage>[1]> = {
  java, javascript, js: javascript, typescript, ts: typescript,
  xml, html: xml, vue: xml, css, scss, json, bash, sh: bash, shell,
  sql, python, py: python, go, golang: go, yaml, yml: yaml,
  markdown, md: markdown, ini, properties, dockerfile, diff, plaintext, text: plaintext
}
Object.entries(REGISTERED).forEach(([name, lang]) => hljs.registerLanguage(name, lang))

const marked = new Marked(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      const language = hljs.getLanguage(lang) ? lang : 'plaintext'
      return hljs.highlight(code, { language, ignoreIllegals: true }).value
    }
  })
)
marked.setOptions({ gfm: true, breaks: true })

// DOMPurify 默认允许 class 属性；明确放行 hljs 生成的标签与 code 语言类
const PURIFY_CONFIG = { ADD_ATTR: ['target', 'rel'] }
function safeMarkdown(text: string): string {
  const rawHtml = marked.parse(text, { async: false }) as string
  return DOMPurify.sanitize(rawHtml, PURIFY_CONFIG)
}

interface Msg {
  role: 'user' | 'assistant'
  content: string
  sources?: Record<string, any>[]
  _sourcesCollapsed?: boolean
  tokens?: number
  duration?: number
  /** 流式中断时的错误信息：内容照常渲染，错误以独立提示块展示在下方 */
  error?: string
}

const messages = ref<Msg[]>([])
const sessions = ref<any[]>([])
const input = ref('')
const typing = ref(false)
const streamingStarted = ref(false)
const bodyRef = ref<HTMLElement | null>(null)
const scrollAnchor = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const showHistory = ref(false)

const applicationId = ref('')
const sessionId = ref('')
const userId = ref('')
const embedded = ref(false)
const title = ref('CangJie Chat')
const configError = ref('')

/* ===== 工具审批（人工在环）===== */
// 引擎遇到高风险工具时会写检查点挂起 run 并推 approval_required 帧，
// 卡片把决策送回后由后端同步跑完剩余轮次；令牌只在这两处的响应里出现，丢了就只能重开会话。
const pendingApproval = ref<PendingApproval | null>(null)
const approvalSubmitting = ref(false)
const approvalNow = ref(Date.now())
let approvalTimer: ReturnType<typeof setInterval> | null = null

const approvalToolName = computed(() => pendingApproval.value?.toolName || pendingApproval.value?.tool || '未知工具')
const approvalLeftMs = computed(() => (pendingApproval.value?.expireAt ?? 0) - approvalNow.value)
const approvalExpired = computed(
  () => !!pendingApproval.value && pendingApproval.value.expireAt > 0 && approvalLeftMs.value <= 0
)
const approvalBusy = computed(() => approvalSubmitting.value || approvalExpired.value)
const approvalRiskText = computed(() => {
  const level = (pendingApproval.value?.riskLevel || '').toLowerCase()
  return level === 'high' ? '高风险' : level === 'medium' ? '中风险' : '低风险'
})
const approvalRiskClass = computed(() => {
  const level = (pendingApproval.value?.riskLevel || 'low').toLowerCase()
  return level === 'high' ? 'risk-high' : level === 'medium' ? 'risk-medium' : 'risk-low'
})
const approvalCountdown = computed(() => {
  const left = Math.max(0, Math.floor(approvalLeftMs.value / 1000))
  const m = Math.floor(left / 60)
  const s = left % 60
  return `${m}:${String(s).padStart(2, '0')}`
})

/** SSE 帧与恢复结果字段名不一致（tool / toolName），归一后再交给卡片 */
function normalizeApproval(raw: any): PendingApproval | null {
  if (!raw || !raw.approvalId || !raw.resumeToken) return null
  return { ...raw, toolName: raw.toolName || raw.tool }
}

function setPendingApproval(raw: any) {
  const normalized = normalizeApproval(raw)
  if (!normalized) return
  pendingApproval.value = normalized
  approvalNow.value = Date.now()
  if (!approvalTimer) {
    approvalTimer = setInterval(() => {
      approvalNow.value = Date.now()
      // 超时后审批单已失效，直接收起卡片，避免出现点了才报错的死按钮
      if (approvalExpired.value) {
        stopApprovalCountdown()
        pendingApproval.value = null
        messages.value.push({ role: 'assistant', content: '审批已超时，本次运行不再恢复，请重新发起对话' })
        scroll()
      }
    }, 1000)
  }
  scroll()
}

function clearPendingApproval() {
  pendingApproval.value = null
  stopApprovalCountdown()
}

function stopApprovalCountdown() {
  if (approvalTimer) {
    clearInterval(approvalTimer)
    approvalTimer = null
  }
}

/** 参数原文是 JSON 字符串，能解析就格式化缩进展示，解析不了按原样输出 */
function prettyArgs(raw?: string): string {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

/**
 * 应用恢复执行的结果：继续挂起则换成新审批单，跑完则把回答并入消息流
 */
function applyResumeResult(res: ApprovalResumeResult) {
  clearPendingApproval()
  if (!res) {
    messages.value.push({ role: 'assistant', content: '恢复执行失败：服务未返回结果' })
    return
  }
  if (res.status === 'waiting_approval') {
    setPendingApproval(res.pendingApproval)
    return
  }
  if (res.status === 'completed') {
    messages.value.push({
      role: 'assistant',
      content: res.message || '（本轮无文本产出）',
      tokens: res.tokens,
      duration: res.duration
    })
    loadSessions()
    return
  }
  const reason = res.errorMessage || res.finishReason || res.status || '未知原因'
  messages.value.push({ role: 'assistant', content: '恢复执行未成功：' + reason })
}

async function resolveApproval(approved: boolean) {
  const target = pendingApproval.value
  if (!target || approvalSubmitting.value) return
  let remark = ''
  if (!approved) {
    // 拒绝原因会作为 tool 消息回喂模型，写清楚它才能换路子继续
    try {
      const { value } = await ElMessageBox.prompt('拒绝后原因会回喂给模型，便于它改用其他方式完成任务', '拒绝审批', {
        inputPlaceholder: '例如：当前会话不需要执行该操作',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写拒绝原因'),
        confirmButtonText: '确认拒绝',
        cancelButtonText: '返回'
      })
      remark = (value || '').trim()
    } catch {
      return
    }
  }
  approvalSubmitting.value = true
  typing.value = true
  try {
    const res = await chatApi.webDecideApproval(target.approvalId, {
      approved,
      resumeToken: target.resumeToken,
      sessionId: sessionId.value || undefined,
      remark: remark || undefined
    })
    applyResumeResult(res)
  } catch (e: any) {
    // 令牌单次生效，重复提交或已超时都不可恢复，收起卡片避免继续误点
    clearPendingApproval()
    messages.value.push({ role: 'assistant', content: '审批处理失败：' + (e?.message || '请稍后重试') })
  } finally {
    approvalSubmitting.value = false
    typing.value = false
    scroll()
  }
}

/** 加载会话下未过期的待审批单（刷新页面 / 切换会话后重建卡片） */
async function loadPendingApproval() {
  if (!sessionId.value) {
    clearPendingApproval()
    return
  }
  try {
    const pending = await chatApi.webPendingApproval(sessionId.value)
    if (pending) {
      setPendingApproval(pending)
    } else {
      clearPendingApproval()
    }
  } catch {
    clearPendingApproval()
  }
}

/* ===== 本地文件工具（File System Access API）===== */
// LOCAL 工具服务端不执行：引擎挂起 run 并推 local_tool_required 帧，
// 浏览器在用户授权目录内执行，结果回传后端续跑；可能连续挂起多次，用循环串行处理。
const localFsSupported = isLocalFsSupported()
const localFsAuthorized = ref(false)
const pendingLocalTool = ref<PendingLocalTool | null>(null)
const localToolRunning = ref(false)
const localToolResumeRunning = ref(false)
const localToolNeedAuth = ref(false)
const localToolFailed = ref(false)
const localToolNotFound = ref(false)
const localToolHint = ref('')
/** 变更类工具（写/改名/删除）预检后的二次确认方案；非空表示正在等待用户允许/拒绝 */
const localToolConfirm = ref<LocalToolConfirmPlan | null>(null)
/** 预检阶段标记，避免与执行中 spinner 混淆 */
const localToolPlanning = ref(false)

const localToolBusy = computed(() =>
  localToolRunning.value || localToolResumeRunning.value || localToolPlanning.value
)

const LOCAL_TOOL_NAMES: Record<string, string> = {
  local_list_dir: '列出本地文件夹',
  local_read_file: '读取本地文件',
  local_write_file: '写入本地文件',
  local_rename_file: '重命名/移动本地文件',
  local_delete_file: '删除本地文件/文件夹'
}
const localToolDisplayName = computed(() => {
  const name = pendingLocalTool.value?.toolName || pendingLocalTool.value?.tool || ''
  return LOCAL_TOOL_NAMES[name] || name || '未知本地工具'
})
/** 从工具参数里提取模型想操作的目标路径，用于授权卡片给出明确上下文 */
const localToolTargetPath = computed(() => {
  const raw = pendingLocalTool.value?.arguments
  if (!raw) return ''
  try {
    const args = JSON.parse(raw)
    return String(args.path || args.fromPath || '').trim()
  } catch {
    return ''
  }
})
const localToolStatusText = computed(() => {
  if (localToolResumeRunning.value) return '正在回传结果并继续生成…'
  if (localToolRunning.value) return '正在你的电脑上执行文件操作…'
  if (localToolFailed.value) return '本地工具执行失败'
  if (localToolConfirm.value) {
    return localToolConfirm.value.riskLevel === 'high' ? '高风险操作，请确认' : '等待你确认'
  }
  if (localToolPlanning.value) return '正在检查操作…'
  if (localToolNeedAuth.value) return '需要先授权本地文件夹'
  return '等待执行本地文件操作'
})

async function refreshLocalFsAuth() {
  try {
    localFsAuthorized.value = !!(await getRootHandle(false))
  } catch {
    localFsAuthorized.value = false
  }
}

async function authorizeLocalDir() {
  try {
    await authorizeDirectory({ mode: 'readwrite' })
    localFsAuthorized.value = true
    ElMessage.success('本地文件夹授权成功')
    // 若正有挂起的本地工具在等授权，接着进入预检/执行分流
    if (pendingLocalTool.value && localToolNeedAuth.value) {
      prepareLocalTool()
    }
  } catch (e: any) {
    // 用户取消选择时浏览器抛 AbortError，不算错误
    if (e?.name !== 'AbortError') {
      ElMessage.warning('授权失败：' + (e?.message || '请重试'))
    }
  }
}

/** 授权完成后从卡片触发执行 */
async function authorizeAndRunLocal() {
  await authorizeLocalDir()
}

/** 授权目录内找不到目标时，改选另一个文件夹后再执行 */
async function reauthorizeAndRunLocal() {
  await authorizeLocalDir()
}

function setPendingLocalTool(raw: any) {
  if (!raw || !raw.callId || !raw.resumeToken) return
  pendingLocalTool.value = {
    runId: raw.runId,
    callId: raw.callId,
    toolName: raw.toolName || raw.tool,
    tool: raw.tool,
    arguments: raw.arguments,
    resumeToken: raw.resumeToken
  }
  localToolHint.value = ''
  localToolFailed.value = false
  localToolNotFound.value = false
  localToolConfirm.value = null
  localToolNeedAuth.value = !localFsSupported || !localFsAuthorized.value
  if (!localFsSupported) {
    localToolHint.value = '当前浏览器不支持本地文件操作，请使用最新版 Chrome / Edge。'
  } else if (localToolNeedAuth.value) {
    localToolHint.value =
      '浏览器需要你的一次性授权才能读写本地文件。请点击下方按钮，选择包含目标文件的文件夹；文件操作只会在该文件夹内进行。'
  }
  scroll()
  // 已授权则进入预检/执行分流（只读自动执行，写改删先弹二次确认）。
  // 必须延后到下一个宏任务：一轮多个本地工具时，本函数可能由上一个工具的
  // submitLocalResult 响应链式触发，此时 localToolResumeRunning/localToolRunning
  // 尚未被外层 finally 复位，同步调用会被 prepareLocalTool 的忙等守卫直接早退，
  // 导致第二张卡片成为无按钮、不执行的死卡片。
  if (!localToolNeedAuth.value) {
    setTimeout(() => {
      // 延迟期间卡片可能已被终态清理，确认仍是当前挂起工具再预检
      if (pendingLocalTool.value?.callId === raw.callId) {
        prepareLocalTool()
      }
    }, 0)
  }
}

function clearPendingLocalTool() {
  pendingLocalTool.value = null
  localToolHint.value = ''
  localToolNeedAuth.value = false
  localToolFailed.value = false
  localToolNotFound.value = false
  localToolConfirm.value = null
}

/** 标记本地工具失败；若为授权目录内找不到目标，提示用户改选包含目标的文件夹 */
function markLocalFailure(message: string) {
  localToolFailed.value = true
  localToolConfirm.value = null
  localToolHint.value = message
  localToolNotFound.value = /找不到(文件|文件夹)/.test(message)
}

/**
 * 已授权后的统一入口：
 * - 只读工具（列目录/读取）直接执行；
 * - 变更类工具（写/改名/删除）先预检，生成二次确认卡片等待用户允许/拒绝。
 */
async function prepareLocalTool() {
  const target = pendingLocalTool.value
  if (!target || localToolBusy.value) return
  localToolHint.value = ''
  localToolFailed.value = false
  localToolNotFound.value = false
  localToolConfirm.value = null
  try {
    if (!localFsSupported) {
      throw new Error('当前浏览器不支持本地文件操作，请使用最新版 Chrome / Edge')
    }
    if (!(await getRootHandle(false))) {
      localToolNeedAuth.value = true
      localToolHint.value = '该操作需要你先选择并授权一个本地文件夹，文件操作只会在该文件夹内进行。'
      return
    }
    localToolNeedAuth.value = false
    const toolName = target.toolName || target.tool || ''

    // 只读：直接执行
    if (!isMutatingLocalTool(toolName)) {
      await runPendingLocalTool()
      return
    }

    // 变更类：先预检生成确认方案
    localToolPlanning.value = true
    try {
      const plan = await planLocalTool(toolName, target.arguments)
      if (plan) {
        localToolConfirm.value = plan
        scroll()
        return
      }
    } finally {
      localToolPlanning.value = false
    }
    // 预检未给出确认方案（理论上变更类都会给出），退化为直接执行
    await runPendingLocalTool()
  } catch (e: any) {
    markLocalFailure(e?.message || '预检失败')
  } finally {
    localToolPlanning.value = false
    scroll()
  }
}

/** 用户在二次确认卡片点"允许"：真正执行变更类工具 */
async function confirmLocalTool() {
  await runPendingLocalTool()
}

/** 用户在二次确认卡片点"拒绝"：以失败结果回喂模型，让其改道或告知用户 */
async function rejectLocalTool() {
  const target = pendingLocalTool.value
  if (!target || localToolBusy.value) return
  const plan = localToolConfirm.value
  const reason = plan
    ? `用户拒绝了「${plan.title}」操作（未对本地文件做任何改动）。请停止该操作，并据此回复用户；如确有必要，可在向用户说明风险后由用户重新发起。`
    : '用户拒绝了该本地文件操作，未做任何改动。'
  localToolConfirm.value = null
  localToolResumeRunning.value = true
  try {
    await submitLocalResult(target, true, undefined, reason)
  } finally {
    localToolResumeRunning.value = false
    scroll()
  }
}

async function runPendingLocalTool() {
  const target = pendingLocalTool.value
  if (!target || localToolBusy.value) return
  localToolRunning.value = true
  localToolHint.value = ''
  localToolFailed.value = false
  localToolNotFound.value = false
  localToolConfirm.value = null
  try {
    if (!localFsSupported) {
      throw new Error('当前浏览器不支持本地文件操作，请使用最新版 Chrome / Edge')
    }
    const toolName = target.toolName || target.tool || ''
    const output = await executeLocalTool(toolName, target.arguments)
    if ('error' in output) {
      const msg = /^\{.*"success"\s*:\s*false/.test(output.error)
        ? safeExtractError(output.error)
        : output.error
      markLocalFailure(msg)
      return
    }
    await submitLocalResult(target, false, output.result, undefined)
  } catch (e: any) {
    markLocalFailure(e?.message || '执行失败')
  } finally {
    localToolRunning.value = false
    scroll()
  }
}

/** 从失败 JSON 里抽出 error 字段展示 */
function safeExtractError(json: string): string {
  try {
    const parsed = JSON.parse(json)
    return parsed?.error || json
  } catch {
    return json
  }
}

/** 回传本地执行结果并处理恢复产出；连续挂起时循环直到终态 */
async function submitLocalResult(target: PendingLocalTool, failed: boolean,
                                 result?: string, errorMessage?: string) {
  localToolResumeRunning.value = true
  try {
    const res = await chatApi.webLocalToolResult({
      runId: target.runId,
      callId: target.callId,
      resumeToken: target.resumeToken,
      sessionId: sessionId.value || undefined,
      failed,
      result,
      errorMessage
    })
    applyLocalResumeResult(res)
  } catch (e: any) {
    clearPendingLocalTool()
    messages.value.push({ role: 'assistant', content: '本地工具结果回传失败：' + (e?.message || '请重新发起对话') })
  } finally {
    localToolResumeRunning.value = false
    scroll()
  }
}

function applyLocalResumeResult(res: LocalToolResumeResult) {
  if (!res) {
    clearPendingLocalTool()
    messages.value.push({ role: 'assistant', content: '恢复执行失败：服务未返回结果' })
    return
  }
  // 又一个本地工具：换卡片后继续自动执行
  if (res.status === 'waiting_local' && res.pendingLocalTool) {
    setPendingLocalTool(res.pendingLocalTool)
    return
  }
  // 恢复过程中命中审批：交审批卡片处理
  if (res.status === 'waiting_approval' && res.pendingApproval) {
    clearPendingLocalTool()
    setPendingApproval(res.pendingApproval)
    return
  }
  clearPendingLocalTool()
  if (res.status === 'completed') {
    messages.value.push({
      role: 'assistant',
      content: res.message || '（本轮无文本产出）',
      tokens: res.tokens,
      duration: res.duration
    })
    loadSessions()
    return
  }
  const reason = res.errorMessage || res.finishReason || res.status || '未知原因'
  messages.value.push({ role: 'assistant', content: '本地工具恢复执行未成功：' + reason })
}

function ensureUserId() {
  if (!userId.value) {
    const stored = localStorage.getItem('cangjie_user_id')
    if (stored) {
      userId.value = stored
    } else {
      userId.value = crypto.randomUUID()
      localStorage.setItem('cangjie_user_id', userId.value)
    }
  }
}

const subtitle = computed(() =>
  configError.value ? '配置异常' : '在线')
const welcomeTitle = computed(() => (configError.value ? '无法开始对话' : '你好，有什么可以帮你？'))
const welcomeSub = computed(() =>
  configError.value || '我是你的智能助手，支持知识库检索与工具调用')

// 建议问题：从应用配置动态加载，未配置则为空
const suggestionCards = ref<{ icon: string; text: string }[]>([])

function scroll() {
  nextTick(() => scrollAnchor.value?.scrollIntoView({ behavior: 'smooth' }))
}

function sourceText(s: Record<string, any>) {
  return s?.content || s?.text || s?.title || s?.documentName || JSON.stringify(s)
}

// 后端 sources 可能是数组，也可能是 JSON 字符串（历史消息）
function parseSources(raw: any): Record<string, any>[] {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function toggleSources(m: any) {
  m._sourcesCollapsed = !m._sourcesCollapsed
}

function formatTime(t: string) {
  if (!t) return ''
  try {
    const d = new Date(t)
    const now = new Date()
    if (d.toDateString() === now.toDateString()) {
      return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    }
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  } catch {
    return t
  }
}

/** 流式输出时每个 token 都会重新渲染，缓存同内容的 HTML 结果避免重复解析 */
const mdCache = new Map<string, string>()
const MD_CACHE_MAX = 200

function renderMarkdown(text: string): string {
  if (!text) return ''
  const cached = mdCache.get(text)
  if (cached !== undefined) return cached
  // 流式过程中代码围栏可能尚未闭合，补齐后再解析，保证代码块实时渲染
  const fenceCount = (text.match(/```/g) || []).length
  const source = fenceCount % 2 === 1 ? text + '\n```' : text
  const html = safeMarkdown(source)
  if (mdCache.size >= MD_CACHE_MAX) mdCache.clear()
  mdCache.set(text, html)
  return html
}

function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

/** 点击输入框容器任意空白处都聚焦到文本框（发送按钮自行处理点击，不拦截） */
function focusInput(e: MouseEvent) {
  if ((e.target as HTMLElement).closest('.send-btn')) return
  if (!inputRef.value?.disabled) {
    inputRef.value?.focus()
  }
}

function useSuggestion(s: { text: string }) {
  input.value = s.text
  send()
}

function newChat() {
  sessionId.value = ''
  messages.value = []
  input.value = ''
  clearPendingApproval()
  clearPendingLocalTool()
}

async function send() {
  const text = input.value.trim()
  if (!text || configError.value) return
  ensureUserId()
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  if (inputRef.value) inputRef.value.style.height = 'auto'
  scroll()
  typing.value = true
  try {
    const stream = chatApi.webSendStream({
      applicationId: applicationId.value,
      message: text,
      sessionId: sessionId.value || undefined,
      userId: userId.value
    })
    let assistantMsg: Msg | null = null
    let pendingSources: any[] | null = null
    for await (const { event, data: chunk } of stream) {
      if (event === 'init') {
        if (chunk?.sessionId) sessionId.value = chunk.sessionId
        if (chunk?.sources) pendingSources = chunk.sources
        continue
      }
      // 高风险工具挂起：本轮回答到此为止，产出改由审批决策接口同步返回
      if (event === 'approval_required') {
        setPendingApproval(chunk)
        break
      }
      // 本地工具挂起：由浏览器在授权目录内执行后回传结果续跑
      if (event === 'local_tool_required') {
        setPendingLocalTool(chunk)
        break
      }
      if (event === 'done' || event === 'error') {
        if (chunk?.error) {
          if (assistantMsg) {
            // 已有部分流式内容：保留内容，错误单独展示（后端也会把部分内容落库）
            assistantMsg.error = chunk.error
          } else {
            // 流未产生任何内容就报错（如余额不足）
            messages.value.push({ role: 'assistant', content: '', error: chunk.error })
          }
        }
        break
      }
      if (event === 'message' && chunk) {
        if (!assistantMsg) {
          streamingStarted.value = true
          assistantMsg = reactive<Msg>({ role: 'assistant', content: '', _sourcesCollapsed: true, sources: pendingSources || undefined })
          messages.value.push(assistantMsg)
        }
        if (chunk.delta) assistantMsg.content += chunk.delta
      }
      scroll()
    }
    loadSessions()
  } catch (e: any) {
    const last = messages.value[messages.value.length - 1]
    if (last?.role === 'assistant') {
      last.error = e?.message || '网络异常，请稍后重试'
    } else {
      messages.value.push({ role: 'assistant', content: '', error: '回复失败：' + (e?.message || '请稍后重试') })
    }
  } finally {
    typing.value = false
    streamingStarted.value = false
    scroll()
  }
}

async function loadSessions() {
  if (!applicationId.value) return
  ensureUserId()
  try {
    sessions.value = await chatApi.webSessions(applicationId.value, userId.value)
  } catch {
    sessions.value = []
  }
}

async function deleteSession(s: any) {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条对话记录吗？删除后不可恢复。',
      '删除确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await chatApi.webDeleteSession(s.sessionId)
    sessions.value = sessions.value.filter((x: any) => x.sessionId !== s.sessionId)
    if (sessionId.value === s.sessionId) {
      sessionId.value = ''
      messages.value = []
      clearPendingLocalTool()
    }
    ElMessage.success('对话已删除')
  } catch (e: any) {
    ElMessage.warning('删除失败：' + (e?.message || '未知错误'))
  }
}

async function openSession(s: any) {
  // 小屏时点击会话后收起侧栏，大屏保持常驻
  if (window.innerWidth < 1024) {
    showHistory.value = false
  }
  sessionId.value = s.sessionId
  messages.value = []
  clearPendingLocalTool()
  try {
    const history = await chatApi.webMessages(s.sessionId)
    messages.value = (history || []).map((m: any) => ({
      role: m.role === 'user' ? 'user' : 'assistant',
      content: m.content,
      sources: parseSources(m.retrievalSources ?? m.sources),
      _sourcesCollapsed: true,
      tokens: m.tokens,
      duration: m.duration
    }))
    scroll()
  } catch {
    ElMessage.warning('加载会话消息失败')
  }
  // 该会话可能停在待审批状态，重建卡片否则挂起的 run 无从恢复
  loadPendingApproval()
}

onMounted(() => {
  const params = new URLSearchParams(location.search)
  applicationId.value = params.get('app') || ''
  // 优先使用 URL 参数中的 userId（业务方显式传入），否则从 localStorage 读取/生成
  userId.value = params.get('user') || ''
  ensureUserId()
  embedded.value = params.get('embed') === '1' || window.self !== window.top
  const customTitle = params.get('title')
  if (customTitle) {
    title.value = customTitle
    document.title = customTitle
  }

  // 大屏默认常驻对话记录，小屏默认折叠
  showHistory.value = !embedded.value && window.innerWidth >= 1024
  window.addEventListener('resize', onWindowResize)

  if (localFsSupported) {
    refreshLocalFsAuth()
  }

  if (!applicationId.value) {
    configError.value = '缺少 app 参数，请通过后台「智能应用 - 接入方式」获取嵌入地址'
    return
  }
  loadConfig()
  loadSessions()
})

// 加载应用配置（建议问题等）
async function loadConfig() {
  try {
    const cfg = await chatApi.getWebConfig(applicationId.value)
    if (cfg?.name && !new URLSearchParams(location.search).get('title')) {
      title.value = cfg.name
      document.title = cfg.name
    }
    suggestionCards.value = (cfg?.suggestions || [])
      .filter((t: string) => t && t.trim())
      .map((t: string) => ({ icon: '💬', text: t }))
  } catch {
    // 配置加载失败不阻塞对话
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize)
  stopApprovalCountdown()
})

// 屏幕尺寸跨过阈值时自动展开/折叠对话记录（嵌入式不参与）
function onWindowResize() {
  if (embedded.value) return
  if (window.innerWidth >= 1024 && !showHistory.value) {
    showHistory.value = true
  } else if (window.innerWidth < 1024 && showHistory.value) {
    showHistory.value = false
  }
}
</script>

<style lang="scss">
/* CSS Variables - 非 scoped，定义在 :root 上才能全局生效 */
:root {
  --cj-primary: #6366f1;
  --cj-primary-light: #818cf8;
  --cj-primary-bg: #eef2ff;
  --cj-bg: #f8fafc;
  --cj-surface: #ffffff;
  --cj-text: #1e293b;
  --cj-text-secondary: #64748b;
  --cj-text-muted: #94a3b8;
  --cj-border: #e2e8f0;
  --cj-border-light: #f1f5f9;
  --cj-radius: 16px;
  --cj-radius-sm: 10px;
  --cj-shadow-sm: 0 1px 3px rgba(0,0,0,0.04);
  --cj-shadow: 0 4px 24px rgba(0,0,0,0.06);
  --cj-transition: 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
</style>

<style lang="scss" scoped>

/* ===== Layout ===== */
.chat-app {
  height: 100vh;
  display: flex;
  background: var(--cj-bg);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  color: var(--cj-text);
  overflow: hidden;
}

/* ===== Sidebar ===== */
.sidebar {
  width: 300px;
  background: var(--cj-surface);
  border-right: 1px solid var(--cj-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: 10;
}
.sidebar-header {
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--cj-border-light);
}
.sidebar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--cj-text);
}
.new-chat-btn {
  margin: 16px 20px;
  padding: 10px 16px;
  background: var(--cj-primary);
  color: #fff;
  border: none;
  border-radius: var(--cj-radius-sm);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: var(--cj-transition);
  &:hover { opacity: 0.9; transform: translateY(-1px); }
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
}
.session-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--cj-radius-sm);
  cursor: pointer;
  transition: var(--cj-transition);
  margin-bottom: 4px;
  position: relative;
  &:hover { background: var(--cj-border-light); }
  &:hover .session-delete-btn { opacity: 1; }
  &.active { background: var(--cj-primary-bg); }
}
.session-delete-btn {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cj-text-muted);
  opacity: 0;
  transition: var(--cj-transition);
  &:hover { background: #fee2e2; color: #ef4444; }
}
.session-icon {
  color: var(--cj-text-muted);
  flex-shrink: 0;
  .active & { color: var(--cj-primary); }
}
.session-info { min-width: 0; }
.session-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--cj-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-time {
  font-size: 12px;
  color: var(--cj-text-muted);
  margin-top: 2px;
}
.empty-sessions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 20px;
  color: var(--cj-text-muted);
  font-size: 13px;
}

/* ===== Slide Transition ===== */
.slide-enter-active, .slide-leave-active { transition: all 0.3s ease; }
.slide-enter-from, .slide-leave-to { transform: translateX(-100%); opacity: 0; }

/* ===== Main ===== */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* ===== Header ===== */
.chat-header {
  padding: 16px 24px;
  background: var(--cj-surface);
  border-bottom: 1px solid var(--cj-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
.logo-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--cj-primary), #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.title {
  font-size: 15px;
  font-weight: 600;
  color: var(--cj-text);
}
.status-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #cbd5e1;
  &.online { background: #22c55e; box-shadow: 0 0 6px rgba(34,197,94,0.4); }
}
.subtitle {
  font-size: 12px;
  color: var(--cj-text-muted);
}
.header-actions {
  display: flex;
  gap: 8px;
}

/* ===== Icon Buttons ===== */
.icon-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--cj-radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cj-text-secondary);
  transition: var(--cj-transition);
  &:hover { background: var(--cj-border-light); color: var(--cj-text); }
}
.action-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--cj-border);
  background: var(--cj-surface);
  border-radius: var(--cj-radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cj-text-secondary);
  transition: var(--cj-transition);
  &:hover { border-color: var(--cj-primary); color: var(--cj-primary); background: var(--cj-primary-bg); }
}

/* ===== Config Error ===== */
.config-error {
  margin: 12px 24px 0;
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--cj-radius-sm);
  color: #dc2626;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ===== Chat Body ===== */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb { background: var(--cj-border); border-radius: 3px; }
}
.embedded .chat-body { padding: 16px; }

/* ===== Welcome ===== */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px 40px;
  text-align: center;
}
.welcome-icon {
  width: 80px;
  height: 80px;
  border-radius: 24px;
  background: linear-gradient(135deg, var(--cj-primary-bg), #ede9fe);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cj-primary);
  margin-bottom: 24px;
}
.welcome-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--cj-text);
  margin: 0;
}
.welcome-sub {
  font-size: 14px;
  color: var(--cj-text-muted);
  margin: 8px 0 0;
  line-height: 1.6;
}
.suggestions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-top: 32px;
  max-width: 480px;
  width: 100%;
}
.suggestion-card {
  padding: 14px 16px;
  background: var(--cj-surface);
  border: 1px solid var(--cj-border);
  border-radius: var(--cj-radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--cj-text);
  text-align: left;
  transition: var(--cj-transition);
  &:hover {
    border-color: var(--cj-primary-light);
    background: var(--cj-primary-bg);
    transform: translateY(-2px);
    box-shadow: var(--cj-shadow);
  }
}
.suggestion-icon { font-size: 18px; flex-shrink: 0; pointer-events: none; }
.suggestion-text { line-height: 1.4; }

/* ===== Messages ===== */
.message-row {
  display: flex;
  gap: 14px;
  margin-bottom: 24px;
  align-items: flex-start;
  animation: fadeIn 0.3s ease;
  &.user {
    flex-direction: row-reverse;
  }
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.message-avatar { flex-shrink: 0; }
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.user-avatar {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
}
.ai-avatar {
  background: linear-gradient(135deg, var(--cj-primary), #8b5cf6);
}
.message-content {
  flex: 1;
  min-width: 0;
  max-width: 720px;
}
.message-role {
  font-size: 12px;
  font-weight: 600;
  color: var(--cj-text-muted);
  margin-bottom: 6px;
}
.message-bubble {
  padding: 14px 18px;
  background: var(--cj-surface);
  border: 1px solid var(--cj-border);
  border-radius: var(--cj-radius);
  border-top-left-radius: 4px;
  line-height: 1.7;
  box-shadow: var(--cj-shadow-sm);
}
.user-bubble {
  background: linear-gradient(135deg, var(--cj-primary), #8b5cf6);
  color: #fff;
  border: none;
  border-radius: var(--cj-radius);
  border-top-right-radius: 4px;
}
.message-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(245, 108, 108, 0.1);
  border: 1px solid rgba(245, 108, 108, 0.35);
  color: #d94848;
  font-size: 13px;
  line-height: 1.5;
  svg {
    flex-shrink: 0;
    margin-top: 2px;
  }
  &:only-child,
  .message-text:empty + & {
    margin-top: 0;
  }
}
.message-text {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;

  :deep(p) {
    margin: 0 0 8px;
    &:last-child { margin-bottom: 0; }
  }
  :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
    margin: 16px 0 8px;
    line-height: 1.4;
    font-weight: 600;
    &:first-child { margin-top: 0; }
  }
  :deep(h1) { font-size: 20px; }
  :deep(h2) { font-size: 18px; }
  :deep(h3) { font-size: 16px; }
  :deep(h4) { font-size: 15px; }

  :deep(ul), :deep(ol) {
    margin: 8px 0;
    padding-left: 22px;
    li { margin: 4px 0; }
  }
  :deep(blockquote) {
    margin: 8px 0;
    padding: 4px 12px;
    border-left: 3px solid var(--border-color, #d9d9d9);
    color: var(--text-secondary, #666);
  }

  :deep(a) {
    color: #4f7cff;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }

  :deep(hr) {
    border: none;
    border-top: 1px solid var(--border-color, #e8e8e8);
    margin: 12px 0;
  }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;
    font-size: 13px;
    display: block;
    overflow-x: auto;
  }
  :deep(th), :deep(td) {
    border: 1px solid var(--border-color, #e0e0e0);
    padding: 6px 10px;
    text-align: left;
  }
  :deep(th) {
    background: rgba(0,0,0,0.03);
    font-weight: 600;
  }

  :deep(code) {
    background: rgba(0,0,0,0.06);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 13px;
    font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
  }
  :deep(pre) {
    margin: 10px 0;
    padding: 0;
    border-radius: 8px;
    background: #282c34;
    overflow: hidden;
    code {
      display: block;
      padding: 12px 14px;
      overflow-x: auto;
      background: #282c34;
      border-radius: 8px;
      font-size: 13px;
      line-height: 1.6;
      font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
    }
  }
  :deep(strong) { font-weight: 600; }
  .user-bubble & {
    :deep(code) { background: rgba(255,255,255,0.2); }
    :deep(pre),
    :deep(pre code) {
      background: rgba(0,0,0,0.28);
    }
    :deep(a) { color: #fff; text-decoration: underline; }
    :deep(th) { background: rgba(255,255,255,0.12); }
    :deep(blockquote) {
      border-color: rgba(255,255,255,0.4);
      color: rgba(255,255,255,0.85);
    }
  }
}

/* ===== Sources ===== */
.sources-card {
  margin-top: 10px;
  padding: 12px 14px;
  background: var(--cj-border-light);
  border-radius: var(--cj-radius-sm);
  border: 1px solid var(--cj-border);
}
.sources-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--cj-text-secondary);
  cursor: pointer;
  user-select: none;
  padding: 2px 0;
  &:hover { color: var(--cj-text); }
}
.sources-header-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.chevron {
  transition: transform 0.2s ease;
  &.open { transform: rotate(180deg); }
}
.sources-body {
  overflow: hidden;
}
.collapse-enter-active, .collapse-leave-active {
  transition: all 0.2s ease;
}
.collapse-enter-from, .collapse-leave-to {
  opacity: 0;
  max-height: 0;
}
.collapse-enter-to, .collapse-leave-from {
  opacity: 1;
  max-height: 600px;
}
.source-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  color: var(--cj-text-secondary);
  line-height: 1.6;
  margin-bottom: 4px;
  &:last-child { margin-bottom: 0; }
}
.source-badge {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--cj-primary-bg);
  color: var(--cj-primary);
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}
.source-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ===== Meta ===== */
.message-meta {
  font-size: 11px;
  color: var(--cj-text-muted);
  margin-top: 6px;
  display: flex;
  gap: 4px;
}
.user .message-meta {
  justify-content: flex-end;
}
.meta-dot { opacity: 0.5; }

/* ===== Typing Indicator ===== */
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 16px 20px;
  background: var(--cj-surface);
  border: 1px solid var(--cj-border);
  border-radius: var(--cj-radius);
  border-top-left-radius: 4px;
  width: fit-content;
}
.typing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cj-primary-light);
  animation: typingBounce 1.4s infinite ease-in-out;
  &:nth-child(2) { animation-delay: 0.16s; }
  &:nth-child(3) { animation-delay: 0.32s; }
}
@keyframes typingBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ===== 审批卡片 ===== */
.approval-avatar {
  background: #fffbeb;
  color: #d97706;
  border: 1px solid #fde68a;
}
.approval-card {
  max-width: 460px;
  padding: 14px 16px;
  background: var(--cj-surface);
  border: 1px solid #fde68a;
  border-left: 3px solid #f59e0b;
  border-radius: var(--cj-radius);
  border-top-left-radius: 4px;
  box-shadow: var(--cj-shadow-sm);
  &.is-expired {
    border-left-color: var(--cj-border);
    opacity: 0.7;
  }
}
.approval-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.approval-risk {
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  &.risk-low { background: var(--cj-primary-bg); color: var(--cj-primary); }
  &.risk-medium { background: #fef3c7; color: #b45309; }
  &.risk-high { background: #fee2e2; color: #b91c1c; }
}
.approval-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--cj-text);
}
.approval-count {
  margin-left: auto;
  font-size: 11px;
  color: var(--cj-text-muted);
  font-variant-numeric: tabular-nums;
}
.approval-desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--cj-text-secondary);
}
.approval-tool {
  padding: 1px 5px;
  background: var(--cj-border-light);
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: var(--cj-text);
}
.approval-type { color: var(--cj-text-muted); }
.approval-reason {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--cj-text-muted);
}
.approval-args {
  margin-top: 10px;
  border: 1px solid var(--cj-border);
  border-radius: var(--cj-radius-sm);
  overflow: hidden;
}
.approval-args-label {
  padding: 6px 10px;
  background: var(--cj-border-light);
  font-size: 11px;
  font-weight: 600;
  color: var(--cj-text-secondary);
}
.approval-args-body {
  margin: 0;
  padding: 10px;
  max-height: 160px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--cj-text);
  white-space: pre-wrap;
  word-break: break-all;
}
.approval-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
.approval-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--cj-radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: var(--cj-transition);
  &:disabled { opacity: 0.55; cursor: not-allowed; }
}
.approval-btn.deny {
  background: var(--cj-surface);
  border: 1px solid var(--cj-border);
  color: var(--cj-text-secondary);
  &:not(:disabled):hover { border-color: #fca5a5; color: #b91c1c; }
}
.approval-btn.allow {
  background: var(--cj-primary);
  border: 1px solid var(--cj-primary);
  color: #fff;
  &:not(:disabled):hover { opacity: 0.9; }
}
.approval-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: approvalSpin 0.7s linear infinite;
}
@keyframes approvalSpin {
  to { transform: rotate(360deg); }
}

/* ===== 本地工具卡片 ===== */
.local-avatar {
  background: linear-gradient(135deg, #0ea5e9, #2563eb) !important;
}
.local-tool-card {
  max-width: 460px;
  padding: 14px 16px;
  background: var(--cj-surface);
  border: 1px solid #bae6fd;
  border-left: 3px solid #0ea5e9;
  border-radius: var(--cj-radius);
  border-top-left-radius: 4px;
  box-shadow: var(--cj-shadow-sm);
}
.local-tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0369a1;
}
.local-tool-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--cj-text);
}
.local-tool-desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--cj-text-secondary);
}
.local-tool-name {
  padding: 1px 5px;
  background: var(--cj-border-light);
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: var(--cj-text);
}
.local-tool-target {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
  color: var(--cj-text-secondary);
  min-width: 0;
}
.local-tool-target-label { flex-shrink: 0; }
.local-tool-target-path {
  padding: 2px 6px;
  background: var(--cj-border-light);
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: var(--cj-text);
  word-break: break-all;
}
.local-tool-args {
  margin-top: 10px;
  border: 1px solid var(--cj-border);
  border-radius: var(--cj-radius-sm);
  overflow: hidden;
}
.local-tool-args-label {
  padding: 6px 10px;
  background: var(--cj-border-light);
  font-size: 11px;
  font-weight: 600;
  color: var(--cj-text-secondary);
  cursor: pointer;
  user-select: none;
}
.local-tool-args-body {
  margin: 0;
  padding: 10px;
  max-height: 160px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--cj-text);
  white-space: pre-wrap;
  word-break: break-all;
}
.local-tool-hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #b45309;
}
.local-tool-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
.local-tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--cj-radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: var(--cj-transition);
  &:disabled { opacity: 0.55; cursor: not-allowed; }
  &.primary {
    background: var(--cj-primary);
    border: 1px solid var(--cj-primary);
    color: #fff;
    &:not(:disabled):hover { opacity: 0.9; }
  }
  &.ghost {
    background: transparent;
    border: 1px solid var(--cj-border);
    color: var(--cj-text-secondary);
    &:not(:disabled):hover {
      border-color: var(--cj-text-secondary);
      color: var(--cj-text);
    }
  }
  &.danger {
    background: #dc2626;
    border: 1px solid #dc2626;
    color: #fff;
    &:not(:disabled):hover { background: #b91c1c; border-color: #b91c1c; }
  }
}

/* ===== 变更类本地操作二次确认 ===== */
.local-confirm {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: var(--cj-radius-sm);
  border: 1px solid;
  &.normal {
    background: var(--cj-primary-rgb, rgba(59, 130, 246, 0.06));
    border-color: color-mix(in srgb, var(--cj-primary) 35%, transparent);
  }
  &.danger {
    background: #fef2f2;
    border-color: #fecaca;
  }
}
.local-confirm-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  .local-confirm.danger & { color: #b91c1c; }
  .local-confirm.normal & { color: var(--cj-text); }
}
.local-confirm-warnings {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--cj-text-secondary);
  .local-confirm.danger & { color: #991b1b; }
}
.local-confirm .local-tool-actions { margin-top: 10px; margin-bottom: 0; }
.local-tool-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(14, 165, 233, 0.25);
  border-top-color: #0ea5e9;
  border-radius: 50%;
  animation: approvalSpin 0.7s linear infinite;
}

/* ===== Footer ===== */
.chat-footer {
  padding: 16px 24px 20px;
  background: var(--cj-surface);
  border-top: 1px solid var(--cj-border);
  flex-shrink: 0;
}
.embedded .chat-footer { padding: 12px 16px 16px; }
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  background: var(--cj-bg);
  border: 2px solid var(--cj-border);
  border-radius: var(--cj-radius);
  padding: 8px 8px 8px 16px;
  transition: var(--cj-transition);
  &:focus-within {
    border-color: var(--cj-primary-light);
    box-shadow: 0 0 0 3px rgba(99,102,241,0.1);
  }
}
.chat-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 14px;
  line-height: 1.6;
  color: var(--cj-text);
  resize: none;
  outline: none;
  font-family: inherit;
  /* 与发送按钮等高，单行时输入框内部不留点击空白带 */
  min-height: 40px;
  max-height: 160px;
  &::placeholder { color: var(--cj-text-muted); }
  &:disabled { opacity: 0.6; }
}
.send-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 12px;
  background: var(--cj-border);
  color: var(--cj-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: var(--cj-transition);
  &.active {
    background: var(--cj-primary);
    color: #fff;
    &:hover { opacity: 0.9; transform: scale(1.05); }
  }
  &:disabled { cursor: not-allowed; }
}
.footer-hint {
  text-align: center;
  font-size: 11px;
  color: var(--cj-text-muted);
  margin-top: 8px;
  opacity: 0.7;
}

/* ===== Scroll Anchor ===== */
.scroll-anchor { height: 1px; }

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    box-shadow: var(--cj-shadow);
  }
  .suggestions {
    grid-template-columns: 1fr;
  }
  .chat-header { padding: 12px 16px; }
  .chat-body { padding: 16px; }
  .chat-footer { padding: 12px 16px 16px; }
  .message-content { max-width: calc(100% - 50px); }
}
</style>
