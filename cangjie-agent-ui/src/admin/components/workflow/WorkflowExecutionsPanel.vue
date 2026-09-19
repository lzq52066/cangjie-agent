<template>
  <div class="wf-exec-panel">
    <QueryBar :loading="loading" @search="onSearch" @reset="resetQuery">
      <el-select v-model="query.workflowId" placeholder="全部工作流" clearable filterable
                 style="width:180px">
        <el-option v-for="w in workflowOptions" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
      <el-select v-model="query.applicationId" placeholder="全部应用" clearable filterable
                 style="width:170px">
        <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-input v-model="query.sessionId" placeholder="会话 ID" clearable style="width:170px"
                @keyup.enter="onSearch" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width:130px">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
    </QueryBar>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="开始时间" prop="startTime" width="160" />
      <el-table-column label="工作流" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ workflowName(row.workflowId) }}</template>
      </el-table-column>
      <el-table-column label="应用" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ appName(row.applicationId) }}</template>
      </el-table-column>
      <el-table-column label="会话" prop="sessionId" width="150" show-overflow-tooltip />
      <el-table-column label="当前节点" prop="currentNode" width="120" show-overflow-tooltip />
      <el-table-column label="耗时" width="100" align="center">
        <template #default="{ row }">{{ fmtDuration(row.duration) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="错误" prop="errorMessage" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="70" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <DataPager v-model:current-page="pageNum" v-model:page-size="pageSize"
               :total="total" @change="loadList" />

    <el-drawer v-model="drawer" title="执行详情" size="720px" destroy-on-close>
      <template v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusTag(current.status)">{{ statusLabel(current.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ fmtDuration(current.duration) }}</el-descriptions-item>
          <el-descriptions-item label="工作流">{{ workflowName(current.workflowId) }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ current.currentNode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="应用">{{ appName(current.applicationId) }}</el-descriptions-item>
          <el-descriptions-item label="会话">{{ current.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ current.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ current.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行 ID" :span="2">{{ current.id }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="current.errorMessage" type="error" :closable="false" show-icon
                  :title="current.errorMessage" style="margin:12px 0" />

        <el-divider content-position="left">节点事件（{{ events.length }}）</el-divider>
        <el-timeline v-if="events.length">
          <el-timeline-item v-for="e in events" :key="e.id" :timestamp="e.createTime" placement="top">
            <el-tag size="small" :type="eventTagType(e.status)" style="margin-right:8px">
              {{ e.nodeName || e.nodeCode || e.nodeType }}
            </el-tag>
            <span class="event-status">{{ eventStatusLabel(e.status) }}</span>
            <div v-if="e.errorMessage" class="event-error">{{ e.errorMessage }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无节点事件" :image-size="60" />

        <el-divider content-position="left">输入</el-divider>
        <pre class="code-block">{{ prettyJson(current.inputs) }}</pre>
        <el-divider content-position="left">输出</el-divider>
        <pre class="code-block">{{ prettyJson(current.outputs) }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import { prettyJson } from '@admin/utils/format'
import { workflowApi } from '@admin/api/workflow-api'
import { applicationApi } from '@admin/api/application-api'

const statusOptions = [
  { value: 'pending', label: '排队中' },
  { value: 'running', label: '执行中' },
  { value: 'completed', label: '成功' },
  { value: 'failed', label: '失败' }
]
function statusLabel(s?: string) {
  return statusOptions.find(o => o.value === s)?.label || s || '-'
}
function statusTag(s?: string): any {
  return ({ running: 'warning', completed: 'success', failed: 'danger' } as Record<string, string>)[s || ''] || 'info'
}
function eventStatusLabel(s?: string) {
  return ({ pending: '等待', running: '执行中', completed: '完成', failed: '失败', skipped: '跳过' } as Record<string, string>)[s || ''] || s || '-'
}
function eventTagType(s?: string): any {
  return ({ running: 'warning', completed: 'success', failed: 'danger', skipped: 'info' } as Record<string, string>)[s || ''] || 'info'
}
function fmtDuration(ms?: number | null) {
  if (ms == null) return '-'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s`
}

const workflowOptions = ref<any[]>([])
const appOptions = ref<any[]>([])
function workflowName(id?: string) {
  return workflowOptions.value.find(w => w.id === id)?.name || id || '-'
}
function appName(id?: string) {
  return appOptions.value.find(a => a.id === id)?.name || id || '-'
}
onMounted(async () => {
  try {
    const [wfs, apps] = await Promise.all([workflowApi.options(), applicationApi.options()])
    workflowOptions.value = wfs || []
    appOptions.value = apps || []
  } catch {
    // 下拉数据不阻塞列表
  }
  loadList()
})

const query = reactive({ workflowId: '', applicationId: '', sessionId: '', status: '' })
const list = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const page = await workflowApi.allExecutions({
      workflowId: query.workflowId || undefined,
      applicationId: query.applicationId || undefined,
      sessionId: query.sessionId || undefined,
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
function onSearch() {
  pageNum.value = 1
  loadList()
}

/** 重置筛选条件并重新查询 */
function resetQuery() {
  query.workflowId = ''
  query.applicationId = ''
  query.sessionId = ''
  query.status = ''
  onSearch()
}

const drawer = ref(false)
const current = ref<any>(null)
const events = ref<any[]>([])
async function showDetail(row: any) {
  current.value = row
  events.value = []
  drawer.value = true
  const [detail, evts] = await Promise.all([
    workflowApi.execution(row.id),
    workflowApi.executionEvents(row.id).catch(() => [])
  ])
  current.value = detail || row
  events.value = evts || []
}
</script>

<style lang="scss" scoped>
.filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
.event-status { font-size: 13px; color: #606266; }
.event-error { color: #f56c6c; font-size: 12px; margin-top: 4px; }
.code-block {
  background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px;
  max-height: 300px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 12px; line-height: 1.7; margin: 0;
}
</style>
