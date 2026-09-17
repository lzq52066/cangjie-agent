<template>
  <div class="knowledge-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-input v-model="keyword" placeholder="搜索知识库..." clearable style="width: 240px"
                      @clear="reload" @keyup.enter="reload">
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
        <el-table-column label="段落大小" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.splitStrategy === 'custom'">{{ row.chunkSize }}</span>
            <span v-else class="text-muted">自动</span>
          </template>
        </el-table-column>
        <el-table-column label="检索模式" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.searchMode === 'two_stage' ? 'primary' : 'info'" effect="plain">
              {{ row.searchMode === 'two_stage' ? '摘要先行' : '段落检索' }}
            </el-tag>
          </template>
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

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                     :page-sizes="[10, 20, 50]" @size-change="loadList" @current-change="loadList" />
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
        <el-form-item label="分段方式" prop="splitStrategy">
          <el-radio-group v-model="form.splitStrategy">
            <el-radio-button value="smart">智能分段</el-radio-button>
            <el-radio-button value="custom">自定义分段</el-radio-button>
          </el-radio-group>
          <div class="form-hint" style="margin-top: 4px">
            <span v-if="form.splitStrategy === 'smart'">自动识别文档结构，按标题和段落智能切分</span>
            <span v-else>手动指定分隔符和段落最大长度</span>
          </div>
        </el-form-item>
        <template v-if="form.splitStrategy === 'custom'">
          <el-form-item label="分隔符" prop="separators">
            <el-select v-model="form.separators" multiple placeholder="选择分隔符" style="width: 100%">
              <el-option-group label="标题">
                <el-option label="一级标题 (#)" value="h1" />
                <el-option label="二级标题 (##)" value="h2" />
                <el-option label="三级标题 (###)" value="h3" />
                <el-option label="四级标题 (####)" value="h4" />
              </el-option-group>
              <el-option-group label="段落">
                <el-option label="空行" value="blank_line" />
                <el-option label="换行" value="newline" />
              </el-option-group>
              <el-option-group label="标点">
                <el-option label="句号 (。！？)" value="period" />
                <el-option label="分号 (；;)" value="semicolon" />
                <el-option label="逗号 (，,)" value="comma" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item label="段落长度" prop="chunkSize">
            <el-slider v-model="form.chunkSize" :min="100" :max="2000" :step="100" show-input />
            <div class="form-hint">段落最大字符数</div>
          </el-form-item>
        </template>
        <el-form-item label="检索模式" prop="searchMode">
          <el-radio-group v-model="form.searchMode">
            <el-radio-button value="paragraph">段落检索</el-radio-button>
            <el-radio-button value="two_stage">摘要先行</el-radio-button>
          </el-radio-group>
          <div class="form-hint" style="margin-top: 4px">
            <span v-if="form.searchMode === 'paragraph'">直接对文档切片做向量 + 全文混合检索</span>
            <span v-else>先用文档摘要筛选候选文档，再对候选做段落级精准检索</span>
          </div>
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
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showCreate = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: '',
  name: '',
  description: '',
  splitStrategy: 'smart',
  chunkSize: 500,
  separators: [] as string[]
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  splitStrategy: [{ required: true, message: '请选择切片策略', trigger: 'change' }]
}

async function loadList() {
  loading.value = true
  try {
    const page = await knowledgeApi.list({
      keyword: keyword.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

/** 搜索/筛选/新建后回到第一页再加载 */
function reload() {
  pageNum.value = 1
  loadList()
}

function goDetail(id: string) {
  router.push(`/knowledge/${id}`)
}

function openEdit(row: any) {
  editing.value = true
  form.id = row.id
  form.name = row.name
  form.description = row.description || ''
  form.splitStrategy = row.splitStrategy || 'smart'
  form.chunkSize = row.chunkSize || 500
  form.searchMode = row.searchMode || 'paragraph'
  // 解析 separators JSON 字符串
  try {
    form.separators = row.separators ? JSON.parse(row.separators) : []
  } catch {
    form.separators = []
  }
  showCreate.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      ...form,
      // 智能分段模式不提交 chunkSize 和 separators
      chunkSize: form.splitStrategy === 'smart' ? undefined : form.chunkSize,
      separators: form.splitStrategy === 'smart' ? undefined : JSON.stringify(form.separators)
    }
    const isEdit = editing.value
    if (isEdit) {
      await knowledgeApi.update(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await knowledgeApi.create(payload)
      ElMessage.success('创建成功')
    }
    showCreate.value = false
    editing.value = false
    // 新建后回到第一页，编辑后留在当前页
    if (isEdit) {
      loadList()
    } else {
      reload()
    }
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
  return { smart: '智能', custom: '自定义' }[code] || code
}

function strategyTag(code: string): any {
  return { smart: 'success', custom: 'warning' }[code] || ''
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
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
