<template>
  <div class="flow-node-card" :class="{ selected: selected }" :style="{ borderTopColor: meta.color }">
    <Handle v-if="data.type !== 'start'" id="left" type="target" :position="Position.Left" class="flow-handle" />

    <div class="node-header" @dblclick="emit('config', id)">
      <span class="node-icon" :style="{ background: meta.color }">
        <el-icon :size="14"><component :is="meta.icon" /></el-icon>
      </span>
      <div class="node-title">
        <div class="node-name">{{ data.name || meta.name }}</div>
        <div class="node-type">{{ meta.name }}</div>
      </div>
      <el-icon class="node-close" @click.stop="emit('remove', id)"><Close /></el-icon>
    </div>

    <div class="node-summary" v-if="summary" @dblclick="emit('config', id)">{{ summary }}</div>

    <Handle v-if="data.type !== 'end'" id="right" type="source" :position="Position.Right" class="flow-handle" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { Close } from '@element-plus/icons-vue'
import { getMeta } from './node-meta'

export interface FlowNodeData {
  type: string
  name: string
  config?: Record<string, any>
}

const props = defineProps<{ id: string; data: FlowNodeData; selected?: boolean }>()
const emit = defineEmits<{
  (e: 'config', id: string): void
  (e: 'remove', id: string): void
}>()

const meta = computed(() => getMeta(props.data.type))

/** 节点配置摘要，一眼看清节点在干什么 */
const summary = computed(() => {
  const c = props.data.config || {}
  switch (props.data.type) {
    case 'llm':
      return c.prompt ? `提示词：${c.prompt}` : (c.modelId ? `模型：${c.modelId}` : '未配置')
    case 'knowledge':
      return c.knowledgeBaseIds?.length ? `${c.knowledgeBaseIds.length} 个知识库，TopK=${c.topK ?? 5}` : '未选择知识库'
    case 'tool':
      return c.toolId ? `工具：${c.toolId}` : '未选择工具'
    case 'condition':
      return c.expression ? `表达式：${c.expression}` : '未配置表达式'
    case 'loop':
      return c.items ? `遍历：${c.items}` : `循环 ${c.maxIterations ?? 10} 次`
    case 'api':
      return c.url ? `${c.method || 'GET'} ${c.url}` : '未配置 URL'
    case 'code':
      return c.script ? c.script.slice(0, 60) : '未配置脚本'
    case 'end':
      return c.output ? `输出：${c.output}` : ''
    default:
      return ''
  }
})
</script>

<style lang="scss" scoped>
.flow-node-card {
  width: 220px; background: #fff; border: 1px solid #e4e7ed; border-top: 3px solid #409eff;
  border-radius: 8px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); font-size: 13px;

  &.selected { border-color: #409eff; box-shadow: 0 2px 12px rgba(64, 158, 255, 0.35); }

  .node-header {
    display: flex; align-items: center; gap: 8px; padding: 8px 10px; cursor: pointer;

    .node-icon {
      width: 26px; height: 26px; border-radius: 6px; color: #fff;
      display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    }
    .node-title { flex: 1; overflow: hidden; }
    .node-name {
      font-weight: 600; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .node-type { font-size: 11px; color: #909399; }
    .node-close {
      color: #c0c4cc; cursor: pointer; flex-shrink: 0;
      &:hover { color: #f56c6c; }
    }
  }

  .node-summary {
    padding: 0 10px 8px; color: #606266; font-size: 12px; line-height: 1.5;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer;
  }
}

.flow-handle {
  width: 10px !important; height: 10px !important; background: #409eff !important;
  border: 2px solid #fff !important;
  opacity: 0; transition: opacity 0.2s;
  pointer-events: all; /* 保持拖拽命中区域，即使不可见也能接线 */
}

/* 仅悬停节点时显示连接点，平时保持干净 */
.flow-node-card:hover .flow-handle { opacity: 1; }
</style>
