<template>
  <div class="system-view">
    <el-card>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基础设置" name="base">
          <el-form :model="baseForm" label-width="140px" style="max-width:640px">
            <el-form-item label="站点名称"><el-input v-model="baseForm.siteName" placeholder="CangJie Agent" /></el-form-item>
            <el-form-item label="站点地址">
              <el-input v-model="baseForm.siteUrl" placeholder="https://agent.cangjiecloud.cn" />
            </el-form-item>
            <el-form-item label="Logo 地址"><el-input v-model="baseForm.logo" placeholder="图片 URL" /></el-form-item>
            <el-form-item label="允许注册">
              <el-switch v-model="baseForm.allowRegister" />
            </el-form-item>
            <el-form-item label="备案信息"><el-input v-model="baseForm.icp" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save('base')">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="邮件设置" name="email">
          <el-form :model="emailForm" label-width="140px" style="max-width:640px">
            <el-form-item label="SMTP 主机"><el-input v-model="emailForm.host" placeholder="smtp.example.com" /></el-form-item>
            <el-form-item label="端口"><el-input v-model="emailForm.port" placeholder="465" /></el-form-item>
            <el-form-item label="发件账号"><el-input v-model="emailForm.username" /></el-form-item>
            <el-form-item label="发件密码">
              <el-input v-model="emailForm.password" type="password" show-password />
            </el-form-item>
            <el-form-item label="发件人名称"><el-input v-model="emailForm.fromName" /></el-form-item>
            <el-form-item label="开启 SSL"><el-switch v-model="emailForm.ssl" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save('email')">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="对话设置" name="chat">
          <el-form :model="chatForm" label-width="140px" style="max-width:640px">
            <el-form-item label="默认欢迎语">
              <el-input v-model="chatForm.welcome" type="textarea" :rows="3" placeholder="你好，我是智能助手，有什么可以帮你？" />
            </el-form-item>
            <el-form-item label="默认最大轮次">
              <el-input-number v-model="chatForm.maxTurns" :min="1" :max="100" />
            </el-form-item>
            <el-form-item label="默认温度">
              <el-slider v-model="chatForm.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
            <el-form-item label="显示引用来源"><el-switch v-model="chatForm.showReference" /></el-form-item>
            <el-form-item label="启用敏感词过滤"><el-switch v-model="chatForm.sensitiveFilter" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save('chat')">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="系统信息" name="info">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="产品名称">{{ info.name || 'CangJie Agent' }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ info.version || '-' }}</el-descriptions-item>
            <el-descriptions-item label="域名">{{ info.domain || '-' }}</el-descriptions-item>
            <el-descriptions-item label="服务器时间">{{ serverTime }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { systemApi, SETTING_TYPE } from '@admin/api/system-api'

const activeTab = ref<'base' | 'email' | 'chat' | 'info'>('base')
const saving = ref(false)

const baseForm = reactive<Record<string, any>>({
  siteName: '', siteUrl: '', logo: '', allowRegister: false, icp: ''
})
const emailForm = reactive<Record<string, any>>({
  host: '', port: '', username: '', password: '', fromName: '', ssl: true
})
const chatForm = reactive<Record<string, any>>({
  welcome: '', maxTurns: 20, temperature: 0.7, showReference: true, sensitiveFilter: false
})

const info = ref<Record<string, any>>({})
const serverTime = computed(() =>
  info.value.time ? new Date(Number(info.value.time)).toLocaleString() : '-')

const typeMap = {
  base: SETTING_TYPE.BASE,
  email: SETTING_TYPE.EMAIL,
  chat: SETTING_TYPE.CHAT
} as const
const formMap = { base: baseForm, email: emailForm, chat: chatForm } as const

async function loadSetting(key: keyof typeof typeMap) {
  try {
    const meta = await systemApi.getSetting(typeMap[key])
    if (meta) Object.assign(formMap[key], meta)
  } catch {
    // 未配置过的设置项保持默认值
  }
}

async function save(key: keyof typeof typeMap) {
  saving.value = true
  try {
    await systemApi.saveSetting(typeMap[key], { ...formMap[key] })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadSetting('base'), loadSetting('email'), loadSetting('chat')])
  try {
    info.value = await systemApi.info()
  } catch {
    info.value = {}
  }
})
</script>

<style lang="scss" scoped>
.system-view { max-width: 1200px; }
</style>
