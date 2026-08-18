<template>
  <div class="designer">
    <header class="designer-header">
      <div class="header-left">
        <el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
        <el-divider direction="vertical" />
        <span class="wf-name">{{ workflow?.name }}</span>
        <el-tag size="small" :type="workflow?.status === 'published' ? 'success' : 'info'" effect="plain">
          {{ workflow?.status === 'published' ? `已发布 v${workflow?.version}` : '草稿' }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-tooltip content="从开始节点拖出连线即可连接下游节点；选中节点/连线后按 Delete 删除；双击节点打开配置" placement="bottom">
          <el-button text><el-icon><QuestionFilled /></el-icon></el-button>
        </el-tooltip>
        <el-button :loading="saving" @click="save(false)">保存</el-button>
        <el-button type="primary" :loading="publishing" @click="save(true)">保存并发布</el-button>
      </div>
    </header>

    <div class="designer-body" v-loading="loading">
      <aside class="palette">
        <div class="palette-title">节点库<span>点击添加或拖拽到画布</span></div>
        <div v-for="t in NODE_TYPES" :key="t.type" class="palette-item"
             @pointerdown="startNodeDrag($event, t.type)">
          <span class="palette-icon" :style="{ background: t.color }">
            <el-icon :size="14"><component :is="t.icon" /></el-icon>
          </span>
          <div class="palette-text">
            <div class="palette-name">{{ t.name }}</div>
            <div class="palette-desc">{{ t.description }}</div>
          </div>
        </div>
      </aside>

      <div class="canvas-wrap" ref="canvasRef">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-edge-options="defaultEdgeOptions"
          :delete-key-code="['Delete', 'Backspace']"
          :min-zoom="0.3"
          :max-zoom="2"
          fit-view-on-init
          @init="onInit"
          @connect="onConnect"
          @edge-click="onEdgeClick"
          @node-double-click="onNodeDblClick"
        >
          <template #node-flow="nodeProps">
            <FlowNodeCard :id="nodeProps.id" :data="nodeProps.data" :selected="nodeProps.selected"
                          @config="openConfig" @remove="removeNode" />
          </template>
          <Background />
          <Controls position="bottom-right" />
        </VueFlow>
      </div>
    </div>

    <!-- 节点配置 -->
    <el-drawer v-model="configDrawer" :title="'节点配置 - ' + getMeta(configType).name" size="440px" destroy-on-close>
      <el-form label-width="90px" label-position="top">
        <el-form-item label="节点名称" required>
          <el-input v-model="configForm.name" placeholder="如：大模型 1" />
        </el-form-item>

        <template v-if="configType === 'llm'">
          <el-form-item label="模型">
            <el-select v-model="configForm.modelId" filterable clearable style="width:100%" placeholder="选择已配置的模型">
              <el-option v-for="m in models" :key="m.id" :label="`${m.name}（${m.modelName || ''}）`" :value="m.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="提示词">
            <el-input v-model="configForm.prompt" type="textarea" :rows="4"
                      placeholder="支持 {变量名} 占位符，如 {question}" />
          </el-form-item>
          <el-form-item :label="`温度（${configForm.temperature}）`">
            <el-slider v-model="configForm.temperature" :min="0" :max="2" :step="0.1" />
          </el-form-item>
        </template>

        <template v-else-if="configType === 'knowledge'">
          <el-form-item label="知识库">
            <el-select v-model="configForm.knowledgeBaseIds" multiple filterable style="width:100%" placeholder="选择知识库">
              <el-option v-for="b in knowledgeBases" :key="b.id" :label="b.name" :value="b.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="召回条数 TopK">
            <el-input-number v-model="configForm.topK" :min="1" :max="50" />
          </el-form-item>
        </template>

        <template v-else-if="configType === 'tool'">
          <el-form-item label="工具">
            <el-select v-model="configForm.toolId" filterable clearable style="width:100%" placeholder="选择已注册的工具">
              <el-option v-for="t in tools" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="工具入参（JSON）">
            <el-input v-model="configForm.inputText" type="textarea" :rows="4" placeholder='如 {"city":"北京"}' />
          </el-form-item>
        </template>

        <template v-else-if="configType === 'condition'">
          <el-form-item label="条件表达式">
            <el-input v-model="configForm.expression" placeholder="如 score > 0.8" />
          </el-form-item>
          <el-alert type="info" :closable="false" show-icon
                    title="满足条件的走带条件标签的分支连线，连线条件在点击连线时设置" />
        </template>

        <template v-else-if="configType === 'loop'">
          <el-form-item label="遍历集合变量">
            <el-input v-model="configForm.items" placeholder="变量名，如 list；为空则按次数循环" />
          </el-form-item>
          <el-form-item label="最大循环次数">
            <el-input-number v-model="configForm.maxIterations" :min="1" :max="1000" />
          </el-form-item>
        </template>

        <template v-else-if="configType === 'api'">
          <el-form-item label="请求方法">
            <el-select v-model="configForm.method" style="width:100%">
              <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="URL">
            <el-input v-model="configForm.url" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="请求体（JSON）">
            <el-input v-model="configForm.bodyText" type="textarea" :rows="4" placeholder="POST/PUT 时有效" />
          </el-form-item>
        </template>

        <template v-else-if="configType === 'code'">
          <el-form-item label="脚本">
            <el-input v-model="configForm.script" type="textarea" :rows="6"
                      placeholder="return { result: input.a + input.b }" />
          </el-form-item>
        </template>

        <template v-else-if="configType === 'end'">
          <el-form-item label="输出变量">
            <el-input v-model="configForm.output" placeholder="作为工作流输出的变量名，如 answer" />
          </el-form-item>
        </template>

        <template v-else-if="configType === 'start'">
          <el-alert type="info" :closable="false" show-icon title="开始节点无需配置，执行输入将注入为初始变量" />
        </template>

        <el-collapse v-if="configType !== 'start'">
          <el-collapse-item title="高级：附加配置（JSON）">
            <el-input v-model="configForm.extraText" type="textarea" :rows="4"
                      placeholder="其余自定义字段，如 {&quot;timeout&quot;: 30}" />
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="configDrawer = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">确定</el-button>
      </template>
    </el-drawer>

    <!-- 拖拽幽灵元素 -->
    <Teleport to="body">
      <div v-if="ghost.show" class="drag-ghost" :style="{ left: ghost.x + 'px', top: ghost.y + 'px' }">
        <span class="ghost-icon" :style="{ background: getMeta(ghost.type).color }">
          <el-icon :size="14"><component :is="getMeta(ghost.type).icon" /></el-icon>
        </span>
        <span class="ghost-name">{{ getMeta(ghost.type).name }}</span>
      </div>
    </Teleport>

    <!-- 连线条件 -->
    <el-dialog v-model="edgeDialog" title="连线条件" width="440px">
      <el-form label-width="80px">
        <el-form-item label="条件标签">
          <el-input v-model="edgeCondition" placeholder="可选，condition 节点按此标签选择分支" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="edgeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveEdgeCondition">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, QuestionFilled } from '@element-plus/icons-vue'
import { VueFlow, MarkerType, type Connection, type Edge, type Node, type VueFlowStore } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import { workflowApi } from '@admin/api/workflow-api'
import { modelApi } from '@admin/api/model-api'
import { knowledgeApi } from '@admin/api/knowledge-api'
import { toolApi } from '@admin/api/tool-api'
import FlowNodeCard from '@admin/components/workflow/FlowNodeCard.vue'
import { NODE_TYPES, getMeta } from '@admin/components/workflow/node-meta'

const route = useRoute()
const router = useRouter()
const workflowId = String(route.params.id)

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const workflow = ref<any>(null)

const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const store = ref<VueFlowStore>()
const canvasRef = ref<HTMLElement | null>(null)

// 拖拽幽灵元素状态（取代 pendingNodeType）
const ghost = reactive({ show: false, x: 0, y: 0, type: '' })

const defaultEdgeOptions = {
  markerEnd: MarkerType.ArrowClosed,
  style: { stroke: '#b1b1b7', strokeWidth: 1.5 }
}

// 节点配置下拉数据源
const models = ref<any[]>([])
const knowledgeBases = ref<any[]>([])
const tools = ref<any[]>([])

function genNodeId(type: string) {
  return `${type}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
}

function safeParse<T>(text: string | undefined | null, fallback: T): T {
  if (!text) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

/** 后端数据 -> 画布数据 */
function loadGraph(wf: any) {
  const rawNodes: any[] = safeParse(wf.nodes, [])
  const rawEdges: any[] = safeParse(wf.edges, [])

  nodes.value = rawNodes.map((n, i) => ({
    id: n.id,
    type: 'flow',
    position: typeof n.position?.x === 'number'
      ? { x: n.position.x, y: n.position.y }
      : { x: 100, y: 100 + i * 150 },
    data: { type: n.type, name: n.name, config: n.config || {} }
  }))

  edges.value = rawEdges
    .filter(e => rawNodes.some(n => n.id === e.source) && rawNodes.some(n => n.id === e.target))
    .map((e, i) => ({
      id: e.id || `edge_${i}_${e.source}_${e.target}`,
      source: e.source,
      target: e.target,
      label: e.condition || '',
      data: { condition: e.condition || '' }
    }))
}

/** 画布数据 -> 后端数据 */
function buildSaveData() {
  const flowNodes = nodes.value.map(n => ({
    id: n.id,
    type: n.data?.type,
    name: n.data?.name || getMeta(n.data?.type).name,
    config: n.data?.config || {},
    position: { x: Math.round(n.position.x), y: Math.round(n.position.y) }
  }))
  const flowEdges = edges.value.map(e => ({
    source: e.source,
    target: e.target,
    condition: (e.data as any)?.condition || ''
  }))
  return { flowNodes, flowEdges }
}

function validate(): string | null {
  if (!nodes.value.length) return '画布为空，请先添加节点'
  const starts = nodes.value.filter(n => n.data?.type === 'start')
  if (starts.length !== 1) return '必须且只能有一个开始节点'
  if (!nodes.value.some(n => n.data?.type === 'end')) return '至少需要一个结束节点'
  if (nodes.value.length > 1) {
    const linked = new Set<string>()
    edges.value.forEach(e => { linked.add(e.source); linked.add(e.target) })
    const orphans = nodes.value.filter(n => !linked.has(n.id))
    if (orphans.length) {
      return `存在未连线的节点：${orphans.map(n => n.data?.name || n.id).join('、')}`
    }
  }
  return null
}

async function save(publish: boolean) {
  const err = validate()
  if (err) {
    ElMessage.error(err)
    return
  }
  const { flowNodes, flowEdges } = buildSaveData()
  if (publish) publishing.value = true
  else saving.value = true
  try {
    const updated = await workflowApi.update(workflowId, {
      name: workflow.value.name,
      description: workflow.value.description,
      applicationId: workflow.value.applicationId,
      variables: workflow.value.variables,
      nodes: JSON.stringify(flowNodes),
      edges: JSON.stringify(flowEdges)
    })
    if (publish) {
      const published = await workflowApi.publish(workflowId)
      workflow.value.status = 'published'
      workflow.value.version = published?.version ?? (workflow.value.version || 1) + 1
      ElMessage.success(`已发布，当前版本 v${workflow.value.version}`)
    } else {
      workflow.value = { ...workflow.value, ...updated }
      ElMessage.success('编排已保存')
    }
  } finally {
    saving.value = false
    publishing.value = false
  }
}

// ============ 画布交互（基于 Pointer Events，兼容 SVG）============

function onInit(instance: VueFlowStore) {
  store.value = instance
}

/** document 级 pointermove — 跟随鼠标移动幽灵元素 */
function onPointerMove(e: PointerEvent) {
  if (!ghost.show) return
  ghost.x = e.clientX + 16
  ghost.y = e.clientY + 16
}

/** 在节点库上按下鼠标/手指 — 开始拖拽，显示幽灵元素 */
function startNodeDrag(e: PointerEvent, type: string) {
  ghost.type = type
  ghost.x = e.clientX + 16
  ghost.y = e.clientY + 16
  ghost.show = true
  ;(e.target as HTMLElement)?.closest('.palette-item')?.classList.add('dragging')
  document.addEventListener('pointermove', onPointerMove)
}

/** document 上的 pointerup — 放置节点，清理幽灵元素 */
function onPointerUp(e: PointerEvent) {
  const type = ghost.type
  const wasShowing = ghost.show
  ghost.show = false
  ghost.type = ''
  document.removeEventListener('pointermove', onPointerMove)
  // 清除所有 palette-item 的 dragging 状态
  document.querySelectorAll('.palette-item.dragging').forEach(el => el.classList.remove('dragging'))
  if (!type || !wasShowing) return

  // 检查释放位置是否在画布区域内
  const canvas = canvasRef.value
  if (!canvas) { addNodeOfType(type); return }
  const rect = canvas.getBoundingClientRect()
  const inCanvas = e.clientX >= rect.left && e.clientX <= rect.right &&
                   e.clientY >= rect.top && e.clientY <= rect.bottom
  const position = inCanvas && store.value
    ? store.value.screenToFlowCoordinate({ x: e.clientX, y: e.clientY })
    : undefined
  addNodeOfType(type, position)
}

function addNodeOfType(type: string, position?: { x: number; y: number }) {
  if (type === 'start' && nodes.value.some(n => n.data?.type === 'start')) {
    ElMessage.warning('开始节点只能有一个')
    return
  }
  const meta = getMeta(type)
  const sameCount = nodes.value.filter(n => n.data?.type === type).length
  nodes.value.push({
    id: genNodeId(type),
    type: 'flow',
    position: position || { x: 150 + (sameCount % 3) * 260, y: 120 + (sameCount % 4) * 170 },
    data: { type, name: sameCount ? `${meta.name} ${sameCount + 1}` : meta.name, config: { ...meta.defaultConfig } }
  })
}

function removeNode(nodeId: string) {
  const idx = nodes.value.findIndex(n => n.id === nodeId)
  if (idx < 0) return
  nodes.value.splice(idx, 1)
  edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
}

function onConnect(connection: Connection) {
  if (!connection.source || !connection.target || connection.source === connection.target) return
  const duplicated = edges.value.some(e => e.source === connection.source && e.target === connection.target)
  if (duplicated) {
    ElMessage.warning('这两个节点之间已存在连线')
    return
  }
  edges.value.push({
    id: `edge_${Date.now().toString(36)}_${edges.value.length}`,
    source: connection.source,
    target: connection.target,
    label: '',
    data: { condition: '' }
  })
}

function onNodeDblClick(payload: any) {
  openConfig(payload?.node?.id)
}

// ============ 节点配置 ============

const configDrawer = ref(false)
const configNodeId = ref('')
const configType = ref('start')
const configForm = reactive<Record<string, any>>({
  name: '', modelId: '', prompt: '', temperature: 0.7,
  knowledgeBaseIds: [], topK: 5, toolId: '', inputText: '',
  expression: '', items: '', maxIterations: 10,
  url: '', method: 'GET', bodyText: '', script: '', output: '', extraText: ''
})

const STRUCTURED_KEYS: Record<string, string[]> = {
  llm: ['modelId', 'prompt', 'temperature'],
  knowledge: ['knowledgeBaseIds', 'topK'],
  tool: ['toolId', 'input'],
  condition: ['expression'],
  loop: ['items', 'maxIterations'],
  api: ['url', 'method', 'body'],
  code: ['script'],
  end: ['output']
}

function openConfig(nodeId: string) {
  if (!nodeId) return
  const node = nodes.value.find(n => n.id === nodeId)
  if (!node) return
  configNodeId.value = nodeId
  configType.value = node.data?.type || 'start'
  const config = node.data?.config || {}

  Object.assign(configForm, {
    name: node.data?.name || '',
    modelId: config.modelId || '', prompt: config.prompt || '', temperature: config.temperature ?? 0.7,
    knowledgeBaseIds: config.knowledgeBaseIds || [], topK: config.topK ?? 5,
    toolId: config.toolId || '', inputText: config.input ? JSON.stringify(config.input, null, 2) : '',
    expression: config.expression || '', items: config.items || '', maxIterations: config.maxIterations ?? 10,
    url: config.url || '', method: config.method || 'GET',
    bodyText: config.body ? JSON.stringify(config.body, null, 2) : '',
    script: config.script || '', output: config.output || ''
  })

  // 结构化字段之外的其余字段放到"高级"里
  const structured = STRUCTURED_KEYS[configType.value] || []
  const extra: Record<string, any> = {}
  Object.keys(config).forEach(k => {
    if (!structured.includes(k)) extra[k] = config[k]
  })
  configForm.extraText = Object.keys(extra).length ? JSON.stringify(extra, null, 2) : ''
  configDrawer.value = true
}

function saveConfig() {
  const node = nodes.value.find(n => n.id === configNodeId.value)
  if (!node) return
  if (!configForm.name.trim()) {
    ElMessage.error('请输入节点名称')
    return
  }
  let config: Record<string, any> = {}
  const t = configType.value
  if (t === 'llm') {
    config = { modelId: configForm.modelId, prompt: configForm.prompt, temperature: configForm.temperature }
  } else if (t === 'knowledge') {
    config = { knowledgeBaseIds: configForm.knowledgeBaseIds, topK: configForm.topK }
  } else if (t === 'tool') {
    config = { toolId: configForm.toolId }
    if (configForm.inputText.trim()) {
      try {
        config.input = JSON.parse(configForm.inputText)
      } catch {
        ElMessage.error('工具入参不是合法 JSON')
        return
      }
    }
  } else if (t === 'condition') {
    config = { expression: configForm.expression }
  } else if (t === 'loop') {
    config = { items: configForm.items, maxIterations: configForm.maxIterations }
  } else if (t === 'api') {
    config = { url: configForm.url, method: configForm.method }
    if (configForm.bodyText.trim()) {
      try {
        config.body = JSON.parse(configForm.bodyText)
      } catch {
        ElMessage.error('请求体不是合法 JSON')
        return
      }
    }
  } else if (t === 'code') {
    config = { script: configForm.script }
  } else if (t === 'end') {
    config = { output: configForm.output }
  }

  if (configForm.extraText.trim()) {
    try {
      config = { ...config, ...JSON.parse(configForm.extraText) }
    } catch {
      ElMessage.error('附加配置不是合法 JSON')
      return
    }
  }

  node.data = { ...node.data, name: configForm.name.trim(), config }
  configDrawer.value = false
}

// ============ 连线条件 ============

const edgeDialog = ref(false)
const edgeId = ref('')
const edgeCondition = ref('')

function onEdgeClick(payload: any) {
  const edge = payload?.edge ?? payload
  if (!edge?.id) return
  edgeId.value = edge.id
  edgeCondition.value = (edge.data as any)?.condition || ''
  edgeDialog.value = true
}

function saveEdgeCondition() {
  const edge = edges.value.find(e => e.id === edgeId.value)
  if (edge) {
    edge.label = edgeCondition.value
    edge.data = { ...(edge.data || {}), condition: edgeCondition.value }
  }
  edgeDialog.value = false
}

// ============ 初始化 ============

function goBack() {
  router.push('/workflow')
}

onMounted(async () => {
  loading.value = true
  try {
    workflow.value = await workflowApi.get(workflowId)
    loadGraph(workflow.value)
    // 全新工作流自动放一个开始节点
    if (!nodes.value.length) {
      addNodeOfType('start', { x: 150, y: 200 })
    }
    nextTick(() => store.value?.fitView({ padding: 0.25 }))
  } finally {
    loading.value = false
  }

  try { models.value = await modelApi.list() } catch { models.value = [] }
  try { knowledgeBases.value = await knowledgeApi.list() } catch { knowledgeBases.value = [] }
  try { tools.value = await toolApi.list() } catch { tools.value = [] }

  // 注册 Pointer-based 拖拽（不依赖 HTML5 DnD，兼容 SVG 画布）
  document.addEventListener('pointerup', onPointerUp)
  document.addEventListener('pointermove', onPointerMove)
})

onUnmounted(() => {
  document.removeEventListener('pointerup', onPointerUp)
  document.removeEventListener('pointermove', onPointerMove)
})
</script>

<style lang="scss" scoped>
.designer {
  display: flex; flex-direction: column; height: 100vh; background: #f5f7fa;

  .designer-header {
    display: flex; justify-content: space-between; align-items: center;
    padding: 0 16px; height: 52px; background: #fff; border-bottom: 1px solid #e4e7ed; flex-shrink: 0;

    .header-left { display: flex; align-items: center; gap: 8px; }
    .wf-name { font-size: 15px; font-weight: 600; color: #303133; }
    .header-right { display: flex; align-items: center; gap: 8px; }
  }

  .designer-body { flex: 1; display: flex; overflow: hidden; }

  .palette {
    width: 230px; background: #fff; border-right: 1px solid #e4e7ed;
    padding: 12px; overflow-y: auto; flex-shrink: 0;

    .palette-title {
      font-weight: 600; color: #303133; margin-bottom: 10px;
      span { font-weight: 400; font-size: 12px; color: #909399; margin-left: 8px; }
    }

    .palette-item {
      display: flex; gap: 8px; align-items: center; padding: 8px;
      border: 1px solid #ebeef5; border-radius: 8px; margin-bottom: 8px;
      cursor: grab; user-select: none; transition: all 0.2s;
      touch-action: none; /* 防止 touch 滚动干扰拖拽 */

      &:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15); }

      &.dragging { opacity: 0.7; transform: scale(0.95); }

      .palette-icon {
        width: 28px; height: 28px; border-radius: 6px; color: #fff; flex-shrink: 0;
        display: flex; align-items: center; justify-content: center;
      }
      .palette-name { font-size: 13px; font-weight: 500; color: #303133; }
      .palette-desc { font-size: 11px; color: #909399; line-height: 1.4; }
    }
  }

  .canvas-wrap { flex: 1; position: relative; }
}

:deep(.vue-flow__edge-textbg) { fill: #fff; }
:deep(.vue-flow__edge-text) { fill: #606266; font-size: 12px; }
</style>

<style>
/* 拖拽幽灵（Teleport 到 body，需全局样式） */
.drag-ghost {
  position: fixed;
  pointer-events: none;
  z-index: 99999;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: rgba(255,255,255,0.95);
  border: 2px solid #409eff;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  transform: translate(-8px, -8px);
  transition: none;
}
.ghost-icon {
  width: 24px; height: 24px; border-radius: 6px; color: #fff;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
</style>
