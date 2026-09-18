<template>
  <div class="user-view">
    <el-card>
      <template #header>
        <QueryBar :loading="loading" @search="reload" @reset="resetQuery">
          <el-input v-model="query.keyword" placeholder="用户名/昵称/邮箱/手机" clearable style="width:230px"
                    @keyup.enter="reload" />
          <el-select v-model="query.isActive" placeholder="全部状态" clearable style="width:130px">
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
          <el-select v-model="query.source" placeholder="全部来源" clearable style="width:130px">
            <el-option label="本地" value="LOCAL" />
          </el-select>
          <template #extra>
            <el-button type="primary" @click="openCreate">
              <el-icon><Plus /></el-icon> 新增用户
            </el-button>
          </template>
        </QueryBar>
      </template>

      <el-table :data="list" v-loading="loading" stripe border>
        <el-table-column label="用户名" prop="username" min-width="130" />
        <el-table-column label="昵称" prop="nickname" min-width="130" show-overflow-tooltip />
        <el-table-column label="邮箱" prop="email" min-width="180" show-overflow-tooltip />
        <el-table-column label="手机" prop="phone" min-width="130" />
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">{{ roleNamesOf(row.id) }}</template>
        </el-table-column>
        <el-table-column label="来源" prop="source" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.isActive !== false"
                       :disabled="row.username === 'admin'"
                       @change="(v: boolean) => handleStatusChange(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="165" />
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link type="warning" @click="openReset(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination background v-model:current-page="pageNum" v-model:page-size="pageSize"
                       :total="total" :page-sizes="[10, 20, 50]"
                       layout="total, sizes, prev, pager, next"
                       @size-change="loadList" @current-change="loadList" />
      </div>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialog" :title="editingId ? '编辑用户' : '新增用户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!editingId" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="初始登录密码" />
        </el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width:100%" placeholder="分配角色">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isActive" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdDialog" title="重置密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="用户">{{ pwdTarget?.username }}</el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="newPassword" type="password" show-password placeholder="输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import QueryBar from '@admin/components/QueryBar.vue'
import { userApi, type AdminUser, type UserSaveDTO } from '@admin/api/user-api'
import { roleApi, type Role } from '@admin/api/role-api'

const query = reactive({ keyword: '', isActive: undefined as boolean | undefined, source: '' })
const list = ref<AdminUser[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const roles = ref<Role[]>([])
// userId -> 角色名集合，列表渲染时避免逐行请求
const userRoleMap = ref<Record<string, string[]>>({})

async function loadList() {
  loading.value = true
  try {
    const page = await userApi.page({
      keyword: query.keyword || undefined,
      isActive: query.isActive,
      source: query.source || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = page?.list || []
    total.value = page?.total || 0
    // 并行补齐每个用户的角色名
    const details = await Promise.all(list.value.map(u => userApi.get(u.id).catch(() => null)))
    const map: Record<string, string[]> = {}
    list.value.forEach((u, i) => {
      const ids = details[i]?.roleIds || []
      map[u.id] = ids.map(id => roles.value.find(r => r.id === id)?.name || id)
    })
    userRoleMap.value = map
  } finally {
    loading.value = false
  }
}
function reload() {
  pageNum.value = 1
  loadList()
}

/** 重置筛选条件并重新查询 */
function resetQuery() {
  query.keyword = ''
  query.isActive = undefined
  query.source = ''
  reload()
}
function roleNamesOf(userId: string) {
  const names = userRoleMap.value[userId]
  return names?.length ? names.join('、') : '-'
}

// 新增/编辑
const dialog = ref(false)
const editingId = ref('')
const saving = ref(false)
const formRef = ref()
const form = reactive<UserSaveDTO>({
  username: '', password: '', nickname: '', email: '', phone: '', isActive: true, roleIds: []
})
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }]
}

function openCreate() {
  editingId.value = ''
  Object.assign(form, { username: '', password: '', nickname: '', email: '', phone: '', isActive: true, roleIds: [] })
  dialog.value = true
}

async function openEdit(row: AdminUser) {
  const detail = await userApi.get(row.id)
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    password: '',
    nickname: detail.user.nickname || '',
    email: detail.user.email || '',
    phone: detail.user.phone || '',
    isActive: detail.user.isActive !== false,
    roleIds: detail.roleIds || []
  })
  dialog.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await userApi.update(editingId.value, {
        nickname: form.nickname, email: form.email, phone: form.phone,
        isActive: form.isActive, roleIds: form.roleIds
      })
      ElMessage.success('已保存')
    } else {
      await userApi.create(form)
      ElMessage.success('用户已创建')
    }
    dialog.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleStatusChange(row: AdminUser, active: boolean) {
  await userApi.updateStatus(row.id, active)
  row.isActive = active
  ElMessage.success(active ? '已启用' : '已停用')
}

// 重置密码
const pwdDialog = ref(false)
const pwdTarget = ref<AdminUser | null>(null)
const newPassword = ref('')
function openReset(row: AdminUser) {
  pwdTarget.value = row
  newPassword.value = ''
  pwdDialog.value = true
}
async function handleResetPassword() {
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  saving.value = true
  try {
    await userApi.resetPassword(pwdTarget.value!.id, newPassword.value)
    ElMessage.success('密码已重置')
    pwdDialog.value = false
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const page = await roleApi.list({ pageNum: 1, pageSize: 200, status: 'active' })
    roles.value = page?.list || []
  } catch {
    roles.value = []
  }
  loadList()
})
</script>

<style lang="scss" scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; gap: 10px; align-items: center; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
