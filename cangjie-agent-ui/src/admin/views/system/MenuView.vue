<template>
  <div class="menu-view">
    <el-card>
      <template #header>
        <QueryBar @search="loadList" @reset="resetQuery">
          <el-input v-model="keyword" placeholder="搜索菜单名称..." clearable style="width: 220px"
                    @keyup.enter="loadList">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <template #extra>
            <el-button type="primary" @click="openCreate">
              <el-icon><Plus /></el-icon> 新增菜单
            </el-button>
          </template>
        </QueryBar>
      </template>

      <el-table :data="treeData" row-key="id" border stripe default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column label="菜单名称" prop="name" min-width="180" />
        <el-table-column label="权限标识" prop="code" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.type)">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路径" prop="path" min-width="180" show-overflow-tooltip />
        <el-table-column label="组件" prop="component" min-width="140" show-overflow-tooltip />
        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon" :size="18"><component :is="row.icon" /></el-icon>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '激活' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link type="success" @click="openCreate(row)">新增子菜单</el-button>
            <el-popconfirm title="确定删除该菜单？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑菜单' : '新增菜单'" width="600px">
      <el-form ref="formRef" :model="form" :rules="menuRules" label-width="100px">
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="权限标识" prop="code">
          <el-input v-model="form.code" placeholder="如 system:menu:add" />
        </el-form-item>
        <el-form-item label="父菜单">
          <el-cascader
            v-model="form.parentId"
            :options="menuTreeOptions"
            :props="{ value: 'id', label: 'name', children: 'children', emitPath: false, checkStrictly: true }"
            placeholder="请选择父菜单（不选则为顶级）"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="目录" value="directory" />
            <el-option label="菜单" value="menu" />
            <el-option label="按钮" value="button" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.path" placeholder="如 /system/role" />
        </el-form-item>
        <el-form-item label="组件">
          <el-input v-model="form.component" placeholder="如 system/RoleView" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="图标名称（如 Setting）" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" active-value="active" inactive-value="inactive" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import QueryBar from '@admin/components/QueryBar.vue'
import { menuApi } from '@admin/api/menu-api'

const treeData = ref<any[]>([])
const keyword = ref('')
const showDialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const menuTreeOptions = ref<any[]>([])

const form = reactive({
  id: '',
  parentId: '',
  name: '',
  code: '',
  type: 'menu',
  path: '',
  component: '',
  icon: '',
  sort: 0,
  status: 'active'
})

const menuRules: FormRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function typeLabel(type: string) {
  return { directory: '目录', menu: '菜单', button: '按钮' }[type] || type
}

function typeTag(type: string) {
  return { directory: 'primary', menu: 'success', button: 'info' }[type] || ''
}

async function loadList() {
  const res = await menuApi.tree()
  treeData.value = res
  menuTreeOptions.value = res
}

/** 重置筛选条件并重新查询 */
function resetQuery() {
  keyword.value = ''
  loadList()
}

function openCreate(row?: any) {
  editing.value = false
  Object.assign(form, {
    id: '',
    parentId: row ? row.id : '',
    name: '',
    code: '',
    type: 'menu',
    path: '',
    component: '',
    icon: '',
    sort: 0,
    status: 'active'
  })
  showDialog.value = true
}

function openEdit(row: any) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId || '',
    name: row.name,
    code: row.code || '',
    type: row.type,
    path: row.path || '',
    component: row.component || '',
    icon: row.icon || '',
    sort: row.sort ?? 0,
    status: row.status
  })
  showDialog.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (editing.value) {
      await menuApi.update(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await menuApi.create({ ...form })
      ElMessage.success('添加成功')
    }
    showDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  await menuApi.remove(row.id)
  ElMessage.success('删除成功')
  loadList()
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; gap: 12px; }
</style>