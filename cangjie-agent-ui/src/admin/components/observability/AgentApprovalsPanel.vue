<template>
  <div class="agent-approvals-panel">
    <!-- 筛选条 -->
    <QueryBar :loading="loading" @search="onSearch" @reset="resetQuery">
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width:140px">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="query.appId" placeholder="全部应用" clearable filterable
                 style="width:180px">
        <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-input v-model="query.userId" placeholder="用户 ID" clearable style="width:160px"
                @keyup.enter="onSearch" />
    </QueryBar>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="应用" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.appId }}</template>
      </el-table-column>
      <el-table-column label="用户" prop="userId" width="120" show-overflow-tooltip />
      <el-table-column label="工具" prop="toolName" min-width="140" show-overflow-tooltip />
      <el-table-column label="工具类型" prop="toolType" width="110" show-overflow-tooltip />
      <el-table-column label="风险" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="riskTagType(row.riskLevel)">{{ row.riskLevel || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="原因" prop="reason" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过期时间" prop="expireTime" width="160" />
      <el-table-column label="操作" width="70" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="showDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <DataPager v-model:current-page="pageNum" v-model:page-size="pageSize"
               :total="total" @change="loadList" />

    <!-- 审批单详情抽屉（管理侧只读，决策在对话侧完成） -->
    <el-drawer v-model="drawer" title="审批单详情" size="640px" destroy-on-close>
      <template v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusTagType(current.status)">{{ statusLabel(current.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag size="small" :type="riskTagType(current.riskLevel)">{{ current.riskLevel || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="工具名称">{{ current.toolName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工具类型">{{ current.toolType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="应用">{{ current.appId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ current.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Run ID" :span="2">{{ current.runId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话 ID" :span="2">{{ current.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="步骤 ID" :span="2">{{ current.stepId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ current.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ current.decidedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ current.decideTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">{{ current.expireTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批备注" :span="2">{{ current.decideRemark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">拦截原因</el-divider>
        <div class="reason-text">{{ current.reason || '-' }}</div>

        <el-divider content-position="left">调用参数</el-divider>
        <pre class="code-block">{{ prettyJson(current.arguments) }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import { prettyJson } from '@admin/utils/format'
import { observabilityApi } from '@admin/api/observability-api'
import { applicationApi } from '@admin/api/application-api'

const statusOptions = [
  { value: 'pending', label: '待审批' },
  { value: 'approved', label: '已批准' },
  { value: 'rejected', label: '已驳回' },
  { value: 'expired', label: '已过期' },
  { value: 'cancelled', label: '已取消' }
]
function statusLabel(s?: string) {
  return statusOptions.find(o => o.value === s)?.label || s || '-'
}
function statusTagType(s?: string) {
  switch (s) {
    case 'approved': return 'success'
    case 'rejected': return 'danger'
    case 'expired':
    case 'cancelled': return 'info'
    default: return 'warning'
  }
}
function riskTagType(r?: string) {
  if (r === 'high') return 'danger'
  if (r === 'medium' || r === 'mid') return 'warning'
  return 'info'
}

const appOptions = ref<any[]>([])
onMounted(async () => {
  try {
    appOptions.value = await applicationApi.options()
  } catch {
    appOptions.value = []
  }
  loadList()
})

const query = reactive({ status: '', appId: '', userId: '' })
const list = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const page = await observabilityApi.agentApprovals({
      status: query.status || undefined,
      appId: query.appId || undefined,
      userId: query.userId || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = page?.records || []
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
  query.status = ''
  query.appId = ''
  query.userId = ''
  onSearch()
}

const drawer = ref(false)
const current = ref<any>(null)
async function showDetail(id: string) {
  current.value = null
  drawer.value = true
  current.value = await observabilityApi.agentApprovalDetail(id)
}
</script>

<style lang="scss" scoped>
.filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
.reason-text {
  background: #fdf6ec; border: 1px solid #faecd8; border-radius: 6px;
  padding: 10px 12px; font-size: 13px; line-height: 1.7; color: #e6a23c;
}
.code-block {
  background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px;
  max-height: 400px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 12px; line-height: 1.7; margin: 0;
}
</style>
