<template>
  <div class="knowledge-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-input v-model="keyword" placeholder="搜索知识库..." clearable style="width: 240px"
                      @clear="loadList" @keyup.enter="loadList">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>
          <el-button type="primary" @click="showCreate = true">
            <el-icon><Plus /></el-icon> 新建知识库
          </el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="名称" prop="name" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row.id)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="切片策略" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="strategyTag(row.splitStrategy)">{{ strategyLabel(row.splitStrategy) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="切片大小" width="90" align="center">
          <template #default="{ row }">{{ row.chunkSize }} / {{ row.chunkOverlap }}</template>
        </el-table-column>
        <el-table-column label="文档数" prop="documentCount" width="80" align="center" />
        <el-table-column label="段落数" prop="paragraphCount" width="80" align="center" />
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="goDetail(row.id)">详情</el-button>
            <el-button size="small" link type="warning" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除此知识库？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="showCreate" :title="editing ? '编辑知识库' : '新建知识库'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：产品手册知识库" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="知识库描述..." />
        </el-form-item>
        <el-form-item label="切片策略" prop="splitStrategy">
          <el-radio-group v-model="form.splitStrategy">
            <el-radio-button value="sentence">句子切片</el-radio-button>
            <el-radio-button value="structural">结构切片</el-radio-button>
            <el-radio-button value="token">Token切片</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="切片大小" prop="chunkSize">
          <el-input-number v-model="form.chunkSize" :min="100" :max="5000" :step="100" />
          <span class="form-hint">字符数</span>
        </el-form-item>
        <el-form-item label="重叠量" prop="chunkOverlap">
          <el-input-number v-model="form.chunkOverlap" :min="0" :max="500" :step="10" />
          <span class="form-hint">相邻切片重叠字符数</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { knowledgeApi } from '@admin/api/knowledge-api'

const router = useRouter()
const list = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const showCreate = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: '',
  name: '',
  description: '',
  splitStrategy: 'sentence',
  chunkSize: 500,
  chunkOverlap: 50
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  splitStrategy: [{ required: true, message: '请选择切片策略', trigger: 'change' }]
}

async function loadList() {
  loading.value = true
  try {
    list.value = await knowledgeApi.list(keyword.value)
  } finally {
    loading.value = false
  }
}

function goDetail(id: string) {
  router.push(`/knowledge/${id}`)
}

function openEdit(row: any) {
  editing.value = true
  form.id = row.id
  form.name = row.name
  form.description = row.description || ''
  form.splitStrategy = row.splitStrategy || 'sentence'
  form.chunkSize = row.chunkSize || 500
  form.chunkOverlap = row.chunkOverlap || 50
  showCreate.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (editing.value) {
      await knowledgeApi.update(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await knowledgeApi.create({ ...form })
      ElMessage.success('创建成功')
    }
    showCreate.value = false
    editing.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  await knowledgeApi.remove(id)
  ElMessage.success('删除成功')
  loadList()
}

function strategyLabel(code: string) {
  return { sentence: '句子', structural: '结构', token: 'Token' }[code] || code
}

function strategyTag(code: string): any {
  return { sentence: 'success', structural: 'warning', token: 'info' }[code] || ''
}

function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.form-hint { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
