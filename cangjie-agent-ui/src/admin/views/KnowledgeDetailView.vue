<template>
  <div class="kb-detail" v-loading="loading">
    <!-- 头部信息 -->
    <el-card class="info-card">
      <div class="kb-header">
        <div class="kb-info">
          <el-button link @click="$router.push('/knowledge')">
            <el-icon><ArrowLeft /></el-icon> 返回列表
          </el-button>
          <h2 class="kb-title">{{ kb.name }}</h2>
          <el-tag size="small" type="success">{{ strategyLabel(kb.splitStrategy) }}</el-tag>
          <span class="kb-meta" v-if="kb.splitStrategy === 'custom'">段落: {{ kb.chunkSize }}字符</span>
          <span class="kb-meta">文档: {{ kb.documentCount }}</span>
          <span class="kb-meta">段落: {{ kb.paragraphCount }}</span>
        </div>
        <div class="kb-actions">
          
          <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".pdf,.docx,.doc,.txt,.md,.csv,.html">
            <el-button type="primary" :loading="uploading">
              <el-icon><Upload /></el-icon> 上传文档
            </el-button>
          </el-upload>
        </div>
      </div>
    </el-card>

    <!-- Tab 区域 -->
    <el-card style="margin-top: 16px">
      <el-tabs v-model="activeTab">
        <!-- 文档列表 -->
        <el-tab-pane label="文档列表" name="documents">
          <el-table :data="documents" stripe v-loading="docLoading">
            <el-table-column label="文件名" prop="name" min-width="180">
              <template #default="{ row }">
                <el-link type="primary" @click="showParagraphs(row)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="类型" prop="fileType" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small">{{ row.fileType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="90" align="center">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="段落数" prop="paragraphCount" width="70" align="center" />
            <el-table-column label="Token数" prop="tokenCount" width="80" align="center" />
            <el-table-column label="摘要" width="120" align="center">
              <template #default="{ row }">
                <el-button v-if="row.summary" size="small" link type="primary" @click="showSummary(row)">查看</el-button>
                <span v-else class="no-file">-</span>
              </template>
            </el-table-column>
            <el-table-column label="源文件" width="100" align="center">
              <template #default="{ row }">
                <el-button v-if="row.fileId" size="small" link type="info" @click="openFile(row)">
                  查看文件
                </el-button>
                <span v-else class="no-file">-</span>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="160" align="center">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right" align="center">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="showParagraphs(row)">查看切片</el-button>
                <el-button size="small" link type="warning" :loading="reEmbeddingDoc === row.id" @click="handleReEmbedDoc(row.id)">重新向量化</el-button>
                <el-popconfirm title="确定删除？" @confirm="handleDeleteDoc(row.id)">
                  <template #reference>
                    <el-button size="small" link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                         :total="docTotal" v-model:current-page="docPageNum" v-model:page-size="docPageSize"
                         :page-sizes="[10, 20, 50]" @size-change="loadDocuments" @current-change="loadDocuments" />
        </el-tab-pane>

        <!-- 检索测试 -->
        <el-tab-pane label="检索测试" name="retrieval">
          <div class="retrieval-panel">
            <div class="retrieval-mode-tip">
              <el-tag size="small" :type="kb.searchMode === 'two_stage' ? 'primary' : 'info'" effect="plain">
                {{ kb.searchMode === 'two_stage' ? '摘要先行检索' : '段落检索' }}
              </el-tag>
              <span class="mode-tip-text">检索模式取自知识库配置，可在知识库编辑中切换</span>
            </div>
            <div class="query-row">
              <el-input v-model="retrievalQuery" placeholder="输入检索文本..." type="textarea" :rows="2"
                        @keydown.ctrl.enter="handleSearch" />
              <el-button type="primary" :loading="searching" @click="handleSearch" style="margin-top: 12px">
                <el-icon><Search /></el-icon> 检索
              </el-button>
            </div>

            <div class="results-area" v-if="results.length > 0">
              <div class="results-header">检索结果（{{ results.length }} 条，按 RRF 融合排序）</div>
              <div v-for="(r, i) in results" :key="i" class="result-item">
                <div class="result-meta">
                  <span class="rank">#{{ i + 1 }}</span>
                  <el-tag size="small" type="info">{{ r.documentName || r.documentId }}</el-tag>
                  <span class="score">RRF: {{ r.finalScore.toFixed(4) }}</span>
                  <span class="score-sub">向量: {{ r.vectorScore.toFixed(4) }}</span>
                  <span class="score-sub">全文: {{ r.fullTextScore.toFixed(4) }}</span>
                </div>
                <div class="result-content">{{ r.content }}</div>
              </div>
            </div>
            <el-empty v-else-if="searched" description="无检索结果" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 段落预览对话框 -->
    <el-dialog v-model="paragraphDialog" :title="`段落切片 - ${currentDoc?.name || ''}`" width="800px" top="5vh">
      <el-table :data="paragraphs" stripe max-height="600" v-loading="paraLoading">
        <el-table-column label="#" prop="chunkIndex" width="60" align="center" />
        <el-table-column label="标题" prop="title" width="180" show-overflow-tooltip />
        <el-table-column label="内容" min-width="300">
          <template #default="{ row }">
            <div class="para-content">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="字符" prop="charCount" width="70" align="center" />
        <el-table-column label="Token" prop="tokenCount" width="70" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.vectorStatus === 'embedded' ? 'success' : 'warning'">
              {{ row.vectorStatus === 'embedded' ? '已嵌入' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     :total="paraTotal" v-model:current-page="paraPageNum" v-model:page-size="paraPageSize"
                     :page-sizes="[10, 20, 50]" @size-change="loadParagraphs" @current-change="loadParagraphs" />
    </el-dialog>

    <!-- 文档摘要对话框 -->
    <el-dialog v-model="summaryDialog" :title="`文档摘要 - ${summaryDoc?.name || ''}`" width="640px">
      <div class="summary-content">{{ summaryDoc?.summary }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Upload, Search } from '@element-plus/icons-vue'
import { knowledgeApi } from '@admin/api/knowledge-api'

const route = useRoute()
const kbId = route.params.id as string

const loading = ref(false)
const kb = ref<any>({})
const documents = ref<any[]>([])
const docLoading = ref(false)
const docPageNum = ref(1)
const docPageSize = ref(10)
const docTotal = ref(0)
const uploading = ref(false)
const activeTab = ref('documents')

const reEmbeddingDoc = ref('')

// 检索
const retrievalQuery = ref('')
const results = ref<any[]>([])
const searching = ref(false)
const searched = ref(false)

// 段落对话框
const paragraphDialog = ref(false)
const currentDoc = ref<any>(null)
const paragraphs = ref<any[]>([])
const paraLoading = ref(false)
const paraPageNum = ref(1)
const paraPageSize = ref(10)
const paraTotal = ref(0)

// 摘要对话框
const summaryDialog = ref(false)
const summaryDoc = ref<any>(null)

async function loadKb() {
  loading.value = true
  try {
    kb.value = await knowledgeApi.get(kbId)
  } finally {
    loading.value = false
  }
}

async function loadDocuments() {
  docLoading.value = true
  try {
    const page = await knowledgeApi.listDocuments(kbId, {
      pageNum: docPageNum.value,
      pageSize: docPageSize.value
    })
    documents.value = page?.list || []
    docTotal.value = page?.total || 0
  } finally {
    docLoading.value = false
  }
}

/** 上传/删除文档后回到第一页再加载 */
function reloadDocuments() {
  docPageNum.value = 1
  loadDocuments()
}

async function loadParagraphs() {
  if (!currentDoc.value) return
  paraLoading.value = true
  try {
    const page = await knowledgeApi.listParagraphs(currentDoc.value.id, {
      pageNum: paraPageNum.value,
      pageSize: paraPageSize.value
    })
    paragraphs.value = page?.list || []
    paraTotal.value = page?.total || 0
  } finally {
    paraLoading.value = false
  }
}

async function handleUpload(file: File) {
  uploading.value = true
  try {
    await knowledgeApi.uploadDocument(kbId, file)
    ElMessage.success('上传成功，文档正在处理中')
    reloadDocuments()
    loadKb()
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

async function handleDeleteDoc(docId: string) {
  await knowledgeApi.removeDocument(docId)
  ElMessage.success('删除成功')
  reloadDocuments()
  loadKb()
}

async function handleReEmbedDoc(docId: string) {
  reEmbeddingDoc.value = docId
  try {
    await knowledgeApi.reEmbedDocument(docId)
    ElMessage.success('文档重新向量化完成')
    loadDocuments()
  } catch (e) {
    ElMessage.error('重新向量化失败')
  } finally {
    reEmbeddingDoc.value = ''
  }
}

/** 切换选中文档时回到第一页再加载段落 */
async function showParagraphs(doc: any) {
  currentDoc.value = doc
  paragraphDialog.value = true
  paraPageNum.value = 1
  await loadParagraphs()
}

function showSummary(doc: any) {
  summaryDoc.value = doc
  summaryDialog.value = true
}

async function handleSearch() {
  if (!retrievalQuery.value.trim()) return
  searching.value = true
  searched.value = true
  try {
    results.value = await knowledgeApi.search({
      query: retrievalQuery.value,
      knowledgeBaseId: kbId,
      topK: 10
    })
  } finally {
    searching.value = false
  }
}

function strategyLabel(code: string) {
  return { smart: '智能分段', custom: '自定义分段' }[code] || code
}
function statusLabel(code: string) {
  return { pending: '待处理', parsing: '解析中', splitting: '切片中', embedding: '向量化中', completed: '已完成', failed: '失败' }[code] || code
}
function statusTag(code: string): any {
  return { completed: 'success', failed: 'danger', pending: 'info', parsing: 'warning', splitting: 'warning', embedding: 'warning' }[code] || ''
}
function formatSize(bytes: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}
function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

function openFile(row: any) {
  // 通过文件管理接口直接打开文件预览
  window.open(`/#/file?highlight=${row.fileId}`, '_blank')
}

onMounted(() => {
  loadKb()
  loadDocuments()
})
</script>

<style lang="scss" scoped>
.kb-header { display: flex; justify-content: space-between; align-items: center; }
.kb-info { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.kb-title { margin: 0; font-size: 18px; }
.kb-meta { color: #909399; font-size: 13px; }
.retrieval-panel { padding: 8px 0; }
.retrieval-mode-tip { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.mode-tip-text { font-size: 12px; color: #909399; }
.summary-content {
  color: #303133; line-height: 1.9; white-space: pre-wrap;
  max-height: 480px; overflow-y: auto;
}
.results-area { margin-top: 20px; }
.results-header { font-weight: 600; margin-bottom: 12px; color: #606266; }
.result-item {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 12px 16px; margin-bottom: 12px;
  transition: box-shadow .2s;
}
.result-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.result-meta { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; font-size: 12px; }
.rank { font-weight: 700; color: #67c23a; }
.score { color: #409eff; font-weight: 600; }
.score-sub { color: #909399; }
.result-content { color: #303133; line-height: 1.8; white-space: pre-wrap; }
.no-file { color: #c0c4cc; }
.pager { margin-top: 16px; justify-content: flex-end; }
.para-content {
  max-height: 80px; overflow: hidden; text-overflow: ellipsis;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical;
}
</style>
