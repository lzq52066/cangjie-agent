<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col :span="6" v-for="(c, i) in cards" :key="i">
        <el-card shadow="hover" class="stat-card" :class="'c' + i">
          <div class="stat-icon"><el-icon :size="28"><component :is="c.icon" /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ c.value }}</div>
            <div class="stat-label">{{ c.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="16">
        <el-card class="module-card">
          <template #header>
            <div class="card-header"><span>核心能力模块</span><el-tag type="success" effect="light">v1.0.0 M1</el-tag></div>
          </template>
          <el-row :gutter="12">
            <el-col :span="8" v-for="m in modules" :key="m.title">
              <el-card class="module-item" shadow="never">
                <div class="m-icon" :style="{ background: m.bg }"><el-icon :size="22"><component :is="m.icon" /></el-icon></div>
                <div class="m-title">{{ m.title }}</div>
                <div class="m-desc">{{ m.desc }}</div>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="notice-card">
          <template #header>
            <div class="card-header"><span>系统信息</span></div>
          </template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="产品名称">{{ sysInfo?.name || 'CangJie Agent' }}</el-descriptions-item>
            <el-descriptions-item label="版本号">{{ sysInfo?.version || '1.0.0' }}</el-descriptions-item>
            <el-descriptions-item label="域名">{{ sysInfo?.domain || 'agent.cangjiecloud.cn' }}</el-descriptions-item>
            <el-descriptions-item label="后端框架">Spring Boot 3.4 + MyBatis-Plus 3.5</el-descriptions-item>
            <el-descriptions-item label="前端技术">Vue 3 + Vite + Pinia + Element Plus</el-descriptions-item>
            <el-descriptions-item label="向量存储">pgvector / PostgreSQL</el-descriptions-item>
            <el-descriptions-item label="权限控制">Sa-Token + RBAC</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref } from 'vue'
import {
  Histogram, Files, Connection, Tools,
  EditPen, Share, VideoPlay, DataLine
} from '@element-plus/icons-vue'
import { authApi } from '@shared/api/auth-api'
import { observabilityApi } from '@admin/api/observability-api'
import { modelApi } from '@admin/api/model-api'
import { knowledgeApi } from '@admin/api/knowledge-api'
import { toolApi } from '@admin/api/tool-api'

const stat = ref<Record<string, any>>({})
const modelCount = ref(0)
const knowledgeCount = ref(0)
const toolCount = ref(0)

const cards = computed(() => [
  { label: '今日对话',    value: String(stat.value.todayChatCount ?? 0), icon: markRaw(Histogram) },
  { label: '知识库数',    value: String(knowledgeCount.value),           icon: markRaw(Files) },
  { label: '已接入模型',  value: String(modelCount.value),               icon: markRaw(Connection) },
  { label: '插件/工具',   value: String(toolCount.value),                icon: markRaw(Tools) }
])

const modules = [
  { title: '大模型接入',    icon: markRaw(Connection), bg: 'linear-gradient(135deg,#67c23a,#85ce61)', desc: 'OpenAI / 通义 / 文心 / 通义 / Llama 统一 Provider SPI' },
  { title: 'RAG 知识库',   icon: markRaw(Files),      bg: 'linear-gradient(135deg,#409eff,#66b1ff)', desc: 'PDF/Word/Markdown 切片 + pgvector + 混合检索 + 重排' },
  { title: '工具插件',     icon: markRaw(Tools),      bg: 'linear-gradient(135deg,#e6a23c,#f0c78a)', desc: 'deepseek-harness 万物即插件，统一 Tool/Plugin SPI' },
  { title: '提示词/Skill', icon: markRaw(EditPen),    bg: 'linear-gradient(135deg,#f56c6c,#f89898)', desc: '变量模板、Skill 复用、规则/命令/记忆' },
  { title: '工作流编排',   icon: markRaw(Share),      bg: 'linear-gradient(135deg,#909399,#c0c4cc)', desc: 'LogicFlow 画布 + 自定义节点 + DAG 执行' },
  { title: '渠道接入',     icon: markRaw(VideoPlay),  bg: 'linear-gradient(135deg,#303133,#606266)', desc: 'Web/SDK/微信/钉钉/飞书 Webhook' }
]

const sysInfo = ref<any>(null)
onMounted(async () => {
  const [s, d, m, k, t] = await Promise.allSettled([
    authApi.systemInfo(),
    observabilityApi.dashboard(),
    modelApi.list(),
    knowledgeApi.list(),
    toolApi.list()
  ])
  if (s.status === 'fulfilled') sysInfo.value = s.value
  if (d.status === 'fulfilled') stat.value = d.value || {}
  if (m.status === 'fulfilled') modelCount.value = (m.value || []).length
  if (k.status === 'fulfilled') knowledgeCount.value = (k.value || []).length
  if (t.status === 'fulfilled') toolCount.value = (t.value || []).length
})
</script>

<style lang="scss" scoped>
.dashboard { }
.stat-card { padding: 6px 18px; }
.stat-card :deep(.el-card__body) { display: flex; align-items: center; gap: 16px; }
.stat-icon {
  width: 56px; height: 56px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.c0 .stat-icon { background: linear-gradient(135deg,#67c23a,#85ce61); }
.c1 .stat-icon { background: linear-gradient(135deg,#409eff,#66b1ff); }
.c2 .stat-icon { background: linear-gradient(135deg,#e6a23c,#f0c78a); }
.c3 .stat-icon { background: linear-gradient(135deg,#f56c6c,#f89898); }
.stat-value { font-size: 26px; font-weight: 700; color: #1f2d3d; }
.stat-label { color: #909399; font-size: 13px; margin-top: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.module-item { margin: 0 !important; border: 1px solid #ebeef5; transition: .25s; cursor: default; }
.module-item:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.06); }
.m-icon {
  width: 44px; height: 44px; border-radius: 12px;
  color: #fff; display: flex; align-items: center; justify-content: center;
  margin-bottom: 12px;
}
.m-title { font-weight: 600; color: #1f2d3d; margin-bottom: 6px; }
.m-desc { color: #909399; font-size: 12px; line-height: 1.6; }
.notice-card :deep(.el-descriptions__label) { width: 90px; background: #fafafa; }
</style>
