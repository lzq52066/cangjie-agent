<template>
  <div class="change-pwd-page">
    <div class="change-pwd-wrap">
      <el-card class="change-pwd-card" shadow="always">
        <div class="header">
          <div class="logo-icon">
            <el-icon><Key /></el-icon>
          </div>
          <h2 class="title">首次登录请修改初始密码</h2>
          <p class="subtitle">为保障账号安全，请先设置新密码后再使用系统</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top"
                 @submit.prevent @keyup.enter="onSubmit">
          <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="form.oldPassword" type="password" show-password
                      placeholder="请输入当前密码（初始密码）" autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="form.newPassword" type="password" show-password
                      placeholder="6-64 位新密码" autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password
                      placeholder="请再次输入新密码" autocomplete="new-password" />
          </el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
            确认修改并进入系统
          </el-button>
          <el-button text class="logout-btn" @click="onLogout">退出登录</el-button>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Key } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@admin/store/user'
import { profileApi } from '@admin/api/profile-api'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度需在 6-64 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    loading.value = true
    await profileApi.changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    // 同步更新本地登录态中的强制改密标记
    if (userStore.userInfo) {
      userStore.userInfo.mustChangePassword = false
    }
    ElMessage.success('密码修改成功')
    router.replace('/dashboard')
  } finally {
    loading.value = false
  }
}

async function onLogout() {
  await userStore.logout()
  router.replace('/login')
}
</script>

<style lang="scss" scoped>
.change-pwd-page {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #0f1c2e 0%, #1a252f 50%, #0e1a26 100%);
}
.change-pwd-wrap {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.change-pwd-card {
  width: 440px;
  max-width: 100%;
  padding: 30px 36px 22px;
  border-radius: 18px;
}
.header { text-align: center; margin-bottom: 22px; }
.logo-icon {
  width: 52px; height: 52px; border-radius: 14px;
  margin: 0 auto 14px;
  background: linear-gradient(135deg, var(--cj-primary), #409eff);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 26px;
}
.title { font-size: 20px; font-weight: 700; color: #1f2d3d; margin: 0; }
.subtitle { font-size: 13px; color: #909399; margin: 8px 0 0; }
.submit-btn { width: 100%; height: 44px; font-size: 15px; letter-spacing: 2px; }
.logout-btn { width: 100%; margin-top: 10px; }
</style>
