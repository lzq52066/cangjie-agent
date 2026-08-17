<template>
  <el-container class="main-layout">
    <el-aside width="220px" class="sidebar">
      <div class="brand">
        <div class="logo-icon">C</div>
        <div class="brand-text">
          <div class="brand-name">CangJie Agent</div>
          <div class="brand-slogan">agent.cangjiecloud.cn</div>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="menu"
        router
        background-color="transparent"
        text-color="#e4e7ed"
        active-text-color="#67c23a"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left"></div>
        <div class="header-right">
          <el-dropdown @command="handleCmd">
            <span class="user-info">
              <el-avatar :size="32" style="background:#67c23a">{{ nickname }}</el-avatar>
              <span class="username">{{ user?.nickname || user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@admin/store/user'
import { ElMessageBox } from 'element-plus'
import {
  DataBoard, Connection, Files, Tools, EditPen,
  Share, DataLine, VideoPlay, Histogram, Folder, Setting, ArrowDown
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeMenu = computed(() => {
  // 子路由映射到父菜单（如 /knowledge/:id → /knowledge）
  const segments = route.path.split('/').filter(Boolean)
  return '/' + (segments[0] || 'dashboard')
})
const user = computed(() => userStore.userInfo)
const nickname = computed(() => user.value?.nickname?.slice(0, 1) || 'U')

const menus = [
  { path: '/dashboard',     title: '工作台',     icon: markRaw(DataBoard) },
  { path: '/model',         title: '模型管理',   icon: markRaw(Connection) },
  { path: '/knowledge',     title: '知识库',     icon: markRaw(Files) },
  { path: '/tool',          title: '工具插件',   icon: markRaw(Tools) },
  { path: '/prompt',        title: '提示词/Skill', icon: markRaw(EditPen) },
  { path: '/workflow',      title: '工作流',     icon: markRaw(Share) },
  { path: '/application',   title: '智能应用',   icon: markRaw(VideoPlay) },
  { path: '/channel',       title: '渠道接入',   icon: markRaw(DataLine) },
  { path: '/observability', title: '可观测性',   icon: markRaw(Histogram) },
  { path: '/file',          title: '文件管理',   icon: markRaw(Folder) },
  { path: '/system',        title: '系统设置',   icon: markRaw(Setting) }
]

async function handleCmd(cmd: string) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await userStore.logout()
    router.replace('/login')
  }
}
</script>

<style lang="scss" scoped>
.main-layout { height: 100vh; }
.sidebar {
  background: linear-gradient(180deg, #2c3e50 0%, #1a252f 100%);
  color: #e4e7ed;
  display: flex;
  flex-direction: column;
}
.brand {
  padding: 20px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.logo-icon {
  width: 40px; height: 40px; border-radius: 10px;
  background: linear-gradient(135deg, #67c23a, #409eff);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-size: 20px;
}
.brand-name { font-size: 15px; font-weight: 600; color: #fff; }
.brand-slogan { font-size: 11px; color: #909399; margin-top: 2px; }
.menu { flex: 1; border: none; padding-top: 12px; }
:deep(.el-menu-item) {
  height: 48px; line-height: 48px;
  margin: 4px 8px; border-radius: 8px;
}
:deep(.el-menu-item:hover) { background: rgba(255,255,255,0.08); }
:deep(.el-menu-item.is-active) { background: rgba(103,194,58,0.15); }
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  padding: 0 24px;
  height: 60px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #606266;
}
.main-content { padding: 20px; background: #f5f7fa; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
