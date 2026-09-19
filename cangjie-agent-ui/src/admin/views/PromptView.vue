<template>
  <div class="prompt-view">
    <el-card>
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane v-for="t in tabs" :key="t.key" :label="t.label" :name="t.key" />
      </el-tabs>

      <!-- 记忆：独立的长期记忆管理（自动提取/手工录入），与其他通用 CRUD 资源不同 -->
      <MemoryManager v-if="activeTab === 'memory'" />

      <template v-else>
      <QueryBar :loading="loading" @search="reload" @reset="resetQuery">
        <el-input v-model="keyword" placeholder="搜索名称..." clearable style="width: 220px"
                  @keyup.enter="reload">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #extra>
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 新建{{ currentTab.label }}
          </el-button>
        </template>
      </QueryBar>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column v-for="col in columns" :key="col.prop"
                         :label="col.label" :prop="col.kind ? undefined : col.prop"
                         :width="col.width" :min-width="col.minWidth"
                         :align="col.align || 'left'"
                         :show-overflow-tooltip="col.ellipsis">
          <template v-if="col.kind" #default="{ row }">
            <el-tag v-if="col.kind === 'status'" size="small" :type="row[col.prop] === 'active' ? 'success' : 'info'">
              {{ row[col.prop] === 'active' ? '激活' : '停用' }}
            </el-tag>
            <template v-else-if="col.kind === 'bool'">
              <el-tag v-if="row[col.prop]" size="small" type="warning">是</el-tag>
              <span v-else>-</span>
            </template>
            <el-tag v-else-if="col.kind === 'dict'" size="small">
              {{ dictLabel(col.dict!, row[col.prop]) }}
            </el-tag>
            <code v-else-if="col.kind === 'code'" class="cmd">{{ row[col.prop] }}</code>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="activeTab === 'template'" size="small" link type="success" @click="openRender(row)">预览</el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
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
      </template>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="showDialog" :title="(editing ? '编辑' : '新建') + currentTab.label" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <!-- 提示词模板 -->
        <template v-if="activeTab === 'template'">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="分类">
            <el-select v-model="form.category" allow-create filterable style="width:100%" placeholder="如 通用 / 客服 / 编程">
              <el-option v-for="c in templateCategories" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容" prop="content">
            <el-input v-model="form.content" type="textarea" :rows="6"
                      :placeholder="templatePlaceholder" />
          </el-form-item>
          <el-form-item label="变量">
            <el-input v-model="form.variables" placeholder='JSON 数组，如 ["角色","问题"]' />
            <div class="hint">
              内容中检测到：{{ detectedVariables.join('、') || '无' }}
              <el-button link type="primary" size="small" @click="fillVariables">回填</el-button>
            </div>
          </el-form-item>
          <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="设为默认"><el-switch v-model="form.isDefault" /></el-form-item>
        </template>

        <!-- Skill -->
        <template v-else-if="activeTab === 'skill'">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="类型" prop="type">
            <el-select v-model="form.type" style="width:100%">
              <el-option v-for="t in skillTypes" :key="t.code" :label="t.label" :value="t.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="函数名">
            <el-input v-model="form.functionName" placeholder="Function Calling 暴露的函数名，如 search_order" />
          </el-form-item>
          <el-form-item label="内容" prop="content">
            <el-input v-model="form.content" type="textarea" :rows="6" placeholder="技能提示词 / 脚本内容" />
          </el-form-item>
          <el-form-item label="参数">
            <el-input v-model="form.parameters" type="textarea" :rows="4"
                      placeholder='JSON Schema，如 {"type":"object","properties":{"orderId":{"type":"string"}}}' />
          </el-form-item>
          <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        </template>

        <!-- 规则 -->
        <template v-else-if="activeTab === 'rule'">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="类型" prop="type">
            <el-select v-model="form.type" style="width:100%">
              <el-option v-for="t in ruleTypes" :key="t.code" :label="t.label" :value="t.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="条件" prop="condition">
            <el-input v-model="form.condition" type="textarea" :rows="3"
                      placeholder="表达式或关键字，如 message contains '退款'" />
          </el-form-item>
          <el-form-item label="动作" prop="action">
            <el-input v-model="form.action" type="textarea" :rows="3" placeholder="命中后的处理，如 reject / 追加提示词" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-input-number v-model="form.priority" :min="0" :max="999" />
            <span class="hint-inline">数值越小越先执行</span>
          </el-form-item>
          <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        </template>

        <!-- 命令 -->
        <template v-else>
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="命令" prop="command">
            <el-input v-model="form.command" placeholder="如 /summary、/translate" />
          </el-form-item>
          <el-form-item label="类型" prop="type">
            <el-select v-model="form.type" style="width:100%">
              <el-option v-for="t in commandTypes" :key="t.code" :label="t.label" :value="t.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="脚本" prop="script">
            <el-input v-model="form.script" type="textarea" :rows="6" placeholder="提示词内容或脚本体" />
          </el-form-item>
          <el-form-item label="参数">
            <el-input v-model="form.parameters" type="textarea" :rows="3" placeholder="JSON Schema" />
          </el-form-item>
          <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        </template>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">激活</el-radio>
            <el-radio value="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模板变量填充预览 -->
    <el-dialog v-model="renderDialog" title="模板预览" width="620px" destroy-on-close>
      <el-empty v-if="renderVars.length === 0" description="该模板没有变量占位符" :image-size="60" />
      <el-form v-else label-width="120px">
        <el-form-item v-for="v in renderVars" :key="v" :label="v">
          <el-input v-model="renderValues[v]" :placeholder="'输入 ' + v" />
        </el-form-item>
      </el-form>
      <el-divider>渲染结果</el-divider>
      <div class="render-result">{{ renderedContent }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import { promptApi, type PromptResource } from '@admin/api/prompt-api'
import MemoryManager from '@admin/components/MemoryManager.vue'

/** Tab 标识：memory 为独立的长期记忆管理，其余走通用 CRUD */
type TabKey = PromptResource | 'memory'

interface Col {
  label: string
  prop: string
  width?: number
  minWidth?: number
  align?: 'left' | 'center'
  ellipsis?: boolean
  kind?: 'status' | 'bool' | 'dict' | 'code'
  dict?: DictName
}
type DictName = 'skillType' | 'ruleType' | 'commandType'

const tabs: { key: TabKey; label: string }[] = [
  { key: 'template', label: '提示词模板' },
  { key: 'skill', label: 'Skill 技能' },
  { key: 'memory', label: '记忆' },
  { key: 'rule', label: '规则' },
  { key: 'command', label: '命令' }
]

const dicts: Record<DictName, { code: string; label: string }[]> = {
  skillType: [
    { code: 'prompt', label: '提示词型' },
    { code: 'function', label: 'Function Call' },
    { code: 'workflow', label: '工作流型' }
  ],
  ruleType: [
    { code: 'input', label: '输入校验' },
    { code: 'output', label: '输出过滤' },
    { code: 'route', label: '路由分发' },
    { code: 'security', label: '安全策略' }
  ],
  commandType: [
    { code: 'prompt', label: '提示词' },
    { code: 'script', label: '脚本' },
    { code: 'tool', label: '工具调用' }
  ]
}
const skillTypes = dicts.skillType
const ruleTypes = dicts.ruleType
const commandTypes = dicts.commandType
const templateCategories = ['通用', '客服', '编程', '写作', '分析', 'RAG']
// 含 mustache 占位符，放在脚本中避免被模板编译器解析
const templatePlaceholder = '支持 {{变量名}} 占位符，例如：你是一名{{角色}}，请回答：{{问题}}'

function dictLabel(name: DictName, code: string) {
  return dicts[name].find(d => d.code === code)?.label || code || '-'
}

const columnsMap: Record<PromptResource, Col[]> = {
  template: [
    { label: '名称', prop: 'name', minWidth: 150 },
    { label: '分类', prop: 'category', width: 110 },
    { label: '内容', prop: 'content', minWidth: 280, ellipsis: true },
    { label: '变量', prop: 'variables', width: 170, ellipsis: true },
    { label: '默认', prop: 'isDefault', width: 80, align: 'center', kind: 'bool' },
    { label: '状态', prop: 'status', width: 90, align: 'center', kind: 'status' }
  ],
  skill: [
    { label: '名称', prop: 'name', minWidth: 150 },
    { label: '类型', prop: 'type', width: 130, align: 'center', kind: 'dict', dict: 'skillType' },
    { label: '函数名', prop: 'functionName', minWidth: 150 },
    { label: '描述', prop: 'description', minWidth: 240, ellipsis: true },
    { label: '状态', prop: 'status', width: 90, align: 'center', kind: 'status' }
  ],
  rule: [
    { label: '名称', prop: 'name', minWidth: 140 },
    { label: '类型', prop: 'type', width: 120, align: 'center', kind: 'dict', dict: 'ruleType' },
    { label: '条件', prop: 'condition', minWidth: 200, ellipsis: true },
    { label: '动作', prop: 'action', minWidth: 200, ellipsis: true },
    { label: '优先级', prop: 'priority', width: 90, align: 'center' },
    { label: '状态', prop: 'status', width: 90, align: 'center', kind: 'status' }
  ],
  command: [
    { label: '名称', prop: 'name', minWidth: 140 },
    { label: '命令', prop: 'command', width: 160, kind: 'code' },
    { label: '类型', prop: 'type', width: 120, align: 'center', kind: 'dict', dict: 'commandType' },
    { label: '描述', prop: 'description', minWidth: 220, ellipsis: true },
    { label: '状态', prop: 'status', width: 90, align: 'center', kind: 'status' }
  ]
}

const activeTab = ref<TabKey>('template')
const currentTab = computed(() => tabs.find(t => t.key === activeTab.value)!)
const columns = computed(() => columnsMap[activeTab.value as PromptResource])

const list = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const showDialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Record<string, any>>({})

const rulesMap: Record<PromptResource, FormRules> = {
  template: {
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
    content: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
  },
  skill: {
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
    type: [{ required: true, message: '请选择类型', trigger: 'change' }],
    content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
  },
  rule: {
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
    type: [{ required: true, message: '请选择类型', trigger: 'change' }],
    condition: [{ required: true, message: '请输入条件', trigger: 'blur' }],
    action: [{ required: true, message: '请输入动作', trigger: 'blur' }]
  },
  command: {
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
    command: [{ required: true, message: '请输入命令', trigger: 'blur' }],
    type: [{ required: true, message: '请选择类型', trigger: 'change' }],
    script: [{ required: true, message: '请输入脚本', trigger: 'blur' }]
  }
}
const rules = computed(() => rulesMap[activeTab.value as PromptResource])

const defaultsMap: Record<PromptResource, Record<string, any>> = {
  template: { name: '', category: '通用', content: '', description: '', variables: '', isDefault: false, status: 'active' },
  skill: { name: '', type: 'prompt', content: '', functionName: '', parameters: '', description: '', status: 'active' },
  rule: { name: '', type: 'input', condition: '', action: '', priority: 100, description: '', status: 'active' },
  command: { name: '', command: '', type: 'prompt', script: '', parameters: '', description: '', status: 'active' }
}

async function loadList() {
  loading.value = true
  try {
    const page = await promptApi.of(activeTab.value as PromptResource).list({
      keyword: keyword.value, pageNum: pageNum.value, pageSize: pageSize.value
    })
    list.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

/** 搜索：回到第一页 */
function reload() {
  pageNum.value = 1
  loadList()
}

/** 重置筛选条件并重新查询 */
function resetQuery() {
  keyword.value = ''
  reload()
}

function onTabChange() {
  // 记忆 tab 由 MemoryManager 自管理数据，不加载通用列表
  if (activeTab.value === 'memory') return
  keyword.value = ''
  pageNum.value = 1
  loadList()
}

function resetForm(source?: any) {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, { id: '', ...defaultsMap[activeTab.value as PromptResource] }, source || {})
}

function openCreate() {
  editing.value = false
  resetForm()
  showDialog.value = true
}

function openEdit(row: any) {
  editing.value = true
  resetForm(row)
  showDialog.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const api = promptApi.of(activeTab.value as PromptResource)
    const { id, ...payload } = form
    if (editing.value) {
      await api.update(id, payload)
      ElMessage.success('更新成功')
    } else {
      await api.create(payload)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  await promptApi.of(activeTab.value as PromptResource).remove(id)
  ElMessage.success('删除成功')
  loadList()
}

// 模板变量解析：{{var}}
const VAR_RE = /\{\{\s*([\w\u4e00-\u9fa5.]+)\s*\}\}/g
function parseVariables(content: string): string[] {
  const set = new Set<string>()
  let m: RegExpExecArray | null
  VAR_RE.lastIndex = 0
  while ((m = VAR_RE.exec(content || '')) !== null) set.add(m[1])
  return Array.from(set)
}

const detectedVariables = computed(() => parseVariables(form.content))
function fillVariables() {
  form.variables = JSON.stringify(detectedVariables.value)
}

const renderDialog = ref(false)
const renderVars = ref<string[]>([])
const renderValues = ref<Record<string, string>>({})
const renderTemplate = ref('')
const renderedContent = computed(() =>
  renderTemplate.value.replace(/\{\{\s*([\w\u4e00-\u9fa5.]+)\s*\}\}/g,
    (_all, k: string) => renderValues.value[k] || '{{' + k + '}}'))

function openRender(row: any) {
  renderTemplate.value = row.content || ''
  renderVars.value = parseVariables(renderTemplate.value)
  renderValues.value = {}
  renderDialog.value = true
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
.hint-inline { margin-left: 10px; font-size: 12px; color: #909399; }
.cmd { background: #f4f4f5; padding: 2px 6px; border-radius: 4px; color: #e6a23c; }
.render-result { white-space: pre-wrap; line-height: 1.8; background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; min-height: 60px; }
</style>
