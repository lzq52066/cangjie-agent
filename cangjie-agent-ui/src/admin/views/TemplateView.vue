<template>
  <div class="template-view">
    <el-card>
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="category" placeholder="全部分类" clearable filterable style="width:180px"
                     @change="reload">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-button @click="reload">刷新</el-button>
        </div>
        <div>
          <el-button @click="triggerImport">
            <el-icon><Upload /></el-icon> 导入 Bundle
          </el-button>
          <input ref="fileInput" type="file" accept=".json,application/json" class="hidden-file"
                 @change="handleImport" />
          <el-button type="primary" @click="openSaveDialog">
            <el-icon><Star /></el-icon> 从应用另存
          </el-button>
        </div>
      </div>

      <div v-loading="loading" class="template-grid">
        <div v-for="t in list" :key="t.id" class="tpl-card">
          <div class="tpl-header">
            <div class="tpl-icon">{{ (t.icon || t.name || '?').slice(0, 1) }}</div>
            <div class="tpl-titles">
              <div class="tpl-name">
                {{ t.name }}
                <el-tag v-if="t.builtin" size="small" type="warning" effect="plain">官方</el-tag>
              </div>
              <div class="tpl-sub">
                <el-tag size="small" effect="plain">{{ typeLabel(t.appType) }}</el-tag>
                <span v-if="t.category" class="tpl-cat">{{ t.category }}</span>
                <span class="tpl-use">使用 {{ t.useCount ?? 0 }} 次</span>
              </div>
            </div>
          </div>
          <div class="tpl-desc">{{ t.description || '暂无描述' }}</div>
          <div class="tpl-footer">
            <el-button size="small" type="primary" @click="createApp(t)">创建应用</el-button>
            <el-button size="small" @click="exportBundle(t)">导出</el-button>
            <el-popconfirm title="确定删除该模板？" @confirm="remove(t)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
        <el-empty v-if="!list.length && !loading" description="暂无模板，可从现有应用另存或导入 Bundle"
                  :image-size="90" class="tpl-empty" />
      </div>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                     :page-sizes="[12, 24, 48]" @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 从应用另存 -->
    <el-dialog v-model="saveDialog" title="将应用另存为模板" width="520px">
      <el-form :model="saveForm" label-width="100px">
        <el-form-item label="选择应用" required>
          <el-select v-model="saveForm.applicationId" filterable placeholder="选择要沉淀的应用"
                     style="width:100%">
            <el-option v-for="a in applications" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="saveForm.name" placeholder="模板名称" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="saveForm.category" allow-create filterable default-first-option
                     placeholder="如：客服 / 写作 / 分析" style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="saveForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmSave">保存为模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Star } from '@element-plus/icons-vue'
import { applicationApi, applicationTemplateApi } from '@admin/api/application-api'

function typeLabel(t?: string) {
  return ({ chat: '对话', agent: '智能体', workflow: '工作流' } as Record<string, string>)[t || ''] || t || '应用'
}

const loading = ref(false)
const list = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)
const category = ref('')
const categories = ref<string[]>([])
const applications = ref<any[]>([])

async function loadList() {
  loading.value = true
  try {
    const [page, all] = await Promise.all([
      applicationTemplateApi.list({
        category: category.value || undefined,
        pageNum: pageNum.value,
        pageSize: pageSize.value
      }),
      // 拉一份全量用于聚合分类选项
      applicationTemplateApi.list({ pageSize: 200 }).catch(() => null)
    ])
    list.value = page?.list || []
    total.value = page?.total || 0
    const cats = new Set<string>()
    ;(all?.list || []).forEach(t => t.category && cats.add(t.category))
    categories.value = Array.from(cats)
  } finally {
    loading.value = false
  }
}
function reload() {
  pageNum.value = 1
  loadList()
}

// 从应用另存
const saveDialog = ref(false)
const saving = ref(false)
const saveForm = reactive({ applicationId: '', name: '', category: '', description: '' })
function openSaveDialog() {
  Object.assign(saveForm, { applicationId: '', name: '', category: '', description: '' })
  saveDialog.value = true
}
async function confirmSave() {
  if (!saveForm.applicationId || !saveForm.name.trim()) {
    ElMessage.warning('请选择应用并填写模板名称')
    return
  }
  saving.value = true
  try {
    await applicationTemplateApi.saveFromApplication({
      applicationId: saveForm.applicationId,
      name: saveForm.name.trim(),
      category: saveForm.category || null,
      description: saveForm.description || null
    })
    ElMessage.success('已保存为模板')
    saveDialog.value = false
    reload()
  } finally {
    saving.value = false
  }
}

// 用模板创建应用
async function createApp(t: any) {
  const { value: name } = await ElMessageBox.prompt('新应用名称', `从模板「${t.name}」创建应用`, {
    inputPlaceholder: '留空则使用模板名称',
    confirmButtonText: '创建',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (name === null) return
  const app = await applicationTemplateApi.createFromTemplate(t.id, name || undefined)
  ElMessage.success(`应用「${app?.name || name}」已创建（草稿态，可在应用管理中发布）`)
}

// 导出 Bundle（JSON 文件下载）
async function exportBundle(t: any) {
  const json = await applicationTemplateApi.exportBundle(t.id)
  const blob = new Blob([typeof json === 'string' ? json : JSON.stringify(json, null, 2)],
    { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `template-${t.name || t.id}.json`
  a.click()
  URL.revokeObjectURL(url)
}

// 删除
async function remove(t: any) {
  await applicationTemplateApi.remove(t.id)
  ElMessage.success('模板已删除')
  loadList()
}

// 导入 Bundle
const fileInput = ref<HTMLInputElement | null>(null)
function triggerImport() {
  fileInput.value?.click()
}
async function handleImport(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    await applicationTemplateApi.importBundle(text)
    ElMessage.success('Bundle 导入成功')
    reload()
  } catch {
    ElMessage.error('导入失败：文件不是合法的模板 Bundle')
  } finally {
    input.value = ''
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
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar-left { display: flex; gap: 10px; }
.hidden-file { display: none; }
.template-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px; min-height: 200px;
}
.tpl-card {
  border: 1px solid #ebeef5; border-radius: 10px; padding: 16px;
  display: flex; flex-direction: column; gap: 10px; background: #fff;
  transition: box-shadow .2s;
  &:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); }
}
.tpl-header { display: flex; gap: 12px; align-items: center; }
.tpl-icon {
  width: 42px; height: 42px; border-radius: 10px; flex-shrink: 0;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff; font-size: 20px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.tpl-titles { min-width: 0; }
.tpl-name { font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.tpl-sub { display: flex; align-items: center; gap: 8px; margin-top: 4px; font-size: 12px; color: #909399; }
.tpl-cat { }
.tpl-use { margin-left: auto; }
.tpl-desc {
  font-size: 13px; color: #606266; line-height: 1.6; flex: 1;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  min-height: 42px;
}
.tpl-footer { display: flex; align-items: center; gap: 8px; }
.tpl-empty { grid-column: 1 / -1; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
