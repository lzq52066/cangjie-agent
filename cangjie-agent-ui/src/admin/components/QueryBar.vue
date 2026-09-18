<template>
  <div class="query-bar">
    <div class="query-bar__fields">
      <slot />
    </div>
    <div class="query-bar__actions">
      <el-button type="primary" :loading="loading" @click="emit('search')">
        <el-icon v-if="!loading"><Search /></el-icon>{{ searchText }}
      </el-button>
      <el-button @click="emit('reset')">
        <el-icon><RefreshLeft /></el-icon>重置
      </el-button>
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  loading?: boolean
  searchText?: string
}>(), {
  loading: false,
  searchText: '查询'
})

const emit = defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
}>()
</script>

<style lang="scss" scoped>
.query-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.query-bar__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  flex: 1;
  min-width: 0;
}
.query-bar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

@media (max-width: 768px) {
  .query-bar,
  .query-bar__fields {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }
  .query-bar__actions {
    justify-content: flex-end;
  }
}
</style>
