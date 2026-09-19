<template>
  <div class="application-view">
    <el-card>
      <template #header>
        <QueryBar :loading="loading" @search="reload" @reset="resetQuery">
          <el-input v-model="keyword" placeholder="搜索应用..." clearable style="width: 200px"
                    @keyup.enter="reload">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 140px">
            <el-option v-for="t in appTypes" :key="t.code" :label="t.label" :value="t.code" />
          </el-select>
          <template #extra>
            <el-button type="primary" @click="openCreate">
              <el-icon><Plus /></el-icon> 新建应用
            </el-button>
          </template>
        </QueryBar>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <div class="app-name">
              <div class="app-avatar">{{ (row.name || '?').slice(0, 1) }}</div>
              <div>
                <div>{{ row.name }}</div>
                <div class="app-desc">{{ row.description || '-' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.type)">{{ label(appTypes, row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ modelName(row.modelId) }}</template>
        </el-table-column>
        <el-table-column label="知识库" width="90" align="center">
          <template #default="{ row }">{{ countJson(row.knowledgeBaseIds) }}</template>
        </el-table-column>
        <el-table-column label="记忆" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.memoryEnabled ? 'success' : 'info'">
              {{ row.memoryEnabled ? '开' : '关' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="RAG 模式" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.ragMode === 'agentic' ? 'primary' : 'info'" effect="plain">
              {{ row.ragMode === 'agentic' ? 'Agentic' : 'Simple' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'published' ? 'success' : 'info'">
              {{ row.status === 'published' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="350" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="warning" @click="handlePublish(row)">发布</el-button>
            <el-button size="small" link type="primary" @click="openVersions(row)">版本</el-button>
            <el-button size="small" link type="success" :disabled="!row.apikey" @click="openAccess(row)">接入</el-button>
            <el-button size="small" link :disabled="!row.apikey" @click="openChat(row)">试聊</el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <DataPager v-model:current-page="pageNum" v-model:page-size="pageSize"
                 :total="total" @change="loadList" />
    </el-card>

    <!-- 创建/编辑 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑应用' : '新建应用'" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-divider content-position="left">基础信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="应用名称" prop="name"><el-input v-model="form.name" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应用类型" prop="type">
              <el-select v-model="form.type" style="width:100%" @change="onTypeChange">
                <el-option v-for="t in appTypes" :key="t.code" :label="t.label" :value="t.code" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>

        <!-- 工作流应用：流程全部在工作流画布中配置，这里只保留基础信息 -->
        <el-alert v-if="form.type === 'workflow'" type="info" :closable="false" show-icon
                  title="工作流应用：业务流程请在「工作流」模块中使用节点编排（知识库/LLM/工具等），发布后对外仅走工作流引擎，下方对话能力配置不生效。" />
        <template v-if="form.type !== 'workflow'">
          <el-divider content-position="left">核心能力</el-divider>
          <el-form-item label="对话模型">
            <el-select v-model="form.modelId" placeholder="选择模型" clearable filterable style="width:100%">
              <el-option v-for="m in models" :key="m.id" :label="m.name + ' (' + m.modelName + ')'" :value="m.id" />
            </el-select>
            <div class="form-hint">不选择时使用系统默认模型</div>
          </el-form-item>
          <el-form-item label="知识库">
            <el-select v-model="form.knowledgeBaseIds" multiple collapse-tags collapse-tags-tooltip
                       placeholder="可多选，检索时融合" filterable style="width:100%">
              <el-option v-for="k in knowledgeBases" :key="k.id" :label="k.name" :value="k.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.knowledgeBaseIds.length" label="RAG 模式">
            <el-select v-model="form.ragMode" style="width:100%">
              <el-option label="Simple（预处理检索）" value="simple" />
              <el-option label="Agentic（LLM 自主检索）" value="agentic" />
            </el-select>
            <div class="form-hint">Simple：对话前固定注入 topK=5 检索结果；Agentic：LLM 自主决定是否调用检索工具</div>
          </el-form-item>
          <el-form-item label="提示词模板">
            <el-select v-model="form.promptTemplateId" placeholder="可选" clearable filterable style="width:100%">
              <el-option v-for="p in templates" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>

          <el-divider content-position="left">增强能力</el-divider>
          <el-alert v-if="form.type === 'agent'" type="info" :closable="false" show-icon
                    title="Agent 应用：建议绑定技能/工具/规则，让模型在 Function Calling 中自主决策与执行；规则用于约束回答边界。" />
          <el-form-item label="技能">
            <el-select v-model="form.skillIds" multiple collapse-tags collapse-tags-tooltip
                       placeholder="可选" filterable style="width:100%">
              <el-option v-for="s in skills" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="工具">
            <el-select v-model="form.toolIds" multiple collapse-tags collapse-tags-tooltip
                       placeholder="可选" filterable style="width:100%">
              <el-option v-for="t in tools" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="规则">
            <el-select v-model="form.ruleIds" multiple collapse-tags collapse-tags-tooltip
                       placeholder="可选" filterable style="width:100%">
              <el-option v-for="r in rulesList" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-divider content-position="left">对话设置</el-divider>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="启用记忆"><el-switch v-model="form.memoryEnabled" /></el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="最大轮数">
                <el-input-number v-model="form.maxTurns" :min="1" :max="100" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="温度">
                <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" style="width:100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="form-hint" style="margin-top:-8px">记忆：记住用户偏好/背景，跨会话使用；建议仅对需要持续服务的场景（如客服/个人助理）开启</div>

          <el-divider content-position="left">网页体验</el-divider>
          <div class="form-hint" style="margin-top:-8px">仅当应用提供网页聊天入口时有意义；纯 API 调用可全部留空</div>
          <el-form-item label="建议问题">
            <div class="suggestion-editor">
              <div v-for="(_, idx) in form.suggestions" :key="idx" class="suggestion-row">
                <el-input v-model="form.suggestions[idx]" placeholder="输入建议问题，如：帮我介绍一下你们的产品" />
                <el-button :icon="Delete" circle size="small" @click="removeSuggestion(idx)" />
              </div>
              <el-button type="primary" plain size="small" :icon="Plus" @click="addSuggestion">添加问题</el-button>
              <div class="form-hint">展示在对话入口欢迎页，用户点击可直接提问；不配置则不显示</div>
            </div>
          </el-form-item>
          <el-form-item label="图标"><el-input v-model="form.icon" placeholder="图标名或 URL" /></el-form-item>
          <el-form-item label="额外配置">
            <el-input v-model="form.config" type="textarea" :rows="3" placeholder="JSON，如欢迎语、开场问题等" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 接入方式 -->
    <el-dialog v-model="accessDialog" :title="'接入 - ' + (current?.name || '')" width="720px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="应用 ID">
          <el-input :model-value="current?.id" readonly>
            <template #append><el-button @click="copy(current?.id)">复制</el-button></template>
          </el-input>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input :model-value="current?.apikey" readonly>
            <template #append><el-button @click="copy(current?.apikey)">复制</el-button></template>
          </el-input>
        </el-form-item>
      </el-form>

      <el-tabs model-value="iframe">
        <el-tab-pane label="iframe 嵌入" name="iframe">
          <p class="tab-tip">将下面代码粘贴到你的系统页面即可嵌入对话窗口。</p>
          <pre class="code-block">{{ iframeSnippet }}</pre>
          <el-button size="small" @click="copy(iframeSnippet)">复制代码</el-button>
        </el-tab-pane>
        <el-tab-pane label="独立链接" name="link">
          <p class="tab-tip">直接分享该链接给用户使用。</p>
          <pre class="code-block">{{ chatUrl }}</pre>
          <el-button size="small" @click="copy(chatUrl)">复制链接</el-button>
          <el-button size="small" type="primary" @click="openChat(current)">打开</el-button>
        </el-tab-pane>
        <el-tab-pane label="HTTP API" name="api">
          <p class="tab-tip">通过请求头 X-API-Key 鉴权调用对话接口。</p>
          <pre class="code-block">{{ curlSnippet }}</pre>
          <el-button size="small" @click="copy(curlSnippet)">复制</el-button>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 历史版本 -->
    <el-drawer v-model="versionDrawer" :title="`历史版本 - ${versionApp?.name || ''}`" size="760px" destroy-on-close>
      <el-table :data="versions" v-loading="versionLoading" stripe size="small">
        <el-table-column label="版本" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.version === latestVersion ? 'success' : 'info'">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布说明" prop="publishLog" min-width="150" show-overflow-tooltip />
        <el-table-column label="发布人" prop="publishBy" width="110" show-overflow-tooltip />
        <el-table-column label="发布时间" prop="createTime" width="160" />
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              :title="row.version === latestVersion ? '该版本即最新版本，确定用它覆盖当前配置？' : `确定回滚到 v${row.version}？当前配置将被快照覆盖（不会新增版本）`"
              @confirm="handleRollback(row)">
              <template #reference>
                <el-button size="small" link type="warning">回滚</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm title="确定删除该历史版本？最新版本不可删除" @confirm="handleDeleteVersion(row)">
              <template #reference>
                <el-button size="small" link type="danger" :disabled="row.version === latestVersion">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无历史版本，发布应用后自动生成版本快照" :image-size="70" />
        </template>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Delete } from '@element-plus/icons-vue'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import { copyText } from '@admin/utils/clipboard'
import { applicationApi } from '@admin/api/application-api'
import { modelApi } from '@admin/api/model-api'
import { knowledgeApi } from '@admin/api/knowledge-api'
import { promptApi } from '@admin/api/prompt-api'
import { toolApi } from '@admin/api/tool-api'

const appTypes = [
  { code: 'chat', label: '对话应用' },
  { code: 'agent', label: 'Agent' },
  { code: 'workflow', label: '工作流应用' }
]

const list = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const filterType = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const saving = ref(false)
const current = ref<any>(null)

const models = ref<any[]>([])
const knowledgeBases = ref<any[]>([])
const templates = ref<any[]>([])
const skills = ref<any[]>([])
const rulesList = ref<any[]>([])
const tools = ref<any[]>([])

const showDialog = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()
const defaults = {
  id: '', name: '', description: '', type: 'chat', modelId: '',
  knowledgeBaseIds: [] as string[], promptTemplateId: '',
  skillIds: [] as string[], ruleIds: [] as string[], toolIds: [] as string[],
  memoryEnabled: false, maxTurns: 20, temperature: 0.7, config: '', icon: '',
  ragMode: 'simple',
  suggestions: [] as string[]
}
const form = reactive<Record<string, any>>({ ...defaults })
const rules: FormRules = {
  name: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择应用类型', trigger: 'change' }]
}

function label(dict: { code: string; label: string }[], code: string) {
  return dict.find(d => d.code === code)?.label || code || '-'
}
function typeTag(code: string): any {
  return ({ chat: 'success', agent: 'primary', workflow: 'warning' } as any)[code] || ''
}
function modelName(id?: string) {
  if (!id) return '-'
  return models.value.find(m => m.id === id)?.name || id
}
function parseIds(text?: string): string[] {
  if (!text) return []
  try {
    const arr = JSON.parse(text)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}
function countJson(text?: string) {
  return parseIds(text).length
}

async function loadList() {
  loading.value = true
  try {
    const page = await applicationApi.list({
      keyword: keyword.value, type: filterType.value,
      pageNum: pageNum.value, pageSize: pageSize.value
    })
    list.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

/** 搜索/切换筛选条件时回到第一页，避免停留在无数据的第 N 页 */
function reload() {
  pageNum.value = 1
  loadList()
}

/** 重置筛选条件并重新查询 */
function resetQuery() {
  keyword.value = ''
  filterType.value = ''
  reload()
}

function openCreate() {
  editing.value = false
  Object.assign(form, { ...defaults, knowledgeBaseIds: [], skillIds: [], ruleIds: [], toolIds: [], suggestions: [] })
  showDialog.value = true
}

function openEdit(row: any) {
  editing.value = true
  Object.assign(form, {
    ...defaults,
    id: row.id,
    name: row.name,
    description: row.description || '',
    type: row.type,
    modelId: row.modelId || '',
    // 后端以 JSON 字符串存储，表单使用数组
    knowledgeBaseIds: parseIds(row.knowledgeBaseIds),
    promptTemplateId: row.promptTemplateId || '',
    skillIds: parseIds(row.skillIds),
    ruleIds: parseIds(row.ruleIds),
    toolIds: parseIds(row.toolIds),
    memoryEnabled: !!row.memoryEnabled,
    maxTurns: row.maxTurns ?? 20,
    temperature: row.temperature ?? 0.7,
    ragMode: row.ragMode || 'simple',
    config: row.config || '',
    icon: row.icon || '',
    suggestions: parseIds(row.suggestions)
  })
  showDialog.value = true
}

function addSuggestion() {
  form.suggestions.push('')
}

/**
 * 切换应用类型时清理不适用字段：workflow 不维护对话能力配置
 */
function onTypeChange(type: string) {
  if (type === 'workflow') {
    form.modelId = ''
    form.knowledgeBaseIds = []
    form.promptTemplateId = ''
    form.skillIds = []
    form.ruleIds = []
    form.toolIds = []
    form.memoryEnabled = false
    form.suggestions = []
    form.icon = ''
    form.config = ''
    form.temperature = 0.7
    form.maxTurns = 20
  }
}

function removeSuggestion(idx: number) {
  form.suggestions.splice(idx, 1)
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...payload } = form
    if (editing.value) {
      await applicationApi.update(id, payload)
      ElMessage.success('更新成功')
    } else {
      await applicationApi.create(payload)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  await applicationApi.remove(id)
  ElMessage.success('删除成功')
  loadList()
}

async function handlePublish(row: any) {
  const updated = await applicationApi.publish(row.id)
  ElMessage.success('已发布，API Key 已生成')
  current.value = updated
  loadList()
  if (versionDrawer.value && versionApp.value?.id === row.id) loadVersions()
}

// 历史版本
const versionDrawer = ref(false)
const versionApp = ref<any>(null)
const versions = ref<any[]>([])
const versionLoading = ref(false)
const latestVersion = computed(() => versions.value[0]?.version ?? 0)

async function loadVersions() {
  if (!versionApp.value) return
  versionLoading.value = true
  try {
    // 版本按 version 倒序返回，一次取全量，版本数量通常很少
    const page = await applicationApi.versions(versionApp.value.id, { pageNum: 1, pageSize: 200 })
    versions.value = page?.list || []
  } finally {
    versionLoading.value = false
  }
}

function openVersions(row: any) {
  versionApp.value = row
  versions.value = []
  versionDrawer.value = true
  loadVersions()
}

async function handleRollback(v: any) {
  await applicationApi.rollbackVersion(versionApp.value.id, v.id)
  ElMessage.success(`已回滚到 v${v.version}`)
  loadList()
}

async function handleDeleteVersion(v: any) {
  await applicationApi.deleteVersion(v.id)
  ElMessage.success('版本已删除')
  loadVersions()
}

// 接入方式
const accessDialog = ref(false)
const origin = computed(() => location.origin)
// 开发环境 admin(5173) 与 chat(5174) 是两个独立 Vite 服务，chat 链接需指向 chat 服务；
// 生产环境同域部署（后端映射 /chat/** 静态资源），直接用当前 origin。
// 可通过 VITE_CHAT_ORIGIN 覆盖。
const chatOrigin = computed(() => {
  const envOrigin = import.meta.env.VITE_CHAT_ORIGIN
  if (envOrigin) return envOrigin
  if (location.port === '5173') {
    return `${location.protocol}//${location.hostname}:5174`
  }
  return location.origin
})
const chatUrl = computed(() =>
  `${chatOrigin.value}/chat/?app=${current.value?.id || ''}&title=${encodeURIComponent(current.value?.name || '')}`)
const iframeSnippet = computed(() =>
  `<iframe\n  src="${chatUrl.value}"\n  style="width:420px;height:640px;border:none;border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,.12)"\n  allow="microphone">\n</iframe>`)
const curlSnippet = computed(() =>
  `curl -X POST ${origin.value}/api/open/chat/completions \\\n  -H "Content-Type: application/json" \\\n  -H "Authorization: Bearer ${current.value?.apikey || ''}" \\\n  -d '{"messages":[{"role":"user","content":"你好"}]}'`)

function openAccess(row: any) {
  current.value = row
  accessDialog.value = true
}

function openChat(row: any) {
  current.value = row
  const url = `${chatOrigin.value}/chat/?app=${row.id || ''}&title=${encodeURIComponent(row.name || '')}`
  window.open(url, '_blank')
}

function copy(text?: string) {
  return copyText(text, '复制失败，请手动选择复制')
}

onMounted(async () => {
  loadList()
  const [m, k, t, s, r, tl] = await Promise.allSettled([
    modelApi.options(), knowledgeApi.options(),
    promptApi.template.options(), promptApi.skill.options(), promptApi.rule.options(),
    toolApi.options()
  ])
  if (m.status === 'fulfilled') models.value = m.value
  if (k.status === 'fulfilled') knowledgeBases.value = k.value
  if (t.status === 'fulfilled') templates.value = t.value
  if (s.status === 'fulfilled') skills.value = s.value
  if (r.status === 'fulfilled') rulesList.value = r.value
  if (tl.status === 'fulfilled') tools.value = tl.value
})
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; gap: 12px; }
.app-name { display: flex; align-items: center; gap: 10px; }
.app-avatar {
  width: 34px; height: 34px; border-radius: 9px; flex-shrink: 0;
  background: linear-gradient(135deg, #409eff, var(--cj-primary));
  color: #fff; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.app-desc { font-size: 12px; color: #909399; margin-top: 2px; }
.tab-tip { font-size: 13px; color: #909399; margin: 0 0 10px; }
.code-block {
  background: #1f2d3d; color: #e4e7ed; border-radius: 8px; padding: 14px;
  font-size: 12px; line-height: 1.7; overflow: auto; max-height: 220px;
  white-space: pre-wrap; word-break: break-all; margin: 0 0 12px;
}
.suggestion-editor { width: 100%; }
.suggestion-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.form-hint { font-size: 12px; color: #909399; margin-top: 6px; }
</style>
