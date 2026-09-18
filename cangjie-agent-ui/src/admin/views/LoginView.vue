<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="glow g1"></div>
      <div class="glow g2"></div>
      <div class="glow g3"></div>
    </div>
    <div class="login-wrap">
      <div class="login-card">
        <div class="login-header">
          <div class="logo">
            <div class="logo-icon">C</div>
            <div class="logo-text">
              <div class="logo-title">CangJie Agent</div>
              <div class="logo-subtitle">仓颉智能体平台</div>
            </div>
          </div>
          <p class="slogan">大模型 × RAG × 工作流 × 万物插件</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="onSubmit">
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="用户名 / admin"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              size="large"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              type="password"
              show-password
              autocomplete="current-password"
              @keyup.enter="onSubmit"
            />
          </el-form-item>

          <div class="captcha-row" v-if="false">
            <el-input v-model="form.code" placeholder="验证码" :prefix-icon="Key" />
            <img :src="captchaUrl" class="captcha-img" alt="captcha" />
          </div>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              :loading="loading"
              native-type="submit"
            >登 录</el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          <span>© CangJie Cloud · agent.cangjiecloud.cn</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { useUserStore } from '@admin/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '',
  code: ''
})

const captchaUrl = '/api/open/auth/captcha'

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' },
             { min: 6, message: '密码长度不少于6位', trigger: 'blur' }]
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    loading.value = true
    await userStore.login(form as any)
    ElMessage.success('登录成功')
    const redirect = String(route.query.redirect || '/dashboard')
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #0f1c2e 0%, #1a252f 50%, #0e1a26 100%);
}
.login-bg { position: absolute; inset: 0; overflow: hidden; }
.glow { position: absolute; border-radius: 50%; filter: blur(80px); opacity: .6; }
.g1 { width: 500px; height: 500px; background: var(--cj-primary); top: -120px; left: -80px; }
.g2 { width: 600px; height: 600px; background: #409eff; bottom: -180px; right: -120px; opacity: .45; }
.g3 { width: 300px; height: 300px; background: #e6a23c; top: 40%; left: 40%; opacity: .25; }

.login-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.login-card {
  width: 440px;
  max-width: 100%;
  padding: 36px 38px 24px;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3), inset 0 1px 0 rgba(255,255,255,.8);
  backdrop-filter: blur(12px);
}
.login-header { text-align: center; margin-bottom: 28px; }
.logo { display: flex; align-items: center; justify-content: center; gap: 12px; }
.logo-icon {
  width: 52px; height: 52px; border-radius: 14px;
  background: linear-gradient(135deg, var(--cj-primary), #409eff);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 26px;
  box-shadow: 0 8px 20px rgba(var(--cj-primary-rgb), 0.4);
}
.logo-title { font-size: 22px; font-weight: 700; color: #1f2d3d; }
.logo-subtitle { font-size: 12px; color: #909399; margin-top: 2px; }
.slogan {
  margin: 14px 0 0; color: #606266; font-size: 13px;
  padding: 6px 14px; display: inline-block;
  background: var(--el-color-primary-light-9); border-radius: 12px; color: var(--cj-primary);
}
.submit-btn { width: 100%; height: 44px; font-size: 15px; letter-spacing: 4px; }
.captcha-row { display: flex; gap: 12px; margin-bottom: 18px; }
.captcha-img { height: 38px; border-radius: 6px; cursor: pointer; border: 1px solid #dcdfe6; }
.login-footer {
  margin-top: 18px; text-align: center;
  color: #909399; font-size: 12px;
  border-top: 1px solid #f0f0f0; padding-top: 14px;
}
</style>
