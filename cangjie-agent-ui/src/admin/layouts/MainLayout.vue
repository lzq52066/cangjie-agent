<template>
  <el-container class="main-layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
      <div class="brand">
        <div class="logo-icon">C</div>
        <div v-show="!collapsed" class="brand-text">
          <div class="brand-name">CangJie Agent</div>
          <div class="brand-slogan">agent.cangjiecloud.cn</div>
        </div>
      </div>
      <el-menu
        :key="openKey"
        :default-active="activeMenu"
        :default-openeds="collapsed ? [] : openMenus"
        :collapse="collapsed"
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
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
        </div>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@admin/store/user'
import type { MenuNode } from '@shared/types'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, Fold, Expand } from '@element-plus/icons-vue'
import MenuTreeNode from './MenuTreeNode.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 侧边栏折叠：窄屏自动收起，支持手动切换
const collapsed = ref(false)
const autoCollapsed = ref(false)
function applyViewport() {
  const narrow = window.innerWidth < 992
  if (narrow && !autoCollapsed.value) {
    autoCollapsed.value = true
    collapsed.value = true
  } else if (!narrow && autoCollapsed.value) {
    autoCollapsed.value = false
    collapsed.value = false
  }
}
onMounted(() => {
  applyViewport()
  window.addEventListener('resize', applyViewport)
})
onBeforeUnmount(() => window.removeEventListener('resize', applyViewport))
// 手动展开后取消自动收起约束
watch(collapsed, v => {
  if (!v && window.innerWidth >= 992) autoCollapsed.value = false
})

const activeMenu = computed(() => {
  if (route.path.startsWith('/system/role')) return '/system/role'
  if (route.path.startsWith('/system/menu')) return '/system/menu'
  if (route.path.startsWith('/observability/eval')) return '/observability/eval'
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
  transition: width .25s ease;
  overflow: hidden;
}
.brand {
  padding: 20px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  min-height: 61px;
  box-sizing: border-box;
}
.brand-text { white-space: nowrap; overflow: hidden; }
.logo-icon {
  width: 40px; height: 40px; border-radius: 10px;
  background: linear-gradient(135deg, #67c23a, #409eff);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-size: 20px;
}
.brand-name { font-size: 15px; font-weight: 600; color: #fff; }
.brand-slogan { font-size: 11px; color: #909399; margin-top: 2px; }
.menu { flex: 1; border: none; padding-top: 12px; width: 100%; }
:deep(.el-menu--collapse) { width: 64px; }
:deep(.el-menu-item) {
  height: 48px; line-height: 48px;
  margin: 4px 8px; border-radius: 8px;
}
:deep(.el-menu--collapse .el-menu-item) { margin: 4px auto; justify-content: center; }
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
.collapse-btn {
  font-size: 20px;
  color: #606266;
  cursor: pointer;
}
.collapse-btn:hover { color: var(--cj-primary, #67c23a); }
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

@media (max-width: 768px) {
  .header { padding: 0 14px; }
  .main-content { padding: 12px; }
  .username { display: none; }
  .brand { padding: 20px 0; justify-content: center; }
}
</style>
