<template>
  <el-card class="file-view">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="keyword" placeholder="搜索文件名..." clearable style="width:200px"
                  @clear="reload" @keyup.enter="reload">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="category" placeholder="全部分类" clearable style="width:150px" @change="reload">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
      </div>
      <el-upload :show-file-list="false" :before-upload="handleUpload" :disabled="uploading">
        <el-button type="primary" :loading="uploading">
          <el-icon><Upload /></el-icon> 上传文件
        </el-button>
      </el-upload>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="文件名" prop="fileName" min-width="220" show-overflow-tooltip />
      <el-table-column label="分类" prop="category" width="120" />
      <el-table-column label="类型" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ displayType(row.contentType) }}</template>
      </el-table-column>
      <el-table-column label="大小" width="110" align="right">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="存储" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ row.storageType || 'local' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上传时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openFile(row)">预览</el-button>
          <el-button size="small" link type="success" @click="downloadFile(row)">下载</el-button>
          <el-button size="small" link type="warning" @click="copyUrl(row)">复制链接</el-button>
          <el-popconfirm title="确定删除该文件？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button size="small" link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                   :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                   :page-sizes="[10, 20, 50]" @size-change="loadList" @current-change="loadList" />

    <!-- 文件预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewTitle" width="80%" top="5vh" destroy-on-close>
      <div class="preview-container">
        <iframe v-if="previewType === 'iframe'"
                :src="previewUrl" class="preview-iframe" frameborder="0" />
        <div v-else class="preview-unsupported">
          <p>该文件类型不支持在线预览</p>
          <el-button type="primary" @click="downloadCurrentPreview">下载文件</el-button>
        </div>
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Upload } from '@element-plus/icons-vue'
import { fileApi } from '@admin/api/file-api'

const categories = ['document', 'image', 'avatar', 'other']

/** 浏览器可直接内联渲染的文件类型 */
const INLINE_TYPES = [
  'image/', 'video/', 'audio/',
  'application/pdf',
  'text/',
  'application/json',
  'application/xml',
]

const list = ref<any[]>([])
const loading = ref(false)
const uploading = ref(false)
const keyword = ref('')
const category = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 预览弹窗
const previewVisible = ref(false)
const previewTitle = ref('')
const previewUrl = ref('')
const previewType = ref<'iframe' | 'unsupported'>('iframe')
let currentPreviewRow: any = null

function canPreviewInline(contentType?: string): boolean {
  if (!contentType) return false
  return INLINE_TYPES.some(t => contentType.startsWith(t))
}

const MIME_SHORT_MAP: Record<string, string> = {
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'Word (.docx)',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'Excel (.xlsx)',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'PowerPoint (.pptx)',
  'application/msword': 'Word (.doc)',
  'application/vnd.ms-excel': 'Excel (.xls)',
  'application/vnd.ms-powerpoint': 'PowerPoint (.ppt)',
  'application/pdf': 'PDF',
  'application/zip': 'ZIP 压缩包',
  'application/x-rar-compressed': 'RAR 压缩包',
  'application/x-7z-compressed': '7Z 压缩包',
  'application/gzip': 'GZIP 压缩包',
  'application/json': 'JSON',
  'application/xml': 'XML',
  'text/plain': '文本文件',
  'text/html': 'HTML',
  'text/css': 'CSS',
  'text/javascript': 'JavaScript',
  'image/png': 'PNG 图片',
  'image/jpeg': 'JPEG 图片',
  'image/gif': 'GIF 图片',
  'image/webp': 'WebP 图片',
  'image/svg+xml': 'SVG 图片',
  'video/mp4': 'MP4 视频',
  'audio/mpeg': 'MP3 音频',
  'audio/wav': 'WAV 音频',
}

function displayType(contentType?: string): string {
  if (!contentType) return '-'
  return MIME_SHORT_MAP[contentType] || contentType
}

function formatSize(size?: number) {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(2) + ' MB'
}

async function loadList() {
  loading.value = true
  try {
    const page = await fileApi.list({
      keyword: keyword.value, category: category.value,
      pageNum: pageNum.value, pageSize: pageSize.value
    })
    list.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  loadList()
}

async function handleUpload(file: File) {
  uploading.value = true
  try {
    await fileApi.upload(file, category.value || undefined)
    ElMessage.success('上传成功')
    reload()
  } finally {
    uploading.value = false
  }
  return false
}

async function handleDelete(id: string) {
  await fileApi.remove(id)
  ElMessage.success('删除成功')
  loadList()
}

function openFile(row: any) {
  currentPreviewRow = row
  previewTitle.value = row.fileName || '文件预览'
  if (canPreviewInline(row.contentType)) {
    previewType.value = 'iframe'
    previewUrl.value = location.origin + fileApi.previewUrl(row.id)
  } else {
    previewType.value = 'unsupported'
    previewUrl.value = ''
  }
  previewVisible.value = true
}

function downloadCurrentPreview() {
  if (currentPreviewRow) downloadFile(currentPreviewRow)
}

function downloadFile(row: any) {
  const url = location.origin + fileApi.downloadUrl(row.id)
  const a = document.createElement('a')
  a.href = url
  a.download = row.fileName || ''
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

async function copyUrl(row: any) {
  const url = row.url || (location.origin + fileApi.previewUrl(row.id))
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制：' + url)
  }
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar-left { display: flex; gap: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.preview-container { min-height: 400px; display: flex; align-items: center; justify-content: center; }
.preview-iframe { width: 100%; height: 70vh; border: none; }
.preview-unsupported { text-align: center; color: #999; }
</style>
