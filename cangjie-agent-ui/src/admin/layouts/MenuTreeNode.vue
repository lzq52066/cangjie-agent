<template>
  <el-sub-menu v-if="hasChildren" :index="node.path || node.id">
    <template #title>
      <el-icon v-if="node.icon"><component :is="getIcon(node.icon)" /></el-icon>
      <span>{{ node.name }}</span>
    </template>
    <MenuTreeNode v-for="child in node.children" :key="child.id" :node="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="node.path || node.id">
    <el-icon v-if="node.icon"><component :is="getIcon(node.icon)" /></el-icon>
    <span>{{ node.name }}</span>
  </el-menu-item>
</template>

<script setup lang="ts">
import { computed, markRaw } from 'vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { Menu } from '@element-plus/icons-vue'
import type { MenuNode } from '@shared/types'

const props = defineProps<{ node: MenuNode }>()

defineOptions({ name: 'MenuTreeNode' })

const hasChildren = computed(() => !!(props.node.children && props.node.children.length > 0))

const iconMap: Record<string, any> = {}
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  iconMap[key] = component
}

function getIcon(name?: string) {
  return name && iconMap[name] ? markRaw(iconMap[name]) : markRaw(Menu)
}
</script>
