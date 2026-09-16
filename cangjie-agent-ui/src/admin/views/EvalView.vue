<template>
  <div class="eval-view">
    <el-row :gutter="16">
      <!-- 左侧：数据集列表 -->
      <el-col :xs="24" :sm="24" :md="8" :lg="8">
        <el-card class="dataset-card">
          <template #header>
            <div class="card-header">
              <span>评估数据集</span>
              <el-button type="primary" size="small" @click="openDatasetDialog()">
                <el-icon><Plus /></el-icon> 新建
              </el-button>
            </div>
          </template>
          <div v-loading="loading">
            <div v-for="ds in datasets" :key="ds.id" class="dataset-item"
                 :class="{ active: current?.id === ds.id }" @click="selectDataset(ds)">
              <div class="ds-main">
                <div class="ds-name">{{ ds.name }}</div>
                <div class="ds-desc">{{ ds.description || '暂无描述' }}</div>
              </div>
              <div class="ds-meta">
                <el-tag size="small" type="info" effect="plain">{{ ds.caseCount ?? 0 }} 用例</el-tag>
                <el-dropdown trigger="click" @command="(cmd) => onDatasetCmd(cmd, ds)">
                  <el-button link size="small" @click.stop><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
            <el-empty v-if="!datasets.length" description="暂无数据集，点击右上角新建" :image-size="80" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：用例管理 + 运行 -->
      <el-col :xs="24" :sm="24" :md="16" :lg="16" class="case-col">
        <el-card v-if="current">
          <template #header>
            <div class="card-header">
              <span>{{ current.name }} — 测试用例</span>
              <div>
                <el-button size="small" @click="openRunDialog">运行评估</el-button>
                <el-button type="primary" size="small" @click="openCaseDialog()">
                  <el-icon><Plus /></el-icon> 添加用例
                </el-button>
              </div>
            </div>
          </template>
          <el-table :data="cases" v-loading="caseLoading" stripe size="small">
            <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="expectedAnswer" label="期望回答" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="openCaseDialog(row)">编辑</el-button>
                <el-popconfirm title="确定删除该用例？" @confirm="deleteCase(row.id)">
                  <template #reference><el-button link size="small" type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card v-else>
          <el-empty description="请选择左侧数据集，或先新建一个数据集" :image-size="100" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据集编辑 -->
    <el-dialog v-model="dsDialog" :title="editingDs ? '编辑数据集' : '新建数据集'" width="520px" destroy-on-close>
      <el-form :model="dsForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="dsForm.name" placeholder="如：合同问答测试集" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dsForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-select v-model="dsForm.knowledgeBaseId" filterable clearable style="width:100%"
                     placeholder="用于召回率评估">
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDataset">保存</el-button>
      </template>
    </el-dialog>

    <!-- 用例编辑 -->
    <el-dialog v-model="caseDialog" :title="editingCase ? '编辑用例' : '添加用例'" width="560px" destroy-on-close>
      <el-form :model="caseForm" label-width="90px">
        <el-form-item label="问题" required>
          <el-input v-model="caseForm.question" type="textarea" :rows="2" placeholder="测试问题" />
        </el-form-item>
        <el-form-item label="期望回答">
          <el-input v-model="caseForm.expectedAnswer" type="textarea" :rows="2"
                    placeholder="用于正确性评估（LLM judge）" />
        </el-form-item>
        <el-form-item label="参考文档ID">
          <el-input v-model="caseForm.referenceDocsText" type="textarea" :rows="3"
                    placeholder="每行一个文档 ID，用于召回率计算" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCase">保存</el-button>
      </template>
    </el-dialog>

    <!-- 运行配置 -->
    <el-dialog v-model="runDialog" title="运行评估" width="480px" destroy-on-close>
      <el-form :model="runForm" label-width="110px">
        <el-form-item label="回答模型">
          <el-select v-model="runForm.modelId" filterable clearable style="width:100%"
                     placeholder="不选则只测检索召回率">
            <el-option v-for="m in models" :key="m.id" :label="`${m.name}（${m.modelName || ''}）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="召回条数 TopK">
          <el-input-number v-model="runForm.topK" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialog = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="startRun">开始评估</el-button>
      </template>
    </el-dialog>

    <!-- 运行报告 -->
    <el-drawer v-model="reportDrawer" :title="'评估报告 - ' + (current?.name || '')" size="780px" destroy-on-close>
      <div v-if="report" class="report-body">
        <el-alert v-if="report.status === 'failed'" type="error" :closable="false"
                  :title="report.errorMessage || '评估失败'" class="report-error" />
        <el-progress :percentage="report.progress ?? 0"
                     :status="report.status === 'failed' ? 'exception' : (report.progress >= 100 ? 'success' : '')"
                     class="report-progress" />
        <el-row :gutter="12" class="summary-row">
          <el-col :xs="12" :sm="6"><div class="metric-card"><div class="metric-value">{{ fmt(summary.avgRecall) }}</div><div class="metric-label">平均召回率</div></div></el-col>
          <el-col :xs="12" :sm="6"><div class="metric-card"><div class="metric-value">{{ fmt(summary.avgCorrectness) }}</div><div class="metric-label">平均正确性</div></div></el-col>
          <el-col :xs="12" :sm="6"><div class="metric-card"><div class="metric-value">{{ summary.avgLatency != null ? Math.round(summary.avgLatency) + 'ms' : '-' }}</div><div class="metric-label">平均耗时</div></div></el-col>
          <el-col :xs="12" :sm="6"><div class="metric-card"><div class="metric-value">{{ summary.totalTokens ?? 0 }}</div><div class="metric-label">总 Token</div></div></el-col>
        </el-row>
        <el-table :data="resultList" size="small" stripe max-height="480">
          <el-table-column prop="question" label="问题" min-width="170" show-overflow-tooltip />
          <el-table-column prop="answer" label="回答" min-width="170" show-overflow-tooltip />
          <el-table-column label="召回" width="80" align="center">
            <template #default="{ row }">{{ fmt(row.recall) }}</template>
          </el-table-column>
          <el-table-column label="正确性" width="90" align="center">
            <template #default="{ row }">{{ row.correctness != null ? fmt(row.correctness) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="latency" label="耗时(ms)" width="90" align="center" />
        </el-table>
      </div>
      <div v-else class="report-loading" v-loading="true"></div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, MoreFilled } from '@element-plus/icons-vue'
import { evalApi } from '@admin/api/eval-api'
import { modelApi } from '@admin/api/model-api'
import { knowledgeApi } from '@admin/api/knowledge-api'

const loading = ref(false)
const datasets = ref<any[]>([])
const current = ref<any>(null)

const cases = ref<any[]>([])
const caseLoading = ref(false)

const knowledgeBases = ref<any[]>([])
const models = ref<any[]>([])

// ============ 数据集 ============
const dsDialog = ref(false)
const editingDs = ref(false)
const dsForm = reactive({ id: '', name: '', description: '', knowledgeBaseId: '' })

async function loadDatasets() {
  loading.value = true
  try {
    datasets.value = await evalApi.listDatasets()
    if (current.value) {
      current.value = datasets.value.find(d => d.id === current.value?.id) || null
    }
  } finally {
    loading.value = false
  }
}

function selectDataset(ds: any) {
  current.value = ds
  loadCases(ds.id)
}

async function loadCases(datasetId: string) {
  caseLoading.value = true
  try {
    cases.value = await evalApi.listCases(datasetId)
  } finally {
    caseLoading.value = false
  }
}

function openDatasetDialog(ds?: any) {
  editingDs.value = !!ds
  Object.assign(dsForm, {
    id: ds?.id || '',
    name: ds?.name || '',
    description: ds?.description || '',
    knowledgeBaseId: ds?.knowledgeBaseId || ''
  })
  dsDialog.value = true
}

function onDatasetCmd(cmd: string, ds: any) {
  if (cmd === 'edit') openDatasetDialog(ds)
  if (cmd === 'delete') deleteDataset(ds)
}

async function saveDataset() {
  if (!dsForm.name.trim()) {
    ElMessage.error('请输入数据集名称')
    return
  }
  const payload = {
    name: dsForm.name.trim(),
    description: dsForm.description,
    knowledgeBaseId: dsForm.knowledgeBaseId || null
  }
  if (editingDs.value) {
    await evalApi.updateDataset(dsForm.id, payload)
    ElMessage.success('更新成功')
  } else {
    await evalApi.createDataset(payload)
    ElMessage.success('创建成功')
  }
  dsDialog.value = false
  loadDatasets()
}

async function deleteDataset(ds: any) {
  await ElMessage.confirm(`确定删除数据集「${ds.name}」及其用例吗？`, '提示', { type: 'warning' })
  await evalApi.deleteDataset(ds.id)
  if (current.value?.id === ds.id) {
    current.value = null
    cases.value = []
  }
  ElMessage.success('删除成功')
  loadDatasets()
}

// ============ 用例 ============
const caseDialog = ref(false)
const editingCase = ref(false)
const caseForm = reactive({ id: '', question: '', expectedAnswer: '', referenceDocsText: '' })

function openCaseDialog(c?: any) {
  editingCase.value = !!c
  Object.assign(caseForm, {
    id: c?.id || '',
    question: c?.question || '',
    expectedAnswer: c?.expectedAnswer || '',
    referenceDocsText: toTextLines(c?.referenceDocs)
  })
  caseDialog.value = true
}

function toTextLines(json?: string): string {
  if (!json) return ''
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.join('\n') : ''
  } catch {
    return ''
  }
}

function toJsonList(text: string): string {
  const ids = text.split('\n').map(s => s.trim()).filter(Boolean)
  return ids.length ? JSON.stringify(ids) : '[]'
}

async function saveCase() {
  if (!current.value) return
  if (!caseForm.question.trim()) {
    ElMessage.error('请输入问题')
    return
  }
  const payload = {
    question: caseForm.question.trim(),
    expectedAnswer: caseForm.expectedAnswer,
    referenceDocs: toJsonList(caseForm.referenceDocsText)
  }
  if (editingCase.value) {
    await evalApi.updateCase(caseForm.id, payload)
    ElMessage.success('更新成功')
  } else {
    await evalApi.createCase(current.value.id, payload)
    ElMessage.success('添加成功')
  }
  caseDialog.value = false
  loadCases(current.value.id)
  loadDatasets()
}

async function deleteCase(id: string) {
  await evalApi.deleteCase(id)
  ElMessage.success('删除成功')
  if (current.value) loadCases(current.value.id)
  loadDatasets()
}

// ============ 运行评估 ============
const runDialog = ref(false)
const running = ref(false)
const runForm = reactive({ modelId: '', topK: 5 })

const reportDrawer = ref(false)
const report = ref<any>(null)
const summary = reactive<Record<string, any>>({})
const resultList = ref<any[]>([])
let pollTimer: any = null

function openRunDialog() {
  if (!cases.value.length) {
    ElMessage.warning('该数据集暂无用例，请先添加用例')
    return
  }
  Object.assign(runForm, { modelId: '', topK: 5 })
  runDialog.value = true
}

async function startRun() {
  if (!current.value) return
  running.value = true
  try {
    const config: Record<string, any> = { topK: runForm.topK }
    if (runForm.modelId) config.modelId = runForm.modelId
    const run = await evalApi.run(current.value.id, config)
    runDialog.value = false
    reportDrawer.value = true
    report.value = null
    resultList.value = []
    Object.keys(summary).forEach(k => delete summary[k])
    pollReport(run.id)
  } finally {
    running.value = false
  }
}

function pollReport(runId: string) {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = setTimeout(async () => {
    try {
      const run = await evalApi.getRun(runId)
      report.value = run
      if (run.summary) Object.assign(summary, safeParse(run.summary, {}))
      if (run.results) resultList.value = safeParse(run.results, [])
      if (run.status === 'running' || run.status === 'pending') {
        pollReport(runId)
      }
    } catch {
      pollReport(runId)
    }
  }, 1500)
}

function safeParse<T>(text: string | undefined | null, fallback: T): T {
  if (!text) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

function fmt(v: any) {
  return v == null || isNaN(v) ? '-' : (Number(v) * 100).toFixed(1) + '%'
}

onMounted(async () => {
  loadDatasets()
  const [m, k] = await Promise.allSettled([modelApi.list(), knowledgeApi.list()])
  if (m.status === 'fulfilled') models.value = m.value
  if (k.status === 'fulfilled') knowledgeBases.value = k.value
})
</script>

<style lang="scss" scoped>
.card-header {
  display: flex; justify-content: space-between; align-items: center;
}
.dataset-card {
  .dataset-item {
    display: flex; justify-content: space-between; align-items: center;
    padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 8px;
    margin-bottom: 8px; cursor: pointer; transition: all 0.2s;

    &:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64,158,255,.12); }
    &.active { border-color: #409eff; background: #ecf5ff; }

    .ds-main { flex: 1; min-width: 0; margin-right: 8px; }
    .ds-name { font-size: 13px; font-weight: 600; color: #303133; }
    .ds-desc {
      font-size: 12px; color: #909399; margin-top: 2px;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .ds-meta { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
  }
}
.report-body { padding: 4px 8px; }
.report-error { margin-bottom: 12px; }
.report-progress { margin-bottom: 16px; }
.summary-row { margin-bottom: 16px; }
.metric-card {
  background: #f7f8fa; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 14px 8px; text-align: center;
  .metric-value { font-size: 20px; font-weight: 700; color: #303133; }
  .metric-label { font-size: 12px; color: #909399; margin-top: 4px; }
}
.report-loading { height: 200px; }

@media (max-width: 992px) {
  .case-col { margin-top: 16px; }
}
@media (max-width: 768px) {
  .metric-card { margin-bottom: 12px; padding: 12px 4px; }
  .metric-card .metric-value { font-size: 18px; }
}
</style>
