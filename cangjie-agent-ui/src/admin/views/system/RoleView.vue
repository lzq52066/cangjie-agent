<template>
  <div class="role-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-input v-model="searchForm.roleName" placeholder="角色名称" clearable style="width: 180px"
                      @clear="loadList" @keyup.enter="loadList" />
            <el-input v-model="searchForm.roleCode" placeholder="角色编码" clearable style="width: 180px"
                      @clear="loadList" @keyup.enter="loadList" />
            <el-button @click="loadList">搜索</el-button>
          </div>
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon> 新增角色
          </el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe border>
        <el-table-column label="角色名称" prop="roleName" min-width="150" />
        <el-table-column label="角色编码" prop="roleCode" min-width="150" />
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '激活' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="320" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link type="success" @click="openAssignUsers(row)">分配用户</el-button>
            <el-button size="small" link type="warning" @click="openAssignMenus(row)">分配菜单</el-button>
            <el-popconfirm title="确定删除该角色？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑角色' : '新增角色'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="请输入角色编码" :disabled="editing" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配用户对话框 -->
    <el-dialog v-model="userDialog" title="分配用户" width="700px">
      <div class="user-select-area">
        <el-input v-model="userKeyword" placeholder="搜索用户..." clearable style="width: 240px"
                  @clear="loadUserOptions" @keyup.enter="loadUserOptions">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-table
          :data="userOptions"
          v-loading="userLoading"
          @selection-change="handleUserSelectionChange"
          style="margin-top: 12px"
          height="360"
          border
        >
          <el-table-column type="selection" width="55" />
          <el-table-column label="用户名" prop="username" min-width="120" />
          <el-table-column label="昵称" prop="nickname" min-width="120" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" :loading="userSaving" @click="handleAssignUsers">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配菜单对话框 -->
    <el-dialog v-model="menuDialog" title="分配菜单" width="420px">
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        :props="{ label: 'name', children: 'children' }"
        show-checkbox
        node-key="id"
        default-expand-all
      />
      <template #footer>
        <el-button @click="menuDialog = false">取消</el-button>
        <el-button type="primary" :loading="menuSaving" @click="handleAssignMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { roleApi } from '@admin/api/role-api'

// 列表
const list = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const searchForm = reactive({ roleName: '', roleCode: '' })

// 表单
const showDialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ id: '', roleName: '', roleCode: '', description: '', status: 1 })

const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

// 分配用户
const userDialog = ref(false)
const userLoading = ref(false)
const userSaving = ref(false)
const userKeyword = ref('')
const userOptions = ref<any[]>([])
const selectedUserIds = ref<string[]>([])
const currentRoleIdForUser = ref('')

// 分配菜单
const menuDialog = ref(false)
const menuSaving = ref(false)
const menuTree = ref<any[]>([])
const menuTreeRef = ref()
const currentRoleIdForMenu = ref('')

async function loadList() {
  loading.value = true
  try {
    const res = await roleApi.list({ page: page.value, size: size.value, ...searchForm })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: '', roleName: '', roleCode: '', description: '', status: 1 })
  showDialog.value = true
}

function openEdit(row: any) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    roleName: row.roleName,
    roleCode: row.roleCode,
    description: row.description || '',
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
      await roleApi.update(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await roleApi.create({ ...form })
      ElMessage.success('添加成功')
    }
    showDialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  await roleApi.remove(row.id)
  ElMessage.success('删除成功')
  loadList()
}

async function openAssignUsers(row: any) {
  currentRoleIdForUser.value = row.id
  selectedUserIds.value = row.userIds || []
  userDialog.value = true
  await loadUserOptions()
}

async function loadUserOptions() {
  userLoading.value = true
  try {
    userOptions.value = await roleApi.userOptions(userKeyword.value)
  } finally {
    userLoading.value = false
  }
}

function handleUserSelectionChange(selection: any[]) {
  selectedUserIds.value = selection.map((s: any) => s.userId)
}

async function handleAssignUsers() {
  userSaving.value = true
  try {
    await roleApi.assignUsers(currentRoleIdForUser.value, selectedUserIds.value)
    ElMessage.success('分配用户成功')
    userDialog.value = false
    loadList()
  } finally {
    userSaving.value = false
  }
}

async function openAssignMenus(row: any) {
  currentRoleIdForMenu.value = row.id
  menuDialog.value = true
  menuTree.value = await roleApi.menuTree()
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(row.menuIds || [])
}

async function handleAssignMenus() {
  menuSaving.value = true
  try {
    const keys = menuTreeRef.value?.getCheckedKeys() || []
    await roleApi.assignMenus(currentRoleIdForMenu.value, keys)
    ElMessage.success('分配菜单成功')
    menuDialog.value = false
    loadList()
  } finally {
    menuSaving.value = false
  }
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; gap: 12px; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.user-select-area { padding: 4px 0; }
</style>