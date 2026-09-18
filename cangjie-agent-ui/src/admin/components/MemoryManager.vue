<template>
  <div class="memory-manager">
    <!-- 筛选条 -->
    <QueryBar :loading="loading" @search="onSearch" @reset="resetQuery">
      <el-input v-model="query.userId" :disabled="!isAdmin" clearable style="width: 240px"
                :placeholder="isAdmin ? '用户 ID（留空查全部）' : '用户 ID'"
                @keyup.enter="onSearch" />
      <el-select v-model="query.applicationId" placeholder="全部应用" clearable filterable
                 style="width: 200px">
        <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-select v-model="query.dimension" placeholder="全部维度" clearable style="width: 140px">
        <el-option v-for="d in dimensions" :key="d.code" :label="d.label" :value="d.code" />
      </el-select>
      <el-select v-model="query.memoryType" placeholder="全部类型" clearable style="width: 140px">
        <el-option label="用户画像（跨会话）" value="user" />
        <el-option label="场景事实（会话内）" value="scene" />
      </el-select>
      <el-checkbox v-model="query.includeInactive">含已停用</el-checkbox>
      <template #extra>
        <el-button type="success" @click="openCreate">录入记忆</el-button>
      </template>
    </QueryBar>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column v-if="isAdmin" label="用户" prop="memory.userId" width="140" show-overflow-tooltip />
      <el-table-column label="内容" prop="memory.content" min-width="240" show-overflow-tooltip />
      <el-table-column label="维度" width="100" align="center">
        <template #default="{ row }">{{ dimLabel(row.memory.dimension) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="150" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.memory.memoryType === 'scene' ? 'warning' : ''">
            {{ row.memory.memoryType === 'scene' ? '场景' : '画像' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.memory.source === 'explicit' ? 'success' : 'info'">
            {{ sourceLabel(row.memory.source) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="强度" width="80" align="center" prop="score" />
      <el-table-column label="置信度" width="90" align="center">
        <template #default="{ row }">{{ fmt(row.memory.confidence) }}</template>
      </el-table-column>
      <el-table-column label="触发" prop="memory.triggerCount" width="70" align="center" />
      <el-table-column label="应用" prop="memory.applicationId" width="140" show-overflow-tooltip />
      <el-table-column label="会话" prop="memory.sessionId" width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.memory.isActive ? 'success' : 'info'">
            {{ row.memory.isActive ? '激活' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后触发" prop="memory.lastTriggeredAt" width="160" />
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openEdit(row.memory)">编辑</el-button>
          <el-button v-if="row.memory.isActive" size="small" link type="warning" @click="toggle(row.memory, false)">停用</el-button>
          <el-button v-else size="small" link type="success" @click="toggle(row.memory, true)">激活</el-button>
          <el-popconfirm title="确定彻底删除该记忆？" @confirm="remove(row.memory.id!)">
            <template #reference>
              <el-button size="small" link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无记忆数据" :image-size="70" />
      </template>
    </el-table>

    <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                   :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                   :page-sizes="[10, 20, 50]" @size-change="loadList" @current-change="loadList" />

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑记忆' : '录入记忆'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户 ID" prop="userId">
          <el-input v-model="form.userId" :disabled="editing || !isAdmin" placeholder="记忆归属用户" />
        </el-form-item>
        <el-form-item label="应用" prop="applicationId">
          <el-select v-model="form.applicationId" filterable allow-create style="width:100%"
                     :placeholder="'选择应用，或 global（跨应用）'" :disabled="editing">
            <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
            <el-option label="global（跨应用）" value="global" />
          </el-select>
        </el-form-item>
        <el-form-item label="维度" prop="dimension">
          <el-select v-model="form.dimension" style="width:100%">
            <el-option v-for="d in dimensions" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="4" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="置信度">
          <el-input-number v-model="form.confidence" :min="0" :max="1" :step="0.05" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import QueryBar from '@admin/components/QueryBar.vue'
import { memoryApi, type LongTermMemory, type MemoryItem } from '@admin/api/memory-api'
import { applicationApi } from '@admin/api/application-api'
import { useUserStore } from '@admin/store/user'

const userStore = useUserStore()
const isAdmin = computed(() => (userStore.userInfo?.role || '').toUpperCase() === 'ADMIN')

const dimensions = [
  { code: 'preference', label: '偏好' },
  { code: 'background', label: '背景' },
  { code: 'convention', label: '惯例' },
  { code: 'goal', label: '目标' }
]
function dimLabel(code?: string) {
  return dimensions.find(d => d.code === code)?.label || code || '-'
}
function sourceLabel(source?: string) {
  if (source === 'explicit') return '人工'
  if (source === 'import') return '导入'
  return '自动'
}
function fmt(v?: number | null) {
  return v == null ? '-' : Number(v).toFixed(2)
}

const appOptions = ref<any[]>([])
onMounted(async () => {
  // 普通用户固定查询本人记忆，输入框禁用；管理员留空即查全部
  if (!isAdmin.value) {
    query.userId = userStore.userInfo?.userId || ''
  }
  try {
    appOptions.value = await applicationApi.options()
  } catch {
    appOptions.value = []
  }
  loadList()
})

const query = reactive({
  userId: '',
  applicationId: '',
  dimension: '',
  memoryType: '',
  includeInactive: false
})
const list = ref<MemoryItem[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const page = await memoryApi.list({
      userId: query.userId || undefined,
      applicationId: query.applicationId || undefined,
      dimension: query.dimension || undefined,
      memoryType: query.memoryType || undefined,
      includeInactive: query.includeInactive,
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
  // 普通用户固定查本人记忆，userId 输入框禁用，重置时保留
  if (isAdmin.value) query.userId = ''
  query.applicationId = ''
  query.dimension = ''
  query.memoryType = ''
  query.includeInactive = false
  onSearch()
}

// ===== 新增 / 编辑 =====
const showDialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<LongTermMemory>({
  userId: '', applicationId: 'global', dimension: 'preference', content: '', confidence: 0.9
})
const rules: FormRules = {
  userId: [{ required: true, message: '请输入用户 ID', trigger: 'blur' }],
  applicationId: [{ required: true, message: '请选择应用', trigger: 'change' }],
  dimension: [{ required: true, message: '请选择维度', trigger: 'change' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: undefined,
    userId: isAdmin.value ? query.userId : (userStore.userInfo?.userId || ''),
    applicationId: query.applicationId || 'global',
    dimension: 'preference',
    content: '',
    confidence: 0.9
  })
  showDialog.value = true
}

function openEdit(m: LongTermMemory) {
  editing.value = true
  Object.assign(form, {
    id: m.id,
    userId: m.userId,
    applicationId: m.applicationId,
    dimension: m.dimension,
    content: m.content,
    confidence: m.confidence ?? 0.9
  })
  showDialog.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (editing.value && form.id) {
      await memoryApi.update(form.id, {
        content: form.content,
        confidence: form.confidence,
        dimension: form.dimension
      })
      ElMessage.success('更新成功')
    } else {
      await memoryApi.create({
        userId: form.userId,
        applicationId: form.applicationId,
        dimension: form.dimension,
        content: form.content,
        confidence: form.confidence
      })
      ElMessage.success('录入成功')
    }
    showDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function toggle(m: LongTermMemory, active: boolean) {
  if (!m.id) return
  if (active) {
    await memoryApi.reactivate(m.id)
    ElMessage.success('已激活')
  } else {
    await memoryApi.deactivate(m.id)
    ElMessage.success('已停用')
  }
  loadList()
}

async function remove(id: string) {
  await memoryApi.remove(id)
  ElMessage.success('删除成功')
  loadList()
}
</script>

<style lang="scss" scoped>
.filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
