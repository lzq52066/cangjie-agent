<template>
  <div class="workflow-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-input v-model="keyword" placeholder="搜索工作流..." clearable style="width: 220px"
                    @clear="loadList" @keyup.enter="loadList">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 新建工作流
          </el-button>
        </div>
      </template>

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

    <!-- 编排抽屉 -->
    <el-drawer v-model="designDrawer" :title="'编排 - ' + (current?.name || '')" size="72%" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"
                title="按节点 + 连线定义 DAG，执行时从 start 节点开始按连线顺序遍历" />

      <div class="section-head">
        <span class="section-title">节点</span>
        <el-button size="small" type="primary" @click="openNode()">
          <el-icon><Plus /></el-icon> 添加节点
        </el-button>
      </div>
      <el-table :data="designNodes" size="small" border>
        <el-table-column label="节点 ID" prop="id" width="150" />
        <el-table-column label="名称" prop="name" min-width="140" />
        <el-table-column label="类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'start' ? 'success' : row.type === 'end' ? 'danger' : ''">
              {{ nodeTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配置" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.config ? JSON.stringify(row.config) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row, $index }">
            <el-button size="small" link type="primary" @click="openNode(row, $index)">编辑</el-button>
            <el-button size="small" link type="danger" @click="removeNode($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-head" style="margin-top:24px">
        <span class="section-title">连线</span>
        <el-button size="small" type="primary" :disabled="designNodes.length < 2" @click="addEdge">
          <el-icon><Plus /></el-icon> 添加连线
        </el-button>
      </div>
      <el-table :data="designEdges" size="small" border>
        <el-table-column label="来源节点" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.source" size="small" style="width:100%">
              <el-option v-for="n in designNodes" :key="n.id" :label="nodeLabel(n)" :value="n.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="目标节点" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.target" size="small" style="width:100%">
              <el-option v-for="n in designNodes" :key="n.id" :label="nodeLabel(n)" :value="n.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="条件" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.condition" size="small" placeholder="可选，condition 节点分支" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" @click="designEdges.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flow-preview" v-if="flowChain.length">
        <span class="section-title">执行链路预览</span>
        <div class="chain">
          <template v-for="(n, i) in flowChain" :key="n.id + i">
            <span class="chain-node">{{ nodeLabel(n) }}</span>
            <el-icon v-if="i < flowChain.length - 1" class="chain-arrow"><Right /></el-icon>
          </template>
        </div>
      </div>

      <template #footer>
        <el-button @click="designDrawer = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDesign">保存编排</el-button>
      </template>
    </el-drawer>

    <!-- 节点编辑 -->
    <el-dialog v-model="nodeDialog" :title="nodeIndex < 0 ? '添加节点' : '编辑节点'" width="600px" destroy-on-close append-to-body>
      <el-form :model="nodeForm" label-width="90px">
        <el-form-item label="节点 ID" required>
          <el-input v-model="nodeForm.id" placeholder="唯一标识，如 node_1" />
        </el-form-item>
        <el-form-item label="节点类型" required>
          <el-select v-model="nodeForm.type" style="width:100%" @change="onNodeTypeChange">
            <el-option v-for="t in nodeTypeOptions" :key="t.type" :label="t.name || t.type" :value="t.type">
              <span>{{ t.name || t.type }}</span>
              <span class="opt-desc">{{ t.description }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="节点名称" required><el-input v-model="nodeForm.name" /></el-form-item>
        <el-form-item label="节点配置">
          <el-input v-model="nodeForm.configText" type="textarea" :rows="6" placeholder='JSON，如 {"modelId":"xxx"}' />
          <div class="hint">{{ nodeTypeHint }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveNode">确定</el-button>
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
      <template v-if="detailExecution">
        <el-divider>执行详情</el-divider>
        <ExecutionDetail :execution="detailExecution" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Right } from '@element-plus/icons-vue'
import { workflowApi } from '@admin/api/workflow-api'
import { applicationApi } from '@admin/api/application-api'
import ExecutionDetail from '@admin/components/ExecutionDetail.vue'

interface FlowNode {
  id: string
  type: string
  name: string
  config?: Record<string, any>
  position?: Record<string, any>
}
interface FlowEdge { source: string; target: string; condition?: string }

const list = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const saving = ref(false)
const current = ref<any>(null)
const applications = ref<any[]>([])

// 后端注册的节点类型；取不到时用内置清单兜底
const builtinNodeTypes = [
  { type: 'start', name: '开始', description: '工作流入口，注入初始变量' },
  { type: 'llm', name: '大模型', description: '调用大模型生成内容' },
  { type: 'knowledge', name: '知识检索', description: 'RAG 混合检索知识库' },
  { type: 'tool', name: '工具调用', description: '调用已注册的工具/插件' },
  { type: 'condition', name: '条件分支', description: '按表达式选择后继分支' },
  { type: 'loop', name: '循环', description: '按集合或次数循环执行' },
  { type: 'api', name: 'HTTP 请求', description: '调用外部 HTTP 接口' },
  { type: 'code', name: '代码执行', description: '执行脚本处理变量' },
  { type: 'end', name: '结束', description: '工作流出口，输出结果' }
]
const nodeTypeOptions = ref<any[]>(builtinNodeTypes)
const nodeConfigHints: Record<string, string> = {
  start: '通常无需配置',
  llm: '示例：{"modelId":"模型ID","prompt":"{question}","temperature":0.7}',
  knowledge: '示例：{"knowledgeBaseIds":["库ID"],"topK":5}',
  tool: '示例：{"toolId":"工具ID","input":{}}',
  condition: '示例：{"expression":"score > 0.8"}',
  loop: '示例：{"items":"list","maxIterations":10}',
  api: '示例：{"url":"https://...","method":"POST","body":{}}',
  code: '示例：{"script":"return {result: input.a + input.b}"}',
  end: '示例：{"output":"answer"}'
}

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

function nodeTypeLabel(type: string) {
  return nodeTypeOptions.value.find((t: any) => t.type === type)?.name || type
}
function nodeLabel(n: FlowNode) {
  return `${n.name || n.id} (${n.id})`
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
    list.value = await workflowApi.list(keyword.value)
  } finally {
    loading.value = false
  }
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

// 编排
const designDrawer = ref(false)
const designNodes = ref<FlowNode[]>([])
const designEdges = ref<FlowEdge[]>([])

function openDesign(row: any) {
  current.value = row
  designNodes.value = safeParse(row.nodes, [])
  designEdges.value = safeParse(row.edges, [])
  designDrawer.value = true
}

function safeParse<T>(text: string | undefined, fallback: T): T {
  if (!text) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

async function saveDesign() {
  const ids = designNodes.value.map(n => n.id)
  if (new Set(ids).size !== ids.length) {
    ElMessage.error('存在重复的节点 ID')
    return
  }
  const invalidEdge = designEdges.value.find(e => !ids.includes(e.source) || !ids.includes(e.target))
  if (invalidEdge) {
    ElMessage.error('存在指向不存在节点的连线')
    return
  }
  saving.value = true
  try {
    await workflowApi.update(current.value.id, {
      name: current.value.name,
      description: current.value.description,
      applicationId: current.value.applicationId,
      variables: current.value.variables,
      nodes: JSON.stringify(designNodes.value),
      edges: JSON.stringify(designEdges.value)
    })
    ElMessage.success('编排已保存')
    designDrawer.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// 节点编辑
const nodeDialog = ref(false)
const nodeIndex = ref(-1)
const nodeForm = reactive({ id: '', type: 'llm', name: '', configText: '' })
const nodeTypeHint = computed(() => nodeConfigHints[nodeForm.type] || '')

function openNode(row?: FlowNode, index = -1) {
  nodeIndex.value = index
  if (row) {
    Object.assign(nodeForm, {
      id: row.id,
      type: row.type,
      name: row.name,
      configText: row.config ? JSON.stringify(row.config, null, 2) : ''
    })
  } else {
    const hasStart = designNodes.value.some(n => n.type === 'start')
    Object.assign(nodeForm, {
      id: 'node_' + (designNodes.value.length + 1),
      type: hasStart ? 'llm' : 'start',
      name: hasStart ? '' : '开始',
      configText: ''
    })
  }
  nodeDialog.value = true
}

function onNodeTypeChange(type: string) {
  if (!nodeForm.name) nodeForm.name = nodeTypeLabel(type)
}

function saveNode() {
  if (!nodeForm.id || !nodeForm.type || !nodeForm.name) {
    ElMessage.error('节点 ID、类型、名称均为必填')
    return
  }
  let config: Record<string, any> | undefined
  if (nodeForm.configText.trim()) {
    try {
      config = JSON.parse(nodeForm.configText)
    } catch {
      ElMessage.error('节点配置不是合法 JSON')
      return
    }
  }
  const duplicated = designNodes.value.some((n, i) => n.id === nodeForm.id && i !== nodeIndex.value)
  if (duplicated) {
    ElMessage.error('节点 ID 已存在')
    return
  }
  const node: FlowNode = { id: nodeForm.id, type: nodeForm.type, name: nodeForm.name, config }
  if (nodeIndex.value < 0) designNodes.value.push(node)
  else designNodes.value[nodeIndex.value] = node
  nodeDialog.value = false
}

function removeNode(index: number) {
  const removed = designNodes.value[index]
  designNodes.value.splice(index, 1)
  designEdges.value = designEdges.value.filter(e => e.source !== removed.id && e.target !== removed.id)
}

function addEdge() {
  designEdges.value.push({
    source: designNodes.value[0].id,
    target: designNodes.value[1].id,
    condition: ''
  })
}

/** 从 start 节点沿连线走出的主链路，用于直观校验编排 */
const flowChain = computed<FlowNode[]>(() => {
  const start = designNodes.value.find(n => n.type === 'start')
  if (!start) return []
  const chain: FlowNode[] = [start]
  const visited = new Set([start.id])
  let cursor = start.id
  while (true) {
    const next = designEdges.value.find(e => e.source === cursor)
    if (!next || visited.has(next.target)) break
    const node = designNodes.value.find(n => n.id === next.target)
    if (!node) break
    chain.push(node)
    visited.add(node.id)
    cursor = node.id
  }
  return chain
})

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

async function openExecutions(row: any) {
  current.value = row
  detailExecution.value = null
  execDrawer.value = true
  execLoading.value = true
  try {
    executions.value = await workflowApi.executions(row.id)
  } finally {
    execLoading.value = false
  }
}

onMounted(async () => {
  loadList()
  try {
    const nodes = await workflowApi.nodes()
    if (nodes?.length) nodeTypeOptions.value = nodes
  } catch {
    // 无注册节点时保留内置清单
  }
  try {
    applications.value = await applicationApi.list()
  } catch {
    applications.value = []
  }
})
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.section-title { font-weight: 600; color: #303133; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
.opt-desc { float: right; color: #c0c4cc; font-size: 12px; }
.flow-preview { margin-top: 24px; }
.chain { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.chain-node {
  padding: 6px 12px; border-radius: 8px; background: #ecf5ff;
  color: #409eff; font-size: 13px; border: 1px solid #d9ecff;
}
.chain-arrow { color: #c0c4cc; }
</style>
