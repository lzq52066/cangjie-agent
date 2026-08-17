<template>
  <div class="chat-app" :class="{ embedded }">
    <div class="chat-header">
      <div class="brand">
        <div class="logo">C</div>
        <div>
          <div class="title">{{ title }}</div>
          <div class="subtitle">{{ subtitle }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button v-if="sessions.length" link @click="showHistory = true">历史会话</el-button>
        <el-button link type="primary" @click="newChat">新对话</el-button>
      </div>
    </div>

    <el-alert v-if="configError" type="warning" :closable="false" show-icon :title="configError" />

    <el-scrollbar class="chat-body" ref="bodyRef">
      <div v-if="messages.length === 0 && !typing" class="welcome">
        <div class="welcome-emoji">👋</div>
        <div class="welcome-title">{{ welcomeTitle }}</div>
        <div class="welcome-sub">{{ welcomeSub }}</div>
      </div>
      <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
        <div class="avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
        <div class="bubble-wrap">
          <div class="bubble">{{ m.content }}</div>
          <div v-if="m.sources?.length" class="sources">
            <div class="sources-title">引用来源 {{ m.sources.length }} 条</div>
            <div v-for="(s, si) in m.sources" :key="si" class="source-item">
              <span class="source-index">{{ si + 1 }}</span>
              <span class="source-text">{{ sourceText(s) }}</span>
            </div>
          </div>
          <div v-if="m.duration != null" class="meta">
            {{ m.duration }} ms<template v-if="m.tokens"> · {{ m.tokens }} tokens</template>
          </div>
        </div>
      </div>
      <div v-if="typing" class="msg-row assistant">
        <div class="avatar">AI</div>
        <div class="bubble typing">
          <span></span><span></span><span></span>
        </div>
      </div>
    </el-scrollbar>

    <div class="chat-footer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="1"
        :disabled="!!configError || typing"
        placeholder="输入消息，回车发送 / Shift+回车换行"
        resize="none"
        @keydown.enter.exact.prevent="send"
        size="large"
      />
      <el-button type="primary" :disabled="!input.trim() || typing || !!configError" @click="send">
        <el-icon><Promotion /></el-icon>
      </el-button>
    </div>

    <el-drawer v-model="showHistory" title="历史会话" size="360px">
      <div v-for="s in sessions" :key="s.sessionId" class="session-item"
           :class="{ active: s.sessionId === sessionId }" @click="openSession(s)">
        <div class="session-title">{{ s.title || '未命名会话' }}</div>
        <div class="session-time">{{ s.updateTime || s.createTime }}</div>
      </div>
      <el-empty v-if="sessions.length === 0" description="暂无历史会话" :image-size="70" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { chatApi } from '@shared/api/chat-api'

interface Msg {
  role: 'user' | 'assistant'
  content: string
  sources?: Record<string, any>[]
  tokens?: number
  duration?: number
}

const messages = ref<Msg[]>([])
const sessions = ref<any[]>([])
const input = ref('')
const typing = ref(false)
const bodyRef = ref<any>(null)
const showHistory = ref(false)

const applicationId = ref('')
const apikey = ref('')
const sessionId = ref('')
const embedded = ref(false)
const title = ref('CangJie Chat')
const configError = ref('')

const subtitle = computed(() =>
  configError.value ? '未正确配置' : '智能助手正在为您服务')
const welcomeTitle = computed(() => (configError.value ? '无法开始对话' : '有什么可以帮你？'))
const welcomeSub = computed(() =>
  configError.value || '发送消息开始对话，支持知识库检索与工具调用')

function scroll() {
  nextTick(() => bodyRef.value?.setScrollTop?.(999999))
}

function sourceText(s: Record<string, any>) {
  return s?.content || s?.text || s?.title || s?.documentName || JSON.stringify(s)
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
      sources: res?.retrievalSources || [],
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

async function openSession(s: any) {
  showHistory.value = false
  sessionId.value = s.sessionId
  messages.value = []
  try {
    const history = await chatApi.listMessages(apikey.value, s.sessionId)
    messages.value = (history || []).map((m: any) => ({
      role: m.role === 'user' ? 'user' : 'assistant',
      content: m.content,
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
  if (customTitle) title.value = customTitle

  if (!applicationId.value || !apikey.value) {
    configError.value = '缺少 app 或 apikey 参数，请通过后台「智能应用 - 接入方式」获取嵌入地址'
    return
  }
  loadSessions()
})
</script>

<style lang="scss" scoped>
.chat-app {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}
.chat-header {
  padding: 14px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.embedded .chat-header { padding: 10px 14px; }
.brand { display: flex; align-items: center; gap: 12px; }
.logo {
  width: 40px; height: 40px; border-radius: 12px;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
}
.title { font-weight: 600; color: #303133; }
.subtitle { font-size: 12px; color: #909399; margin-top: 2px; }
.chat-body { flex: 1; padding: 20px; }
.embedded .chat-body { padding: 14px; }
.welcome { padding: 80px 20px; text-align: center; }
.welcome-emoji { font-size: 64px; }
.welcome-title { font-size: 22px; font-weight: 600; margin-top: 16px; }
.welcome-sub { color: #909399; margin-top: 8px; line-height: 1.6; }
.msg-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  align-items: flex-start;
}
.msg-row.user { flex-direction: row-reverse; }
.bubble-wrap { max-width: 74%; }
.msg-row.user .bubble-wrap { display: flex; flex-direction: column; align-items: flex-end; }
.avatar {
  width: 36px; height: 36px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 14px; font-weight: 600; flex-shrink: 0;
  background: #909399;
}
.msg-row.user .avatar { background: linear-gradient(135deg, #409eff, #67c23a); }
.msg-row.assistant .avatar { background: linear-gradient(135deg, #67c23a, #409eff); }
.bubble {
  padding: 12px 16px;
  border-radius: 12px;
  background: #fff;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.msg-row.user .bubble {
  background: linear-gradient(135deg, #e1f3d8, #c2e7b0);
  color: #1f3a14;
}
.sources {
  margin-top: 8px; padding: 10px 12px; background: #fff;
  border: 1px dashed #dcdfe6; border-radius: 10px;
}
.sources-title { font-size: 12px; color: #909399; margin-bottom: 6px; }
.source-item { display: flex; gap: 8px; font-size: 12px; color: #606266; line-height: 1.6; margin-bottom: 4px; }
.source-index {
  flex-shrink: 0; width: 16px; height: 16px; border-radius: 50%;
  background: #ecf5ff; color: #409eff; font-size: 11px;
  display: flex; align-items: center; justify-content: center; margin-top: 2px;
}
.source-text {
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta { font-size: 11px; color: #c0c4cc; margin-top: 6px; }
.bubble.typing span {
  display: inline-block; width: 6px; height: 6px;
  background: #909399; border-radius: 50%;
  margin: 0 2px;
  animation: bounce 1.2s infinite ease-in-out;
}
.bubble.typing span:nth-child(2) { animation-delay: .15s; }
.bubble.typing span:nth-child(3) { animation-delay: .3s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(.6); opacity:.5; } 40% { transform: scale(1); opacity:1; } }
.chat-footer {
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.embedded .chat-footer { padding: 12px 14px; }
.session-item {
  padding: 12px 14px; border-radius: 10px; cursor: pointer;
  border: 1px solid #ebeef5; margin-bottom: 10px; transition: .2s;
}
.session-item:hover { background: #f5f7fa; }
.session-item.active { border-color: #67c23a; background: #f0f9eb; }
.session-title { font-size: 14px; color: #303133; }
.session-time { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
