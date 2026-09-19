<template>
  <div class="execution-detail">
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="执行 ID">{{ execution.id }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag size="small" :type="statusTag">{{ statusLabel }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="当前节点">{{ execution.currentNode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="耗时">{{ execution.duration ?? '-' }} ms</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ execution.startTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="结束时间">{{ execution.endTime || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-alert v-if="execution.errorMessage" type="error" :closable="false" show-icon
              :title="execution.errorMessage" style="margin-top:12px" />

    <div class="io-block">
      <div class="io-label">输入</div>
      <pre class="io-content">{{ prettyJson(execution.inputs) }}</pre>
    </div>
    <div class="io-block">
      <div class="io-label">输出</div>
      <pre class="io-content">{{ prettyJson(execution.outputs) }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { prettyJson } from '@admin/utils/format'

const props = defineProps<{ execution: Record<string, any> }>()

const statusLabel = computed(() =>
  ({ running: '执行中', completed: '成功', failed: '失败' } as any)[props.execution.status] || props.execution.status)
const statusTag = computed<any>(() =>
  ({ running: 'warning', completed: 'success', failed: 'danger' } as any)[props.execution.status] || 'info')
</script>

<style lang="scss" scoped>
.io-block { margin-top: 12px; }
.io-label { font-weight: 600; color: #606266; margin-bottom: 6px; font-size: 13px; }
.io-content {
  margin: 0; background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 12px; max-height: 220px; overflow: auto;
  white-space: pre-wrap; word-break: break-all; font-size: 12px; line-height: 1.7;
}
</style>
