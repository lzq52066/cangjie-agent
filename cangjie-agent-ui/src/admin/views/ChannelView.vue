<template>
  <div class="channel-view">
    <el-card>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="渠道" name="list" />
        <el-tab-pane label="消息审计" name="messages" />
      </el-tabs>

      <ChannelMessagesPanel v-if="activeTab === 'messages'" />

      <template v-if="activeTab === 'list'">
      <QueryBar :loading="loading" @search="reload" @reset="resetQuery">
        <el-input v-model="keyword" placeholder="搜索渠道名称..." clearable style="width: 200px"
                  @keyup.enter="reload">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 160px">
          <el-option v-for="t in channelTypes" :key="t.code" :label="t.label" :value="t.code" />
        </el-select>
        <template #extra>
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 新建渠道
          </el-button>
        </template>
      </QueryBar>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="渠道名称" prop="name" min-width="160" />
        <el-table-column label="类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.type)">{{ label(channelTypes, row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联应用" min-width="150">
          <template #default="{ row }">{{ appName(row.applicationId) }}</template>
        </el-table-column>
        <el-table-column label="AppId" prop="appId" min-width="160" show-overflow-tooltip />
        <el-table-column label="调用次数" prop="callCount" width="100" align="center" />
        <el-table-column label="最近调用" prop="lastCallTime" width="170" align="center">
          <template #default="{ row }">{{ row.lastCallTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link :type="row.status === 'active' ? 'warning' : 'success'"
                       @click="toggleStatus(row)">
              {{ row.status === 'active' ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" link type="primary" @click="openAccess(row)">接入信息</el-button>
            <el-button size="small" link type="info" @click="openMessages(row)">消息</el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该渠道？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                     :page-sizes="[10, 20, 50]" @size-change="loadList" @current-change="loadList" />
      </template>
    </el-card>

    <!-- 渠道编辑 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑渠道' : '新建渠道'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="渠道名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="渠道类型" prop="type">
          <el-select v-model="form.type" style="width:100%" :disabled="editing">
            <el-option v-for="t in channelTypes" :key="t.code" :label="t.label" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联应用" prop="applicationId">
          <el-select v-model="form.applicationId" filterable clearable placeholder="选择接收消息的应用" style="width:100%">
            <el-option v-for="a in applications" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="appIdLabel"><el-input v-model="form.appId" /></el-form-item>
        <el-form-item :label="appSecretLabel">
          <el-input v-model="form.appSecret" type="password" show-password />
        </el-form-item>
        <el-form-item label="验证 Token">
          <el-input v-model="form.token" placeholder="平台后台配置的 Token，用于回调校验" />
        </el-form-item>
        <el-form-item v-if="isWechat" label="消息加密 Key">
          <el-input v-model="form.encodingAesKey" placeholder="EncodingAESKey（消息加解密模式需要）" />
        </el-form-item>
        <el-form-item v-if="!isWechat" label="平台校验 Token">
          <el-input v-model="form.verifyToken" placeholder="钉钉/飞书事件订阅的 Verification Token" />
        </el-form-item>
        <el-form-item label="扩展配置">
          <el-input v-model="form.config" type="textarea" :rows="3" :placeholder="configPlaceholder" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 接入信息 -->
    <el-dialog v-model="accessDialog" title="回调接入信息" width="680px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"
                :title="accessTip" />
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="渠道类型">{{ label(channelTypes, current?.type) }}</el-descriptions-item>
        <el-descriptions-item label="回调地址">
          <div class="copy-line">
            <span class="mono">{{ callbackUrl }}</span>
            <el-button size="small" link type="primary" @click="copy(callbackUrl)">复制</el-button>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="验证 Token">
          <div class="copy-line">
            <span class="mono">{{ current?.token || '未配置' }}</span>
            <el-button v-if="current?.token" size="small" link type="primary" @click="copy(current.token)">复制</el-button>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="关联应用">{{ appName(current?.applicationId) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 消息记录 -->
    <el-dialog v-model="msgDialog" title="消息记录" width="920px" destroy-on-close>
      <el-table :data="messages" v-loading="msgLoading" stripe max-height="480">
        <el-table-column label="时间" prop="createTime" width="170" />
        <el-table-column label="用户" prop="openId" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" prop="msgType" width="90" align="center" />
        <el-table-column label="消息内容" prop="content" min-width="200" show-overflow-tooltip />
        <el-table-column label="回复内容" prop="replyContent" min-width="200" show-overflow-tooltip />
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
      </el-table>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     :total="msgTotal" v-model:current-page="msgPageNum" v-model:page-size="msgPageSize"
                     :page-sizes="[10, 20, 50]" @size-change="loadMessages" @current-change="loadMessages" />
      <el-empty v-if="!msgLoading && messages.length === 0" description="暂无消息记录" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import QueryBar from '@admin/components/QueryBar.vue'
import { channelApi } from '@admin/api/channel-api'
import { applicationApi } from '@admin/api/application-api'
import ChannelMessagesPanel from '@admin/components/trigger/ChannelMessagesPanel.vue'

const activeTab = ref<'list' | 'messages'>('list')

const channelTypes = [
  { code: 'wechat', label: '微信公众号' },
  { code: 'wechat_work', label: '企业微信' },
  { code: 'dingtalk', label: '钉钉' },
  { code: 'feishu', label: '飞书' }
]
const configPlaceholder = '{"welcome":"你好，有什么可以帮你？","timeout":15}'

const list = ref<any[]>([])
const applications = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const filterType = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const saving = ref(false)
const editing = ref(false)
const current = ref<any>(null)

const showDialog = ref(false)
const formRef = ref<FormInstance>()
const defaults = {
  id: '', name: '', type: 'wechat', applicationId: '', appId: '', appSecret: '',
  token: '', encodingAesKey: '', verifyToken: '', config: '', status: 'active'
}
const form = reactive<Record<string, any>>({ ...defaults })
const rules: FormRules = {
  name: [{ required: true, message: '请输入渠道名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择渠道类型', trigger: 'change' }],
  applicationId: [{ required: true, message: '请选择关联应用', trigger: 'change' }]
}

const isWechat = computed(() => String(form.type || '').startsWith('wechat'))
const appIdLabel = computed(() => (isWechat.value ? 'AppID' : form.type === 'dingtalk' ? 'AppKey' : 'App ID'))
const appSecretLabel = computed(() => (isWechat.value ? 'AppSecret' : form.type === 'dingtalk' ? 'AppSecret' : 'App Secret'))

const origin = ref('')
const callbackUrl = computed(() => `${origin.value}/trigger/${current.value?.type || ''}/callback`)
const accessTip = computed(() => {
  const type = current.value?.type
  if (type === 'dingtalk') return '在钉钉开放平台「机器人 - 消息接收地址」中填写下方回调地址'
  if (type === 'feishu') return '在飞书开放平台「事件订阅 - 请求地址」中填写下方回调地址'
  return '在微信公众平台「服务器配置」中填写下方回调地址与 Token，并保持渠道为启用状态'
})

function label(dict: { code: string; label: string }[], code?: string) {
  return dict.find(d => d.code === code)?.label || code || '-'
}
function typeTag(code: string): any {
  return ({ wechat: 'success', wechat_work: 'primary', dingtalk: 'warning', feishu: 'info' } as any)[code] || ''
}
function appName(id?: string) {
  return applications.value.find(a => a.id === id)?.name || (id ? id : '-')
}

async function loadList() {
  loading.value = true
  try {
    const page = await channelApi.list({
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
  Object.assign(form, defaults)
  showDialog.value = true
}

function openEdit(row: any) {
  editing.value = true
  Object.assign(form, defaults, row)
  showDialog.value = true
}

async function save() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...payload } = form
    if (editing.value) {
      await channelApi.update(id, payload)
      ElMessage.success('更新成功')
    } else {
      await channelApi.create(payload)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: any) {
  if (row.status === 'active') {
    await channelApi.disable(row.id)
    ElMessage.success('已停用')
  } else {
    await channelApi.enable(row.id)
    ElMessage.success('已启用')
  }
  loadList()
}

async function handleDelete(id: string) {
  await channelApi.remove(id)
  ElMessage.success('删除成功')
  loadList()
}

const accessDialog = ref(false)
function openAccess(row: any) {
  current.value = row
  accessDialog.value = true
}

const msgDialog = ref(false)
const msgLoading = ref(false)
const messages = ref<any[]>([])
const msgChannelId = ref('')
const msgPageNum = ref(1)
const msgPageSize = ref(10)
const msgTotal = ref(0)

async function loadMessages() {
  if (!msgChannelId.value) return
  msgLoading.value = true
  try {
    const page = await channelApi.messages(msgChannelId.value, {
      pageNum: msgPageNum.value, pageSize: msgPageSize.value
    })
    messages.value = page?.list || []
    msgTotal.value = page?.total || 0
  } finally {
    msgLoading.value = false
  }
}

function openMessages(row: any) {
  current.value = row
  msgChannelId.value = row.id
  messages.value = []
  msgTotal.value = 0
  msgPageNum.value = 1
  msgDialog.value = true
  loadMessages()
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

onMounted(async () => {
  origin.value = location.origin
  loadList()
  try {
    applications.value = await applicationApi.options()
  } catch {
    applications.value = []
  }
})
</script>

<style lang="scss" scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar-left { display: flex; gap: 12px; }
.copy-line { display: flex; align-items: center; gap: 10px; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 13px; word-break: break-all; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
