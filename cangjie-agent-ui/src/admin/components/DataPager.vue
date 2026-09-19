<template>
  <el-pagination
    class="pager"
    :background="background"
    :layout="layout"
    :total="total"
    :current-page="currentPage"
    :page-size="pageSize"
    :page-sizes="pageSizes"
    @update:current-page="emit('update:current-page', $event)"
    @update:page-size="emit('update:page-size', $event)"
    @size-change="emit('change')"
    @current-change="emit('change')"
  />
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  total: number
  currentPage: number
  pageSize: number
  pageSizes?: number[]
  layout?: string
  background?: boolean
}>(), {
  pageSizes: () => [10, 20, 50],
  layout: 'total, sizes, prev, pager, next',
  background: true
})

const emit = defineEmits<{
  (e: 'update:current-page', value: number): void
  (e: 'update:page-size', value: number): void
  /** 页码或每页条数变化后触发，用于重新查询 */
  (e: 'change'): void
}>()
</script>

<style lang="scss" scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .pager {
    justify-content: center;
  }
}
</style>
