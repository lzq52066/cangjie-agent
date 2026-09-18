<template>
  <div class="profile-view" v-loading="loading">
    <!-- 左侧：用户概览 -->
    <el-card class="profile-card user-card">
      <div class="avatar" :style="{ background: themeColor }">{{ initial }}</div>
      <div class="nickname">{{ info?.nickname || '-' }}</div>
      <div class="username">@{{ info?.username }}</div>
      <el-tag :type="info?.role === 'ADMIN' ? 'danger' : 'info'" effect="plain" round>
        {{ roleText }}
      </el-tag>
      <el-descriptions :column="1" class="meta" border size="small">
        <el-descriptions-item label="用户 ID">{{ info?.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工作空间">{{ info?.workspaceId || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 右侧：资料编辑 / 修改密码 -->
    <div class="right">
      <el-card class="profile-card">
        <template #header>
          <div class="card-title">基本资料</div>
        </template>
        <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules"
                 label-width="80px" class="profile-form" @submit.prevent>
          <el-form-item label="用户名">
            <el-input :model-value="info?.username" disabled />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="profileForm.nickname" maxlength="64" show-word-limit
                      placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" maxlength="128" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="手机" prop="phone">
            <el-input v-model="profileForm.phone" maxlength="20" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">保存资料</el-button>
            <el-button @click="resetProfileForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="profile-card">
        <template #header>
          <div class="card-title">修改密码</div>
        </template>
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules"
                 label-width="80px" class="profile-form" @submit.prevent>
          <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password
                      placeholder="请输入当前密码" autocomplete="off" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password
                      placeholder="6-64 位新密码" autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password
                      placeholder="再次输入新密码" autocomplete="new-password"
                      @keyup.enter="handleChangePassword" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingPwd" @click="handleChangePassword">确认修改</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@admin/store/user'
import { profileApi } from '@admin/api/profile-api'
import { useTheme } from '@admin/utils/theme'
import type { UserIdentity } from '@shared/types'

const { themeColor } = useTheme()
const userStore = useUserStore()

const loading = ref(false)
const info = ref<UserIdentity | null>(userStore.userInfo)

const initial = computed(() => info.value?.nickname?.slice(0, 1) || info.value?.username?.slice(0, 1) || 'U')
const roleText = computed(() => info.value?.role === 'ADMIN' ? '超级管理员' : '普通用户')

async function loadInfo() {
  loading.value = true
  try {
    info.value = await profileApi.get()
    userStore.userInfo = info.value
    resetProfileForm()
  } finally {
    loading.value = false
  }
}

// 基本资料
const profileFormRef = ref<FormInstance>()
const savingProfile = ref(false)
const profileForm = reactive({ nickname: '', email: '', phone: '' })

const profileRules: FormRules = {
  nickname: [{ max: 64, message: '昵称长度不能超过 64 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^[\d+\-() ]*$/, message: '手机号格式不正确', trigger: 'blur' }]
}

function resetProfileForm() {
  profileForm.nickname = info.value?.nickname || ''
  profileForm.email = info.value?.email || ''
  profileForm.phone = info.value?.phone || ''
  profileFormRef.value?.clearValidate()
}

async function handleSaveProfile() {
  await profileFormRef.value?.validate()
  savingProfile.value = true
  try {
    const updated = await profileApi.update({
      nickname: profileForm.nickname?.trim(),
      email: profileForm.email?.trim(),
      phone: profileForm.phone?.trim()
    })
    info.value = updated
    userStore.userInfo = updated
    resetProfileForm()
    ElMessage.success('资料已保存')
  } finally {
    savingProfile.value = false
  }
}

// 修改密码
const pwdFormRef = ref<FormInstance>()
const savingPwd = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度需在 6-64 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function handleChangePassword() {
  await pwdFormRef.value?.validate()
  savingPwd.value = true
  try {
    await profileApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdFormRef.value?.clearValidate()
  } finally {
    savingPwd.value = false
  }
}

onMounted(loadInfo)
</script>

<style lang="scss" scoped>
.profile-view {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.profile-card { border-radius: 10px; }
.user-card {
  width: 300px;
  flex-shrink: 0;
  text-align: center;
}
.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  margin: 8px auto 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 30px;
  font-weight: 600;
}
.nickname { font-size: 18px; font-weight: 600; color: #303133; }
.username { font-size: 13px; color: #909399; margin: 4px 0 12px; }
.meta { margin-top: 18px; text-align: left; }
.right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card-title { font-size: 15px; font-weight: 600; color: #303133; }
.profile-form { max-width: 520px; }

@media (max-width: 768px) {
  .profile-view { flex-direction: column; }
  .user-card { width: 100%; }
}
</style>
