<template>
  <div class="sessions-panel">
    <QueryBar :loading="loading" @search="reload" @reset="resetQuery">
      <el-input v-model="query.keyword" placeholder="标题 / 会话 ID" clearable style="width:200px"
                @keyup.enter="reload" />
      <el-select v-model="query.applicationId" placeholder="全部应用" clearable filterable style="width:180px">
        <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-input v-model="query.userId" placeholder="用户 ID" clearable style="width:150px"
                @keyup.enter="reload" />
      <el-select v-model="query.source" placeholder="全部来源" clearable style="width:130px">
        <el-option v-for="s in sourceOptions" :key="s" :label="sourceLabel(s)" :value="s" />
      </el-select>
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width:120px">
        <el-option label="进行中" value="active" />
        <el-option label="已关闭" value="closed" />
      </el-select>
    </QueryBar>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="开始时间" prop="createTime" width="160" />
      <el-table-column label="会话" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="session-title">{{ row.title || '未命名会话' }}</div>
          <div class="session-id">{{ row.sessionId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="应用" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ appName(row.applicationId) }}</template>
      </el-table-column>
      <el-table-column label="用户" prop="userId" width="120" show-overflow-tooltip />
      <el-table-column label="来源" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ sourceLabel(row.source) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="消息数" prop="messageCount" width="80" align="center" />
      <el-table-column label="Token" prop="tokensUsed" width="90" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '进行中' : '已关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openMessages(row)">消息</el-button>
        </template>
      </el-table-column>
    </el-table>

    <DataPager v-model:current-page="pageNum" v-model:page-size="pageSize"
               :total="total" @change="loadList" />

    <!-- 聊天记录 -->
    <el-drawer v-model="drawer" :title="`聊天记录 - ${current?.title || current?.sessionId || ''}`"
               size="760px" destroy-on-close>
      <template v-if="current">
        <el-descriptions :column="3" border size="small" class="session-desc">
          <el-descriptions-item label="应用">{{ appName(current.applicationId) }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ current.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceLabel(current.source) }}</el-descriptions-item>
          <el-descriptions-item label="消息数">{{ current.messageCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="Token">{{ current.tokensUsed ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ current.status === 'active' ? '进行中' : '已关闭' }}</el-descriptions-item>
          <el-descriptions-item label="会话 ID" :span="3">
            {{ current.sessionId }}
            <!-- 一条链路只有一次请求，整段会话要靠 sessionId 到链路视图里横向展开 -->
            <el-button link type="primary" @click="emit('view-links', current.sessionId)">
              查看链路
            </el-button>
          </el-descriptions-item>
          <el-descriptions-item v-if="current.summary" label="滚动摘要" :span="3">
            {{ current.summary }}
          </el-descriptions-item>
        </el-descriptions>

        <QueryBar :loading="msgLoading" search-text="筛选" @search="reloadMessages" @reset="resetMessageQuery">
          <el-select v-model="msgQuery.role" placeholder="全部角色" clearable style="width:120px">
            <el-option label="用户" value="user" />
            <el-option label="助手" value="assistant" />
            <el-option label="系统" value="system" />
          </el-select>
          <el-select v-model="msgQuery.feedback" placeholder="全部反馈" clearable style="width:120px">
            <el-option label="点赞" value="like" />
            <el-option label="点踩" value="dislike" />
            <el-option label="未反馈" value="none" />
          </el-select>
          <el-input v-model="msgQuery.keyword" placeholder="内容关键词" clearable style="width:180px"
                    @keyup.enter="reloadMessages" />
        </QueryBar>

        <div v-loading="msgLoading" class="msg-list">
          <div v-for="m in messages" :key="m.id" class="msg-item" :class="'msg-' + m.role">
            <div class="msg-meta">
              <el-tag size="small" :type="roleTagType(m.role)">{{ roleLabel(m.role) }}</el-tag>
              <span class="msg-time">{{ m.createTime }}</span>
              <span v-if="m.tokens != null" class="msg-extra">{{ m.tokens }} tokens</span>
              <span v-if="m.duration != null" class="msg-extra">{{ (m.duration / 1000).toFixed(2) }} s</span>
              <el-icon v-if="m.feedback === 'like'" class="fb-like"><Pointer /></el-icon>
              <el-icon v-else-if="m.feedback === 'dislike'" class="fb-dislike"><Delete /></el-icon>
            </div>
            <div class="msg-content">{{ m.content }}</div>
            <div v-if="m.annotation" class="msg-annotation">
              <span class="annotation-label">人工修正（{{ m.annotateBy || '-' }}）：</span>{{ m.annotation }}
            </div>
            <div v-if="m.role === 'assistant' || m.role === 'user'" class="msg-actions">
              <el-button link type="primary" size="small" @click="openImportDialog(m)">加入评测集</el-button>
            </div>
          </div>
          <el-empty v-if="!messages.length && !msgLoading" description="暂无消息" :image-size="70" />
        </div>

        <DataPager v-model:current-page="msgPageNum" v-model:page-size="msgPageSize"
                   :total="msgTotal" layout="total, prev, pager, next" @change="loadMessages" />
      </template>
    </el-drawer>

    <!-- 单条对话回流：把线上问答（含人工修正与当时的检索来源）存成评测用例 -->
    <el-dialog v-model="importDialog" title="加入评测集" width="440px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="目标数据集">
          <el-select v-model="importDatasetId" filterable placeholder="选择数据集" style="width:100%">
            <el-option v-for="ds in datasetOptions" :key="ds.id"
                       :label="`${ds.name}（${ds.caseCount ?? 0} 用例）`" :value="ds.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="import-hint">
        配不成问答对的那条会被拒；同一条回复重复加入只算一票，当时的检索片段会一并当成参考依据。
      </div>
      <template #footer>
        <el-button @click="importDialog = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 会话记录面板：chat_session / chat_message 的后台审计入口。
 * <p>
 * 原先是独立菜单页，与会话留痕、链路追踪是同一批排障数据，故并入可观测性的一个 tab；
 * 只被授过原菜单的角色由 V114 迁移到可观测性授权上。
 */
import { onMounted, reactive, ref } from 'vue'
import { Delete, Pointer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import { chatSessionApi } from '@admin/api/chat-session-api'
import { applicationApi } from '@admin/api/application-api'
import { evalApi } from '@admin/api/eval-api'

const emit = defineEmits<{ (e: 'view-links', sessionId: string): void }>()

const sourceOptions = ['web', 'api', 'wechat', 'dingtalk', 'feishu']
function sourceLabel(s?: string) {
  return ({ web: '网页', api: 'API', wechat: '微信', dingtalk: '钉钉', feishu: '飞书' } as Record<string, string>)[s || ''] || s || '-'
}
function roleLabel(r?: string) {
  return ({ user: '用户', assistant: '助手', system: '系统' } as Record<string, string>)[r || ''] || r
}
function roleTagType(r?: string) {
  return r === 'user' ? 'success' : r === 'assistant' ? 'primary' : 'info'
}

const appOptions = ref<any[]>([])
function appName(id?: string) {
  return appOptions.value.find(a => a.id === id)?.name || id || '-'
}

const query = reactive({ keyword: '', applicationId: '', userId: '', source: '', status: '' })
const list = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const page = await chatSessionApi.sessions({
      keyword: query.keyword || undefined,
      applicationId: query.applicationId || undefined,
      userId: query.userId || undefined,
      source: query.source || undefined,
      status: query.status || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
function reload() {
  pageNum.value = 1
  loadList()
}

/** 重置筛选条件并重新查询 */
function resetQuery() {
  query.keyword = ''
  query.applicationId = ''
  query.userId = ''
  query.source = ''
  query.status = ''
  reload()
}

// 消息抽屉
const drawer = ref(false)
const current = ref<any>(null)
const messages = ref<any[]>([])
const msgLoading = ref(false)
const msgPageNum = ref(1)
const msgPageSize = ref(20)
const msgTotal = ref(0)
const msgQuery = reactive({ role: '', feedback: '', keyword: '' })

async function loadMessages() {
  if (!current.value) return
  msgLoading.value = true
  try {
    const page = await chatSessionApi.messages(current.value.sessionId, {
      role: msgQuery.role || undefined,
      feedback: msgQuery.feedback || undefined,
      keyword: msgQuery.keyword || undefined,
      pageNum: msgPageNum.value,
      pageSize: msgPageSize.value
    })
    messages.value = page?.list || []
    msgTotal.value = page?.total || 0
  } finally {
    msgLoading.value = false
  }
}
function reloadMessages() {
  msgPageNum.value = 1
  loadMessages()
}

/** 重置消息筛选条件并重新查询 */
function resetMessageQuery() {
  msgQuery.role = ''
  msgQuery.feedback = ''
  msgQuery.keyword = ''
  reloadMessages()
}
function openMessages(row: any) {
  current.value = row
  messages.value = []
  msgQuery.role = ''
  msgQuery.feedback = ''
  msgQuery.keyword = ''
  msgPageNum.value = 1
  drawer.value = true
  loadMessages()
}

onMounted(async () => {
  try {
    appOptions.value = await applicationApi.options()
  } catch {
    appOptions.value = []
  }
  loadList()
})

// ============ 单条对话回流 ============
const importDialog = ref(false)
const importing = ref(false)
const importDatasetId = ref('')
const importMessageId = ref('')
const datasetOptions = ref<any[]>([])

/** 点开会话抽屉不顺手拉数据集列表：只有真要回流时才需要这份选项 */
async function openImportDialog(m: any) {
  importMessageId.value = m.id
  importDatasetId.value = ''
  importDialog.value = true
  if (!datasetOptions.value.length) {
    try {
      const page = await evalApi.listDatasets({ pageNum: 1, pageSize: 100 })
      datasetOptions.value = page?.list || []
    } catch {
      datasetOptions.value = []
    }
  }
}

async function submitImport() {
  if (!importDatasetId.value) {
    ElMessage.error('请选择目标数据集')
    return
  }
  importing.value = true
  try {
    await evalApi.importCaseFromMessage(importDatasetId.value, importMessageId.value)
    ElMessage.success('已加入评测集')
    importDialog.value = false
  } finally {
    importing.value = false
  }
}
</script>

<style lang="scss" scoped>
.session-title { font-weight: 500; }
.session-id { font-size: 12px; color: #909399; }
.session-desc { margin-bottom: 12px; }
.msg-list { display: flex; flex-direction: column; gap: 12px; min-height: 200px; }
.msg-item {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 10px 12px; background: #fafafa;
}
.msg-user { border-left: 3px solid var(--cj-primary); }
.msg-assistant { border-left: 3px solid #409eff; }
.msg-system { border-left: 3px solid #909399; background: #f4f4f5; }
.msg-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.msg-time { font-size: 12px; color: #909399; }
.msg-extra { font-size: 12px; color: #c0c4cc; }
.fb-like { color: var(--cj-primary); }
.fb-dislike { color: #f56c6c; }
.msg-content {
  font-size: 13px; line-height: 1.8; white-space: pre-wrap; word-break: break-all; color: #303133;
}
.msg-annotation {
  margin-top: 8px; padding: 6px 10px; border-radius: 6px;
  background: #fdf6ec; color: #e6a23c; font-size: 12px; line-height: 1.7;
}
.annotation-label { font-weight: 600; }
.msg-actions { margin-top: 6px; display: flex; justify-content: flex-end; }
.import-hint { font-size: 12px; color: #909399; line-height: 1.7; }
</style>
