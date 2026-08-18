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

        <!-- Typing indicator -->
        <div v-if="typing" class="message-row assistant">
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
        <div class="input-wrapper">
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { chatApi } from '@shared/api/chat-api'

interface Msg {
  role: 'user' | 'assistant'
  content: string
  sources?: Record<string, any>[]
  _sourcesCollapsed?: boolean
  tokens?: number
  duration?: number
}

const messages = ref<Msg[]>([])
const sessions = ref<any[]>([])
const input = ref('')
const typing = ref(false)
const bodyRef = ref<HTMLElement | null>(null)
const scrollAnchor = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const showHistory = ref(false)

const applicationId = ref('')
const apikey = ref('')
const sessionId = ref('')
const embedded = ref(false)
const title = ref('CangJie Chat')
const configError = ref('')

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

function renderMarkdown(text: string): string {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // Bold
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  // Inline code
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  // Line breaks
  html = html.replace(/\n/g, '<br>')
  return html
}

function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function useSuggestion(s: { text: string }) {
  input.value = s.text
  send()
}

function newChat() {
  sessionId.value = ''
  messages.value = []
  input.value = ''
}

async function send() {
  const text = input.value.trim()
  if (!text || configError.value) return
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  if (inputRef.value) inputRef.value.style.height = 'auto'
  scroll()
  typing.value = true
  try {
    const res = await chatApi.send(apikey.value, {
      applicationId: applicationId.value,
      message: text,
      sessionId: sessionId.value || undefined
    })
    if (res?.sessionId) sessionId.value = res.sessionId
    messages.value.push({
      role: 'assistant',
      content: res?.message || '（无回复内容）',
      sources: parseSources(res?.retrievalSources),
      _sourcesCollapsed: true,
      tokens: res?.tokens,
      duration: res?.duration
    })
    loadSessions()
  } catch (e: any) {
    messages.value.push({ role: 'assistant', content: '回复失败：' + (e?.message || '请稍后重试') })
  } finally {
    typing.value = false
    scroll()
  }
}

async function loadSessions() {
  if (!applicationId.value || !apikey.value) return
  try {
    sessions.value = await chatApi.listSessions(apikey.value, applicationId.value)
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
    await chatApi.deleteSession(apikey.value, s.sessionId)
    sessions.value = sessions.value.filter((x: any) => x.sessionId !== s.sessionId)
    if (sessionId.value === s.sessionId) {
      sessionId.value = ''
      messages.value = []
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
  try {
    const history = await chatApi.listMessages(apikey.value, s.sessionId)
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
}

onMounted(() => {
  const params = new URLSearchParams(location.search)
  applicationId.value = params.get('app') || ''
  apikey.value = params.get('apikey') || ''
  embedded.value = params.get('embed') === '1' || window.self !== window.top
  const customTitle = params.get('title')
  if (customTitle) {
    title.value = customTitle
    document.title = customTitle
  }

  // 大屏默认常驻对话记录，小屏默认折叠
  showHistory.value = !embedded.value && window.innerWidth >= 1024
  window.addEventListener('resize', onWindowResize)

  if (!applicationId.value || !apikey.value) {
    configError.value = '缺少 app 或 apikey 参数，请通过后台「智能应用 - 接入方式」获取嵌入地址'
    return
  }
  loadConfig()
  loadSessions()
})

// 加载应用配置（建议问题等）
async function loadConfig() {
  try {
    const cfg = await chatApi.getConfig(apikey.value, applicationId.value)
    if (cfg?.title && !new URLSearchParams(location.search).get('title')) {
      title.value = cfg.title
      document.title = cfg.title
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
.suggestion-icon { font-size: 18px; flex-shrink: 0; }
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
.message-text {
  font-size: 14px;
  word-break: break-word;
  :deep(code) {
    background: rgba(0,0,0,0.06);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 13px;
    font-family: 'SF Mono', 'Fira Code', monospace;
  }
  :deep(strong) { font-weight: 600; }
  .user-bubble & :deep(code) { background: rgba(255,255,255,0.2); }
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
  min-height: 24px;
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
