<template>
  <div class="model-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-input v-model="keyword" placeholder="搜索模型..." clearable style="width: 200px"
                      @clear="loadList" @keyup.enter="loadList">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 140px" @change="loadList">
              <el-option v-for="t in modelTypes" :key="t.code" :label="t.label" :value="t.code" />
            </el-select>
          </div>
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 添加模型
          </el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="名称" prop="name" min-width="150" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.modelType)">{{ typeLabel(row.modelType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型标识" prop="modelName" min-width="150" />
        <el-table-column label="Base URL" prop="baseUrl" min-width="250" show-overflow-tooltip />
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
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑模型' : '添加模型'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：GPT-4o" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" placeholder="选择类型" style="width: 100%" @change="onTypeChange">
            <el-option v-for="t in modelTypes" :key="t.code" :label="t.label" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="自动填充，可自定义" />
        </el-form-item>
        <el-form-item label="模型标识" prop="modelName">
          <el-input v-model="form.modelName" placeholder="如 gpt-4o、qwen-max、glm-4" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { modelApi } from '@admin/api/model-api'

const list = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const filterType = ref('')

const modelTypes = [
  { code: 'openai', label: 'OpenAI', baseUrl: 'https://api.openai.com/v1' },
  { code: 'qwen', label: '通义千问', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { code: 'zhipu', label: '智谱清言', baseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
  { code: 'wenxin', label: '文心一言', baseUrl: 'https://qianfan.baidubce.com/v2' },
  { code: 'ollama', label: 'Ollama', baseUrl: 'http://localhost:11434/v1' },
  { code: 'custom', label: '自定义', baseUrl: '' }
]

const showDialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: '',
  name: '',
  modelType: 'openai',
  apiKey: '',
  baseUrl: '',
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
  modelType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型标识', trigger: 'blur' }]
}

// 测试
const testDialog = ref(false)
const testMessage = ref('你好，请用一句话介绍你自己。')
const testResult = ref('')
const testing = ref(false)
const testModelId = ref('')

async function loadList() {
  loading.value = true
  try {
    list.value = await modelApi.list(keyword.value, filterType.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: '', name: '', modelType: 'openai', apiKey: '', baseUrl: '',
    modelName: '', temperature: 0.7, maxTokens: 4096, topP: 1.0,
    isDefault: false, supportEmbedding: false, embeddingDimension: 1536, description: ''
  })
  showDialog.value = true
}

function openEdit(row: any) {
  editing.value = true
  Object.assign(form, {
    id: row.id, name: row.name, modelType: row.modelType, apiKey: row.apiKey || '',
    baseUrl: row.baseUrl || '', modelName: row.modelName,
    temperature: row.temperature ?? 0.7, maxTokens: row.maxTokens ?? 4096, topP: row.topP ?? 1.0,
    isDefault: row.isDefault, supportEmbedding: row.supportEmbedding,
    embeddingDimension: row.embeddingDimension || 1536, description: row.description || ''
  })
  showDialog.value = true
}

function onTypeChange(code: string) {
  const t = modelTypes.find(t => t.code === code)
  if (t && !form.baseUrl) {
    form.baseUrl = t.baseUrl
  }
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (editing.value) {
      await modelApi.update(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await modelApi.create({ ...form })
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

function typeLabel(code: string) {
  return modelTypes.find(t => t.code === code)?.label || code
}
function typeTag(code: string): any {
  return { openai: 'success', qwen: 'primary', zhipu: 'warning', wenxin: 'info', ollama: '', custom: 'info' }[code] || ''
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; gap: 12px; }
.form-hint { margin-left: 8px; color: #909399; font-size: 12px; }
.test-area { padding: 8px 0; }
.test-result { margin-top: 16px; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; background: #f9fafc; }
.test-result-label { font-weight: 600; margin-bottom: 8px; color: #606266; }
.test-result-content { white-space: pre-wrap; line-height: 1.8; }
</style>
