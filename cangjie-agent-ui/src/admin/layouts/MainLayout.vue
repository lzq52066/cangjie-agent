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
        :key="openKey"
        :default-active="activeMenu"
        :default-openeds="openMenus"
        class="menu"
        router
        background-color="transparent"
        text-color="#e4e7ed"
        active-text-color="#67c23a"
      >
        <MenuTreeNode v-for="item in menus" :key="item.id || item.path" :node="item" />
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@admin/store/user'
import type { MenuNode } from '@shared/types'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, UserFilled } from '@element-plus/icons-vue'
import MenuTreeNode from './MenuTreeNode.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeMenu = computed(() => {
  if (route.path.startsWith('/system/role')) return '/system/role'
  if (route.path.startsWith('/system/menu')) return '/system/menu'
  const segments = route.path.split('/').filter(Boolean)
  return '/' + (segments[0] || 'dashboard')
})
const user = computed(() => userStore.userInfo)
const nickname = computed(() => user.value?.nickname?.slice(0, 1) || 'U')

function buildMenuTree(items: MenuNode[]): MenuNode[] {
  const map = new Map<string, MenuNode>()
  const roots: MenuNode[] = []
  for (const item of items) {
    map.set(item.id, { ...item, children: [] })
  }
  for (const item of items) {
    const node = map.get(item.id)!
    const pid = item.parentId
    if (pid && map.has(pid)) {
      const parent = map.get(pid)!
      parent.children = parent.children || []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }
  roots.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
  for (const root of roots) {
    root.children = root.children || []
    root.children.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    pruneEmpty(root)
  }
  return roots
}

function pruneEmpty(node: MenuNode) {
  if (node.children && node.children.length === 0) {
    delete node.children
  } else if (node.children) {
    for (const child of node.children!) pruneEmpty(child)
  }
}

const menus = computed(() => {
  const items = userStore.menus || []
  return buildMenuTree(items.filter(m => m.type !== 'button'))
})

// 当前激活菜单所在分组的 index 列表（用于自动展开对应二级菜单）
const openMenus = computed(() => {
  const result: string[] = []
  const walk = (nodes: MenuNode[], parentIndex?: string) => {
    for (const n of nodes) {
      if (n.path === activeMenu.value && parentIndex) result.push(parentIndex)
      if (n.children && n.children.length) walk(n.children, n.path || n.id)
    }
  }
  walk(menus.value)
  return result
})

// 路由切换时重建菜单，确保分组展开状态正确
const openKey = computed(() => openMenus.value.join('_') || 'root')

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
