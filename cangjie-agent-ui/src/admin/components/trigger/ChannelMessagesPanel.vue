<template>
  <div class="channel-msg-panel">
    <QueryBar :loading="loading" @search="onSearch" @reset="resetQuery">
      <el-select v-model="query.channelId" placeholder="全部渠道" clearable filterable
                 style="width:170px">
        <el-option v-for="c in channelOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="query.channelType" placeholder="全部类型" clearable style="width:140px">
        <el-option v-for="t in channelTypes" :key="t.code" :label="t.label" :value="t.code" />
      </el-select>
      <el-select v-model="query.applicationId" placeholder="全部应用" clearable filterable
                 style="width:160px">
        <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-input v-model="query.openId" placeholder="外部用户 ID" clearable style="width:150px"
                @keyup.enter="onSearch" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width:120px">
        <el-option label="处理成功" value="processed" />
        <el-option label="处理失败" value="failed" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="消息/回复内容关键词" clearable style="width:200px"
                @keyup.enter="onSearch" />
    </QueryBar>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="时间" prop="createTime" width="165" />
      <el-table-column label="渠道" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ channelName(row.channelId) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">{{ typeLabel(row.channelType) }}</template>
      </el-table-column>
      <el-table-column label="应用" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ appName(row.applicationId) }}</template>
      </el-table-column>
      <el-table-column label="外部用户" prop="openId" width="130" show-overflow-tooltip />
      <el-table-column label="消息" prop="content" min-width="180" show-overflow-tooltip />
      <el-table-column label="回复" prop="replyContent" min-width="180" show-overflow-tooltip />
      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">{{ row.costTime != null ? row.costTime + ' ms' : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
            <el-tag size="small" type="danger">失败</el-tag>
          </el-tooltip>
          <el-tag v-else size="small" type="success">成功</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="showDetail(row)">明细</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                   :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                   :page-sizes="[10, 20, 50]" @size-change="loadList" @current-change="loadList" />

    <el-drawer v-model="drawer" title="渠道消息明细" size="600px" destroy-on-close>
      <template v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="时间" :span="2">{{ current.createTime }}</el-descriptions-item>
          <el-descriptions-item label="渠道">{{ channelName(current.channelId) }}</el-descriptions-item>
          <el-descriptions-item label="渠道类型">{{ typeLabel(current.channelType) }}</el-descriptions-item>
          <el-descriptions-item label="应用">{{ appName(current.applicationId) }}</el-descriptions-item>
          <el-descriptions-item label="外部用户">{{ current.openId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话 ID" :span="2">{{ current.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="消息类型">{{ current.msgType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="事件类型">{{ current.eventType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="current.status === 'failed' ? 'danger' : 'success'">
              {{ current.status === 'failed' ? '失败' : '成功' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ current.costTime != null ? current.costTime + ' ms' : '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="current.errorMessage" type="error" :closable="false" show-icon
                  :title="current.errorMessage" style="margin:12px 0" />

        <el-divider content-position="left">消息内容</el-divider>
        <div class="bubble bubble-in">{{ current.content || '-' }}</div>
        <el-divider content-position="left">回复内容</el-divider>
        <div class="bubble bubble-out">{{ current.replyContent || '-' }}</div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import QueryBar from '@admin/components/QueryBar.vue'
import { channelApi } from '@admin/api/channel-api'
import { applicationApi } from '@admin/api/application-api'

const channelTypes = [
  { code: 'wechat', label: '微信公众号' },
  { code: 'wechat_work', label: '企业微信' },
  { code: 'dingtalk', label: '钉钉' },
  { code: 'feishu', label: '飞书' }
]
function typeLabel(code?: string) {
  return channelTypes.find(t => t.code === code)?.label || code || '-'
}

const channelOptions = ref<any[]>([])
const appOptions = ref<any[]>([])
function channelName(id?: string) {
  return channelOptions.value.find(c => c.id === id)?.name || id || '-'
}
function appName(id?: string) {
  return appOptions.value.find(a => a.id === id)?.name || id || '-'
}
onMounted(async () => {
  try {
    const [channels, apps] = await Promise.all([channelApi.options(), applicationApi.options()])
    channelOptions.value = channels || []
    appOptions.value = apps || []
  } catch {
    // 下拉数据不阻塞列表
  }
  loadList()
})

const query = reactive({
  channelId: '', channelType: '', applicationId: '', openId: '', status: '', keyword: ''
})
const list = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const page = await channelApi.allMessages({
      channelId: query.channelId || undefined,
      channelType: query.channelType || undefined,
      applicationId: query.applicationId || undefined,
      openId: query.openId || undefined,
      status: query.status || undefined,
      keyword: query.keyword || undefined,
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
  query.channelId = ''
  query.channelType = ''
  query.applicationId = ''
  query.openId = ''
  query.status = ''
  query.keyword = ''
  onSearch()
}

const drawer = ref(false)
const current = ref<any>(null)
function showDetail(row: any) {
  current.value = row
  drawer.value = true
}
</script>

<style lang="scss" scoped>
.filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.bubble {
  border-radius: 10px; padding: 10px 14px; font-size: 13px; line-height: 1.8;
  white-space: pre-wrap; word-break: break-all;
}
.bubble-in { background: #f4f4f5; color: #303133; }
.bubble-out { background: #ecf5ff; color: #303133; }
</style>
