<template>
  <div class="model-view">
    <el-tabs v-model="activeTab">
      <!-- 模型管理 -->
      <el-tab-pane label="模型管理" name="model">
        <el-card>
          <template #header>
            <QueryBar :loading="loading" @search="reload" @reset="resetModelQuery">
              <el-input v-model="keyword" placeholder="搜索模型..." clearable style="width: 200px"
                        @keyup.enter="reload">
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-select v-model="filterProviderId" placeholder="全部厂商" clearable style="width: 160px">
                <el-option v-for="p in providerOptions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <template #extra>
                <el-button type="primary" @click="openCreate">
                  <el-icon><Plus /></el-icon> 添加模型
                </el-button>
              </template>
            </QueryBar>
          </template>

          <el-table :data="list" v-loading="loading" stripe>
            <el-table-column label="名称" prop="name" min-width="150" />
            <el-table-column label="厂商" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTag(row.modelType)">{{ providerLabel(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="模型标识" prop="modelName" min-width="150" />
            <el-table-column label="嵌入" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.supportEmbedding" size="small" type="success">支持</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="默认" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault" size="small" type="warning">默认</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
                  {{ row.status === 'active' ? '激活' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right" align="center">
              <template #default="{ row }">
                <el-button size="small" link type="success" @click="handleTest(row)">测试</el-button>
                <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="!row.isDefault" size="small" link type="warning" @click="handleSetDefault(row.id)">设默认</el-button>
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
      </el-tab-pane>

      <!-- 厂商管理 -->
      <el-tab-pane label="厂商管理" name="provider">
        <el-card>
          <template #header>
            <QueryBar :loading="providerLoading" @search="reloadProviders" @reset="resetProviderQuery">
              <el-input v-model="providerKeyword" placeholder="搜索厂商..." clearable style="width: 200px"
                        @keyup.enter="reloadProviders">
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <template #extra>
                <el-button type="primary" @click="openProviderCreate">
                  <el-icon><Plus /></el-icon> 添加厂商
                </el-button>
              </template>
            </QueryBar>
          </template>

          <el-alert type="info" :closable="false" show-icon class="provider-tip"
                    title="厂商统一维护 API Key 与 Base URL，模型只需关联厂商即可复用凭证，同厂商下多个模型无需重复配置。" />

          <el-table :data="providers" v-loading="providerLoading" stripe>
            <el-table-column label="名称" prop="name" min-width="120" />
            <el-table-column label="标识" prop="code" width="110" />
            <el-table-column label="Base URL" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.baseUrl">{{ row.baseUrl }}</span>
                <span v-else class="text-muted">未配置</span>
              </template>
            </el-table-column>
            <el-table-column label="API Key" width="140">
              <template #default="{ row }">
                <span v-if="row.apiKey">{{ row.apiKey }}</span>
                <span v-else class="text-muted">未配置</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
                  {{ row.status === 'active' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="描述" prop="description" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="140" fixed="right" align="center">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openProviderEdit(row)">编辑</el-button>
                <el-popconfirm title="确定删除该厂商？" @confirm="handleProviderDelete(row.id)">
                  <template #reference>
                    <el-button size="small" link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <DataPager v-model:current-page="providerPageNum" v-model:page-size="providerPageSize"
                     :total="providerTotal" @change="loadProviders" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建/编辑模型 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑模型' : '添加模型'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：GPT-4o" />
        </el-form-item>
        <el-form-item label="厂商" prop="providerId">
          <el-select v-model="form.providerId" style="width: 100%" placeholder="选择厂商">
            <el-option v-for="p in providerOptions" :key="p.id" :label="`${p.name}（${p.code}）`" :value="p.id" />
          </el-select>
          <span class="form-hint">API Key 与 Base URL 统一在「厂商管理」中维护，模型无需填写</span>
        </el-form-item>
        <el-form-item label="凭证来源">
          <span class="form-hint">{{ credentialHint }}</span>
        </el-form-item>
        <el-form-item label="模型标识" prop="modelName">
          <el-input v-model="form.modelName" placeholder="如 gpt-4o、deepseek-chat、qwen-max、glm-4" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="温度">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Max Tokens">
              <el-input-number v-model="form.maxTokens" :min="1" :max="128000" :step="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="支持嵌入">
          <el-switch v-model="form.supportEmbedding" />
          <template v-if="form.supportEmbedding">
            <el-input-number v-model="form.embeddingDimension" :min="64" :max="4096" :step="64" style="margin-left: 16px" />
            <span class="form-hint">维度</span>
          </template>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 创建/编辑厂商 -->
    <el-dialog v-model="showProviderDialog" :title="providerEditing ? '编辑厂商' : '添加厂商'" width="560px">
      <el-form ref="providerFormRef" :model="providerForm" :rules="providerRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="providerForm.name" placeholder="如：DeepSeek" />
        </el-form-item>
        <el-form-item label="标识" prop="code">
          <el-input v-model="providerForm.code" :disabled="providerEditing" placeholder="如：deepseek" />
          <span v-if="providerEditing" class="form-hint">标识用于关联模型类型，创建后不可修改</span>
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="providerForm.baseUrl" placeholder="如：https://api.deepseek.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="providerForm.apiKey" type="password" show-password placeholder="sk-..." />
          <span v-if="providerEditing" class="form-hint">保持脱敏值即不修改密钥</span>
        </el-form-item>
        <el-form-item v-if="providerEditing" label="状态" prop="status">
          <el-switch v-model="providerForm.status" active-value="active" inactive-value="inactive"
                     active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="providerForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProviderDialog = false">取消</el-button>
        <el-button type="primary" :loading="providerSaving" @click="handleProviderSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试对话框 -->
    <el-dialog v-model="testDialog" title="模型测试" width="600px">
      <div class="test-area">
        <el-input v-model="testMessage" type="textarea" :rows="3" placeholder="输入测试消息..." />
        <el-button type="primary" :loading="testing" @click="runTest" style="margin-top: 12px">发送</el-button>
        <div v-if="testResult" class="test-result">
          <div class="test-result-label">回复：</div>
          <div class="test-result-content">{{ testResult }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import { modelApi, modelProviderApi, type ModelProvider } from '@admin/api/model-api'

const activeTab = ref<'model' | 'provider'>('model')

const list = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const filterProviderId = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const providers = ref<ModelProvider[]>([])
const providerLoading = ref(false)
const providerKeyword = ref('')
const providerPageNum = ref(1)
const providerPageSize = ref(10)
const providerTotal = ref(0)
/** 厂商下拉候选（全量，独立于厂商表格的分页数据） */
const providerOptions = ref<ModelProvider[]>([])

/** 标签配色（仅用于展示，厂商可随时增删） */
const tagColors: Record<string, string> = {
  openai: 'success', deepseek: 'primary', qwen: 'primary',
  zhipu: 'warning', wenxin: 'info', ollama: '', custom: 'info'
}

const showDialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: '',
  name: '',
  providerId: '',
  modelName: '',
  temperature: 0.7,
  maxTokens: 4096,
  topP: 1.0,
  isDefault: false,
  supportEmbedding: false,
  embeddingDimension: 1536,
  description: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  providerId: [{ required: true, message: '请选择厂商', trigger: 'change' }],
  modelName: [{ required: true, message: '请输入模型标识', trigger: 'blur' }]
}

// 厂商表单
const showProviderDialog = ref(false)
const providerEditing = ref(false)
const providerSaving = ref(false)
const providerFormRef = ref<FormInstance>()
const providerForm = reactive({
  id: '', name: '', code: '', baseUrl: '', apiKey: '', status: 'active', description: ''
})
const providerRules: FormRules = {
  name: [{ required: true, message: '请输入厂商名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入厂商标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]{1,20}$/, message: '仅支持字母、数字、下划线与短横线', trigger: 'blur' }
  ]
}

// 测试
const testDialog = ref(false)
const testMessage = ref('你好，请用一句话介绍你自己。')
const testResult = ref('')
const testing = ref(false)
const testModelId = ref('')

async function loadProviders() {
  providerLoading.value = true
  try {
    const page = await modelProviderApi.list({
      keyword: providerKeyword.value, pageNum: providerPageNum.value, pageSize: providerPageSize.value
    })
    providers.value = page?.list || []
    providerTotal.value = page?.total || 0
  } finally {
    providerLoading.value = false
  }
}

/** 加载厂商下拉候选（供筛选与表单选择使用） */
async function loadProviderOptions() {
  providerOptions.value = await modelProviderApi.options() || []
}

/** 厂商搜索：只重置厂商分页 */
function reloadProviders() {
  providerPageNum.value = 1
  loadProviders()
}

async function loadList() {
  loading.value = true
  try {
    const page = await modelApi.list({
      keyword: keyword.value, providerId: filterProviderId.value,
      pageNum: pageNum.value, pageSize: pageSize.value
    })
    list.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

/** 模型搜索/筛选：只重置模型分页 */
function reload() {
  pageNum.value = 1
  loadList()
}

/** 重置筛选条件并重新查询 */
function resetModelQuery() {
  keyword.value = ''
  filterProviderId.value = ''
  reload()
}

/** 重置厂商筛选条件并重新查询 */
function resetProviderQuery() {
  providerKeyword.value = ''
  reloadProviders()
}

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: '', name: '', providerId: '',
    modelName: '', temperature: 0.7, maxTokens: 4096, topP: 1.0,
    isDefault: false, supportEmbedding: false, embeddingDimension: 1536, description: ''
  })
  showDialog.value = true
}

function openEdit(row: any) {
  editing.value = true
  Object.assign(form, {
    id: row.id, name: row.name, providerId: row.providerId || '',
    modelName: row.modelName,
    temperature: row.temperature ?? 0.7, maxTokens: row.maxTokens ?? 4096, topP: row.topP ?? 1.0,
    isDefault: row.isDefault, supportEmbedding: row.supportEmbedding,
    embeddingDimension: row.embeddingDimension || 1536, description: row.description || ''
  })
  showDialog.value = true
}

/** 当前选中厂商（凭证唯一来源） */
const selectedProvider = computed(() => providerOptions.value.find(p => p.id === form.providerId))

/** 凭证来源说明 */
const credentialHint = computed(() => {
  const p = selectedProvider.value
  if (!p) return '请先选择厂商'
  return `「${p.name}」：${p.apiKey || '未配置密钥'} · ${p.baseUrl || '未配置地址'}`
})

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...payload } = form
    if (editing.value) {
      await modelApi.update(id, payload)
      ElMessage.success('更新成功')
    } else {
      await modelApi.create(payload)
      ElMessage.success('添加成功')
    }
    showDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  await modelApi.remove(id)
  ElMessage.success('删除成功')
  loadList()
}

async function handleSetDefault(id: string) {
  await modelApi.setDefault(id)
  ElMessage.success('已设为默认')
  loadList()
}

function handleTest(row: any) {
  testModelId.value = row.id
  testResult.value = ''
  testDialog.value = true
}

async function runTest() {
  testing.value = true
  testResult.value = ''
  try {
    testResult.value = await modelApi.test(testModelId.value, testMessage.value)
  } catch (e: any) {
    testResult.value = '测试失败: ' + (e.message || '未知错误')
  } finally {
    testing.value = false
  }
}

function openProviderCreate() {
  providerEditing.value = false
  Object.assign(providerForm, {
    id: '', name: '', code: '', baseUrl: '', apiKey: '', status: 'active', description: ''
  })
  showProviderDialog.value = true
}

function openProviderEdit(row: ModelProvider) {
  providerEditing.value = true
  Object.assign(providerForm, {
    id: row.id, name: row.name, code: row.code, baseUrl: row.baseUrl || '',
    apiKey: row.apiKey || '', status: row.status || 'active', description: row.description || ''
  })
  showProviderDialog.value = true
}

async function handleProviderSave() {
  if (!providerFormRef.value) return
  await providerFormRef.value.validate()
  providerSaving.value = true
  try {
    const { id, code, name, baseUrl, apiKey, status, description } = providerForm
    if (providerEditing.value) {
      await modelProviderApi.update(id, { name, baseUrl, apiKey, status, description })
      ElMessage.success('更新成功')
    } else {
      await modelProviderApi.create({ name, code, baseUrl, apiKey, description })
      ElMessage.success('添加成功')
    }
    showProviderDialog.value = false
    await Promise.all([loadProviders(), loadProviderOptions()])
    loadList()
  } finally {
    providerSaving.value = false
  }
}

async function handleProviderDelete(id: string) {
  await modelProviderApi.remove(id)
  ElMessage.success('删除成功')
  await Promise.all([loadProviders(), loadProviderOptions()])
  loadList()
}

function providerLabel(row: any) {
  if (row.providerId) {
    return providerOptions.value.find(p => p.id === row.providerId)?.name || row.modelType || '未知厂商'
  }
  return row.modelType || '未关联'
}

function typeTag(code: string): any {
  return tagColors[code] || ''
}

onMounted(async () => {
  await Promise.all([loadProviders(), loadProviderOptions()])
  loadList()
})
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; gap: 12px; }
.form-hint { margin-left: 8px; color: #909399; font-size: 12px; }
.text-muted { color: #909399; font-size: 12px; }
.provider-tip { margin-bottom: 12px; }
.test-area { padding: 8px 0; }
.test-result { margin-top: 16px; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; background: #f9fafc; }
.test-result-label { font-weight: 600; margin-bottom: 8px; color: #606266; }
.test-result-content { white-space: pre-wrap; line-height: 1.8; }
</style>
