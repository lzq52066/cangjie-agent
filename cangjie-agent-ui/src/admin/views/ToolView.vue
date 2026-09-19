<template>
  <div class="tool-view">
    <el-card>
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="工具" name="tool" />
        <el-tab-pane label="插件" name="plugin" />
      </el-tabs>

      <QueryBar :loading="loading" @search="reload" @reset="resetQuery">
        <el-input v-model="keyword" placeholder="搜索名称..." clearable style="width: 200px"
                  @keyup.enter="reload">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 150px">
          <el-option v-for="t in currentTypes" :key="t.code" :label="t.label" :value="t.code" />
        </el-select>
        <template #extra>
          <el-button v-if="activeTab === 'plugin'" :loading="scanning" @click="handleScan">
            <el-icon><Refresh /></el-icon> 扫描插件
          </el-button>
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> {{ activeTab === 'tool' ? '新建工具' : '注册插件' }}
          </el-button>
        </template>
      </QueryBar>

      <!-- 工具列表 -->
      <el-table v-if="activeTab === 'tool'" :data="list" v-loading="loading" stripe>
        <el-table-column label="名称" prop="name" min-width="150" />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="toolTypeTag(row.type)">{{ label(toolTypes, row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分类" prop="category" width="110" />
        <el-table-column label="函数名" prop="functionName" min-width="150" />
        <el-table-column label="描述" prop="description" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '激活' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="success" @click="openExecute(row)">执行</el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 插件列表 -->
      <el-table v-else :data="list" v-loading="loading" stripe>
        <el-table-column label="名称" prop="name" min-width="150" />
        <el-table-column label="类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ label(pluginTypes, row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实现类" prop="className" min-width="260" show-overflow-tooltip />
        <el-table-column label="版本" prop="version" width="100" align="center" />
        <el-table-column label="加载" width="90" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.loadError" :content="row.loadError" placement="top">
              <el-tag size="small" type="danger">失败</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" :type="row.loaded ? 'success' : 'info'">
              {{ row.loaded ? '已加载' : '未加载' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '激活' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="warning" :loading="reloadingId === row.id"
                       @click="handleReload(row)">重载</el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页：工具与插件两个表格互斥渲染，共用同一组分页状态 -->
      <DataPager v-model:current-page="pageNum" v-model:page-size="pageSize"
                 :total="total" @change="loadList" />
    </el-card>

    <!-- 工具编辑 -->
    <el-dialog v-model="showToolDialog" :title="editing ? '编辑工具' : '新建工具'" width="680px" destroy-on-close>
      <el-form ref="toolFormRef" :model="toolForm" :rules="toolRules" label-width="100px">
        <el-form-item label="名称" prop="name"><el-input v-model="toolForm.name" /></el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="toolForm.type" style="width:100%">
            <el-option v-for="t in toolTypes" :key="t.code" :label="t.label" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="toolForm.category" allow-create filterable clearable style="width:100%">
            <el-option v-for="c in toolCategories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="函数名" prop="functionName">
          <el-input v-model="toolForm.functionName" placeholder="模型 Function Calling 调用名，如 get_weather" />
          <span class="hint-inline">该名称即模型调用时使用的函数名，需全局唯一；MCP 工具必须与远端工具名完全一致</span>
        </el-form-item>
        <el-form-item label="参数 Schema">
          <el-input v-model="toolForm.parameters" type="textarea" :rows="5"
                    :placeholder="schemaPlaceholder" />
        </el-form-item>
        <el-form-item label="配置">
          <el-input v-model="toolForm.config" type="textarea" :rows="6" :placeholder="configPlaceholder" />
          <span class="hint-inline">{{ configHint }}</span>
        </el-form-item>
        <el-form-item label="图标"><el-input v-model="toolForm.icon" placeholder="图标名或 URL" /></el-form-item>
        <el-form-item label="描述">
          <el-input v-model="toolForm.description" type="textarea" :rows="2"
                    placeholder="描述会作为工具说明提供给大模型，建议写清用途和使用时机" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="toolForm.status">
            <el-radio value="active">激活</el-radio>
            <el-radio value="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showToolDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTool">保存</el-button>
      </template>
    </el-dialog>

    <!-- 插件编辑 -->
    <el-dialog v-model="showPluginDialog" :title="editing ? '编辑插件' : '注册插件'" width="620px" destroy-on-close>
      <el-form ref="pluginFormRef" :model="pluginForm" :rules="pluginRules" label-width="100px">
        <el-form-item label="名称" prop="name"><el-input v-model="pluginForm.name" /></el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="pluginForm.type" style="width:100%">
            <el-option v-for="t in pluginTypes" :key="t.code" :label="t.label" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="实现类" prop="className">
          <el-input v-model="pluginForm.className" placeholder="如 cn.cangjiecloud.tool.plugin.HttpToolPlugin" />
        </el-form-item>
        <el-form-item label="版本"><el-input v-model="pluginForm.version" placeholder="如 1.0.0" /></el-form-item>
        <el-form-item label="配置">
          <el-input v-model="pluginForm.config" type="textarea" :rows="4" placeholder="JSON 配置" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="pluginForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="pluginForm.status">
            <el-radio value="active">激活</el-radio>
            <el-radio value="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPluginDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePlugin">保存</el-button>
      </template>
    </el-dialog>

    <!-- 工具执行 -->
    <el-dialog v-model="execDialog" :title="'执行工具 - ' + execTool?.name" width="680px" destroy-on-close>
      <el-alert v-if="execParamKeys.length" type="info" :closable="false" show-icon
                title="参数来自工具的 JSON Schema，可切换为 JSON 模式自由输入" style="margin-bottom:16px" />
      <el-radio-group :model-value="execMode" style="margin-bottom:16px" @change="changeExecMode">
        <el-radio-button value="form">表单</el-radio-button>
        <el-radio-button value="json">JSON</el-radio-button>
      </el-radio-group>

      <el-form v-if="execMode === 'form'" label-width="140px">
        <el-empty v-if="execParamKeys.length === 0" description="该工具未声明参数，可直接执行" :image-size="60" />
        <el-form-item v-for="k in execParamKeys" :key="k" :label="k" :required="execParamRequired.includes(k)">
          <el-select v-if="execParamMeta[k]?.enum" v-model="execForm[k]" :multiple="execParamMeta[k].multiple"
                     clearable collapse-tags :collapse-tags-tooltip="execParamMeta[k].multiple"
                     :placeholder="execParamDesc[k]" style="width: 100%">
            <el-option v-for="opt in execParamMeta[k].enum" :key="String(opt)" :label="String(opt)" :value="opt" />
          </el-select>
          <el-input v-else v-model="execForm[k]" :placeholder="execParamDesc[k]" />
        </el-form-item>
      </el-form>
      <el-input v-else v-model="execJson" type="textarea" :rows="6" placeholder='{"key": "value"}' />

      <div style="margin-top:12px">
        <el-button type="primary" :loading="executing" @click="runExecute">执行</el-button>
      </div>

      <template v-if="execResult">
        <el-divider>执行结果</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="execResult.success ? 'success' : 'danger'">
              {{ execResult.success ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ execResult.executionTime ?? '-' }} ms</el-descriptions-item>
        </el-descriptions>
        <pre class="exec-output">{{ formatOutput(execResult) }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Refresh } from '@element-plus/icons-vue'
import QueryBar from '@admin/components/QueryBar.vue'
import DataPager from '@admin/components/DataPager.vue'
import { toolApi, pluginApi } from '@admin/api/tool-api'

const toolTypes = [
  { code: 'HTTP', label: 'HTTP 接口' },
  { code: 'CUSTOM', label: '自定义脚本' },
  { code: 'MCP', label: 'MCP 协议' },
  { code: 'SKILL', label: '技能' },
  { code: 'PLUGIN', label: '插件' },
  { code: 'LOCAL', label: '本地工具' }
]
const pluginTypes = [
  { code: 'tool', label: '工具插件' },
  { code: 'model', label: '模型插件' },
  { code: 'parser', label: '解析插件' },
  { code: 'node', label: '工作流节点' },
  { code: 'channel', label: '渠道插件' }
]
const toolCategories = ['搜索', '数据库', '文件', '通知', '计算', '业务', '权限认证']
const schemaPlaceholder =
  '{"type":"object","properties":{"city":{"type":"string","description":"城市名"}},"required":["city"]}'

const configPlaceholder = computed(() => {
  switch (toolForm.type) {
    case 'HTTP':
      return '{\n' +
        '  "url": "https://api.example.com/endpoint",\n' +
        '  "method": "GET"\n' +
        '}'
    case 'MCP':
      return '{\n  "serverUrl": "http://localhost:3001/mcp"\n}'
    case 'CUSTOM':
      return '// Groovy 脚本内容，如：\nreturn "Hello"'
    case 'SKILL':
      return '{\n  "skillName": "my-skill",\n  "config": {}\n}'
    case 'LOCAL':
      return '本地工具无需服务端配置，仅由参数 Schema 描述能力，实际在用户浏览器授权目录内执行'
    default:
      return '{}'
  }
})

const configHint = computed(() => {
  switch (toolForm.type) {
    case 'HTTP':
      return 'GET 请求参数自动拼到 URL 上；如需自定义请求头，添加 "headers": {"Authorization": "Bearer xxx"}；POST 请求参数自动放入 body'
    case 'MCP':
      return '填入 MCP Server 地址，functionName 即为要调用的工具名'
    case 'CUSTOM':
      return '填入 Groovy 脚本，脚本中可通过 params.参数名 获取调用参数'
    case 'SKILL':
      return '填入技能相关配置'
    case 'LOCAL':
      return '服务端不执行该工具：网页对话中由浏览器在用户授权的本地文件夹内执行，配置留空即可'
    default:
      return ''
  }
})

const activeTab = ref<'tool' | 'plugin'>('tool')
const currentTypes = computed(() => (activeTab.value === 'tool' ? toolTypes : pluginTypes))
const list = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const filterType = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const saving = ref(false)
const editing = ref(false)
const reloadingId = ref('')
const scanning = ref(false)

const showToolDialog = ref(false)
const toolFormRef = ref<FormInstance>()
const toolForm = reactive<Record<string, any>>({})
const toolRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  functionName: [
    { required: true, message: '请输入函数名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9_-]{0,63}$/,
      message: '函数名需以字母开头，只能包含字母、数字、下划线和中划线，长度不超过 64',
      trigger: 'blur'
    }
  ]
}
const toolDefaults = {
  id: '', name: '', type: 'HTTP', category: '', functionName: '',
  parameters: '', config: '', icon: '', description: '', status: 'active'
}

const showPluginDialog = ref(false)
const pluginFormRef = ref<FormInstance>()
const pluginForm = reactive<Record<string, any>>({})
const pluginRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  className: [{ required: true, message: '请输入实现类', trigger: 'blur' }]
}
const pluginDefaults = {
  id: '', name: '', type: 'tool', className: '', version: '1.0.0',
  config: '', description: '', status: 'active'
}

function label(dict: { code: string; label: string }[], code: string) {
  return dict.find(d => d.code === code)?.label || code || '-'
}
function toolTypeTag(code: string): any {
  return ({ HTTP: 'primary', CUSTOM: 'warning', MCP: 'success', SKILL: 'info', PLUGIN: '', LOCAL: 'danger' } as any)[code] || ''
}

async function loadList() {
  loading.value = true
  try {
    const query = { keyword: keyword.value, type: filterType.value, pageNum: pageNum.value, pageSize: pageSize.value }
    const page = activeTab.value === 'tool' ? await toolApi.list(query) : await pluginApi.list(query)
    list.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

/** 搜索 / 切换筛选：回到第一页 */
function reload() {
  pageNum.value = 1
  loadList()
}

/** 重置筛选条件并重新查询 */
function resetQuery() {
  keyword.value = ''
  filterType.value = ''
  reload()
}

function onTabChange() {
  keyword.value = ''
  filterType.value = ''
  pageNum.value = 1
  loadList()
}

function openCreate() {
  editing.value = false
  if (activeTab.value === 'tool') {
    Object.assign(toolForm, toolDefaults)
    showToolDialog.value = true
  } else {
    Object.assign(pluginForm, pluginDefaults)
    showPluginDialog.value = true
  }
}

function openEdit(row: any) {
  editing.value = true
  if (activeTab.value === 'tool') {
    Object.assign(toolForm, toolDefaults, row)
    showToolDialog.value = true
  } else {
    Object.assign(pluginForm, pluginDefaults, row)
    showPluginDialog.value = true
  }
}

async function saveTool() {
  if (!toolFormRef.value) return
  await toolFormRef.value.validate()
  saving.value = true
  try {
    const { id, ...payload } = toolForm
    if (editing.value) {
      await toolApi.update(id, payload)
      ElMessage.success('更新成功')
    } else {
      await toolApi.create(payload)
      ElMessage.success('创建成功')
    }
    showToolDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function savePlugin() {
  if (!pluginFormRef.value) return
  await pluginFormRef.value.validate()
  saving.value = true
  try {
    const { id, ...payload } = pluginForm
    if (editing.value) {
      await pluginApi.update(id, payload)
      ElMessage.success('更新成功')
    } else {
      await pluginApi.create(payload)
      ElMessage.success('注册成功')
    }
    showPluginDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: string) {
  if (activeTab.value === 'tool') await toolApi.remove(id)
  else await pluginApi.remove(id)
  ElMessage.success('删除成功')
  loadList()
}

async function handleReload(row: any) {
  reloadingId.value = row.id
  try {
    const updated = await pluginApi.reload(row.id)
    ElMessage[updated?.loaded ? 'success' : 'warning'](
      updated?.loaded ? '重载成功' : '重载完成但未加载：' + (updated?.loadError || '未知原因'))
    loadList()
  } finally {
    reloadingId.value = ''
  }
}

async function handleScan() {
  scanning.value = true
  try {
    const found = await pluginApi.scan()
    ElMessage.success(`扫描完成，共 ${found?.length ?? 0} 个插件`)
    await loadList()
  } finally {
    scanning.value = false
  }
}

// 工具执行
const execDialog = ref(false)
const execTool = ref<any>(null)
const execMode = ref<'form' | 'json'>('form')
const execForm = ref<Record<string, any>>({})
const execJson = ref('{}')
const execParamKeys = ref<string[]>([])
const execParamDesc = ref<Record<string, string>>({})
const execParamRequired = ref<string[]>([])
/** 参数控件元信息：enum 非空时渲染下拉框，multiple 表示多选（数组 + items.enum） */
const execParamMeta = ref<Record<string, { type: string; enum?: any[]; multiple: boolean }>>({})
const execResult = ref<any>(null)
const executing = ref(false)

function openExecute(row: any) {
  execTool.value = row
  execResult.value = null
  execForm.value = {}
  execJson.value = '{}'
  execMode.value = 'form'
  execParamKeys.value = []
  execParamDesc.value = {}
  execParamRequired.value = []
  execParamMeta.value = {}
  try {
    const schema = row.parameters ? JSON.parse(row.parameters) : null
    const props = schema?.properties || {}
    execParamKeys.value = Object.keys(props)
    execParamRequired.value = schema?.required || []
    const meta: Record<string, { type: string; enum?: any[]; multiple: boolean }> = {}
    Object.keys(props).forEach(k => {
      execParamDesc.value[k] = props[k]?.description || props[k]?.type || ''
      meta[k] = parseParamMeta(props[k])
      if (meta[k].multiple) execForm.value[k] = []
    })
    execParamMeta.value = meta
  } catch {
    // Schema 非法时退化为 JSON 模式
    execMode.value = 'json'
  }
  execDialog.value = true
}

/** 从 JSON Schema 的属性定义里推导控件类型 */
function parseParamMeta(prop: any): { type: string; enum?: any[]; multiple: boolean } {
  const raw = prop?.type
  // type 可能是 ["string","null"] 这类联合写法
  const type = Array.isArray(raw) ? (raw.find(t => t !== 'null') || 'string') : (raw || 'string')
  if (type === 'array') {
    const itemEnum = prop?.items?.enum
    if (Array.isArray(itemEnum) && itemEnum.length) {
      return { type, enum: itemEnum, multiple: true }
    }
    return { type, multiple: false }
  }
  if (Array.isArray(prop?.enum) && prop.enum.length) {
    return { type, enum: prop.enum, multiple: false }
  }
  return { type, multiple: false }
}

// 表单/JSON 切换时双向同步
function changeExecMode(target: string) {
  if (target === execMode.value) return
  if (target === 'json') {
    // 表单 -> JSON：实时生成预览
    execJson.value = JSON.stringify(collectFormInput(), null, 2)
    execMode.value = 'json'
  } else {
    // JSON -> 表单：解析后回填，非法时阻止切换
    try {
      const parsed = JSON.parse(execJson.value || '{}')
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
        ElMessage.error('JSON 必须是对象，例如 {"key": "value"}')
        return
      }
      execForm.value = normalizeFormInput(parsed)
      execMode.value = 'form'
    } catch {
      ElMessage.error('JSON 格式错误，无法切换到表单')
    }
  }
}

/** 把 JSON 模式的值规整成表单控件需要的形状（多选必须是数组） */
function normalizeFormInput(parsed: Record<string, any>): Record<string, any> {
  const form: Record<string, any> = { ...parsed }
  for (const [k, meta] of Object.entries(execParamMeta.value)) {
    const v = form[k]
    if (!meta.multiple) continue
    if (v == null || v === '') {
      form[k] = []
    } else if (!Array.isArray(v)) {
      form[k] = String(v).split(',').map(s => s.trim()).filter(Boolean)
    }
  }
  return form
}

// 收集表单参数：必填原样带上；非必填空值跳过（避免空参数导致 400）
function collectFormInput(): Record<string, any> {
  const input: Record<string, any> = { ...execForm.value }
  for (const k of Object.keys(input)) {
    const v = input[k]
    const empty = v === '' || v == null || (Array.isArray(v) && v.length === 0)
    if (!execParamRequired.value.includes(k) && empty) {
      delete input[k]
    }
  }
  return input
}

async function runExecute() {
  let input: Record<string, any> = {}
  if (execMode.value === 'json') {
    try {
      input = JSON.parse(execJson.value || '{}')
    } catch {
      ElMessage.error('JSON 参数格式错误')
      return
    }
  } else {
    input = collectFormInput()
  }
  executing.value = true
  try {
    execResult.value = await toolApi.execute(execTool.value.id, input)
  } catch (e: any) {
    execResult.value = { success: false, error: e?.message || '执行失败' }
  } finally {
    executing.value = false
  }
}

function formatOutput(result: any) {
  const payload = result?.success ? result?.output : (result?.error ?? result?.output)
  if (payload == null) return '-'
  return typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2)
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar-left { display: flex; gap: 12px; }
.exec-output {
  margin-top: 12px; background: #f9fafc; border: 1px solid #ebeef5;
  border-radius: 8px; padding: 14px; max-height: 280px; overflow: auto;
  white-space: pre-wrap; word-break: break-all; font-size: 13px; line-height: 1.7;
}
.hint-inline { display: block; font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.5; }
</style>
