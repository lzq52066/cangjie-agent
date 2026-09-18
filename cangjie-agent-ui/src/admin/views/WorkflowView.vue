<template>
  <div class="workflow-view">
    <el-card>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="工作流" name="list">
          <div class="tab-toolbar">
            <el-input v-model="keyword" placeholder="搜索工作流..." clearable style="width: 220px"
                      @clear="reload" @keyup.enter="reload">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="openCreate">
              <el-icon><Plus /></el-icon> 新建工作流
            </el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="执行总览" name="executions" />
      </el-tabs>

      <WorkflowExecutionsPanel v-if="activeTab === 'executions'" />

      <template v-if="activeTab === 'list'">

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="名称" prop="name" min-width="160" />
        <el-table-column label="描述" prop="description" min-width="220" show-overflow-tooltip />
        <el-table-column label="节点数" width="90" align="center">
          <template #default="{ row }">{{ countNodes(row.nodes) }}</template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">v{{ row.version || 1 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'published' ? 'success' : 'info'">
              {{ row.status === 'published' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" width="170" />
        <el-table-column label="操作" width="320" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDesign(row)">编排</el-button>
            <el-button size="small" link type="warning" @click="handlePublish(row)">发布</el-button>
            <el-button size="small" link type="success" :disabled="row.status !== 'published'"
                       @click="openExecute(row)">执行</el-button>
            <el-button size="small" link @click="openExecutions(row)">历史</el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
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

    <!-- 基础信息 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑工作流' : '新建工作流'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="关联应用">
          <el-select v-model="form.applicationId" placeholder="可选" clearable filterable style="width:100%">
            <el-option v-for="a in applications" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="全局变量">
          <el-input v-model="form.variables" type="textarea" :rows="3" placeholder='JSON，如 {"lang":"zh"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行 -->
    <el-dialog v-model="execDialog" :title="'执行 - ' + (current?.name || '')" width="640px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="输入参数">
          <el-input v-model="execInputs" type="textarea" :rows="5" placeholder='JSON，如 {"question":"你好"}' />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="executing" @click="runExecute">开始执行</el-button>
      <template v-if="execResult">
        <el-divider>执行结果</el-divider>
        <ExecutionDetail :execution="execResult" />
      </template>
    </el-dialog>

    <!-- 执行历史 -->
    <el-drawer v-model="execDrawer" :title="'执行历史 - ' + (current?.name || '')" size="60%" destroy-on-close>
      <el-table :data="executions" v-loading="execLoading" size="small" border
                @row-click="(row: any) => (detailExecution = row)">
        <el-table-column label="执行 ID" prop="id" width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" prop="currentNode" width="130" />
        <el-table-column label="耗时(ms)" prop="duration" width="100" align="center" />
        <el-table-column label="开始时间" prop="startTime" width="170" />
        <el-table-column label="错误" prop="errorMessage" min-width="180" show-overflow-tooltip />
      </el-table>
      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     :total="execTotal" v-model:current-page="execPageNum" v-model:page-size="execPageSize"
                     :page-sizes="[10, 20, 50]" @size-change="loadExecutions" @current-change="loadExecutions" />
      <template v-if="detailExecution">
        <el-divider>执行详情</el-divider>
        <ExecutionDetail :execution="detailExecution" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { workflowApi } from '@admin/api/workflow-api'
import { applicationApi } from '@admin/api/application-api'
import ExecutionDetail from '@admin/components/ExecutionDetail.vue'
import WorkflowExecutionsPanel from '@admin/components/workflow/WorkflowExecutionsPanel.vue'

const router = useRouter()
const activeTab = ref<'list' | 'executions'>('list')

const list = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const saving = ref(false)
const current = ref<any>(null)
const applications = ref<any[]>([])

const showDialog = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Record<string, any>>({
  id: '', name: '', description: '', applicationId: '', variables: ''
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

function countNodes(nodes?: string) {
  if (!nodes) return 0
  try {
    const arr = JSON.parse(nodes)
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return 0
  }
}

function statusLabel(s: string) {
  return ({ running: '执行中', completed: '成功', failed: '失败' } as any)[s] || s
}
function statusTag(s: string): any {
  return ({ running: 'warning', completed: 'success', failed: 'danger' } as any)[s] || 'info'
}

async function loadList() {
  loading.value = true
  try {
    const page = await workflowApi.list({
      keyword: keyword.value, pageNum: pageNum.value, pageSize: pageSize.value
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

function openCreate() {
  editing.value = false
  Object.assign(form, { id: '', name: '', description: '', applicationId: '', variables: '' })
  showDialog.value = true
}

function openEdit(row: any) {
  editing.value = true
  Object.assign(form, {
    id: row.id, name: row.name, description: row.description || '',
    applicationId: row.applicationId || '', variables: row.variables || ''
  })
  showDialog.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...payload } = form
    if (editing.value) {
      await workflowApi.update(id, payload)
      ElMessage.success('更新成功')
    } else {
      await workflowApi.create(payload)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  await workflowApi.remove(id)
  ElMessage.success('删除成功')
  loadList()
}

async function handlePublish(row: any) {
  const updated = await workflowApi.publish(row.id)
  ElMessage.success(`已发布，当前版本 v${updated?.version ?? ''}`)
  loadList()
}

// 跳转到可视化流程设计器
function openDesign(row: any) {
  router.push(`/workflow/design/${row.id}`)
}

// 执行
const execDialog = ref(false)
const execInputs = ref('{}')
const execResult = ref<any>(null)
const executing = ref(false)

function openExecute(row: any) {
  current.value = row
  execResult.value = null
  execInputs.value = row.variables && row.variables.trim().startsWith('{') ? row.variables : '{}'
  execDialog.value = true
}

async function runExecute() {
  let inputs: Record<string, any>
  try {
    inputs = JSON.parse(execInputs.value || '{}')
  } catch {
    ElMessage.error('输入参数不是合法 JSON')
    return
  }
  executing.value = true
  try {
    execResult.value = await workflowApi.execute(current.value.id, inputs)
    ElMessage[execResult.value?.status === 'completed' ? 'success' : 'warning'](
      '执行' + statusLabel(execResult.value?.status))
  } finally {
    executing.value = false
  }
}

// 执行历史
const execDrawer = ref(false)
const executions = ref<any[]>([])
const execLoading = ref(false)
const detailExecution = ref<any>(null)
const execPageNum = ref(1)
const execPageSize = ref(10)
const execTotal = ref(0)

function openExecutions(row: any) {
  current.value = row
  detailExecution.value = null
  execDrawer.value = true
  execPageNum.value = 1
  loadExecutions()
}

async function loadExecutions() {
  if (!current.value) return
  execLoading.value = true
  try {
    const page = await workflowApi.executions(current.value.id, {
      pageNum: execPageNum.value, pageSize: execPageSize.value
    })
    executions.value = page?.list || []
    execTotal.value = page?.total || 0
  } finally {
    execLoading.value = false
  }
}

onMounted(async () => {
  loadList()
  try {
    applications.value = await applicationApi.options()
  } catch {
    applications.value = []
  }
})
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.tab-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
