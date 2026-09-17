<template>
  <div class="obs-view">
    <!-- 汇总面板 -->
    <div class="stat-grid">
      <el-card v-for="s in stats" :key="s.key" shadow="hover" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
        <div class="stat-unit">{{ s.unit }}</div>
      </el-card>
    </div>

    <el-card>
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="操作日志" name="logs" />
        <el-tab-pane label="系统指标" name="metrics" />
        <el-tab-pane label="调用追踪" name="traces" />
        <el-tab-pane label="LLM 调用" name="llm-traces" />
      </el-tabs>

      <!-- 操作日志 -->
      <template v-if="activeTab === 'logs'">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="logQuery.module" placeholder="模块" clearable style="width:150px" @keyup.enter="loadLogs" />
            <el-input v-model="logQuery.action" placeholder="操作" clearable style="width:150px" @keyup.enter="loadLogs" />
            <el-select v-model="logQuery.status" placeholder="全部状态" clearable style="width:130px" @change="loadLogs">
              <el-option label="成功" value="success" />
              <el-option label="失败" value="fail" />
            </el-select>
            <el-button type="primary" @click="loadLogs">查询</el-button>
          </div>
          <el-button @click="refresh"><el-icon><Refresh /></el-icon> 刷新面板</el-button>
        </div>
        <el-table :data="operationLogs" v-loading="loading" stripe>
          <el-table-column label="时间" prop="createTime" width="170" />
          <el-table-column label="模块" prop="module" width="120" />
          <el-table-column label="操作" prop="action" width="130" />
          <el-table-column label="接口" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.method }} {{ row.uri }}</template>
          </el-table-column>
          <el-table-column label="用户" prop="username" width="120" />
          <el-table-column label="IP" prop="ip" width="130" />
          <el-table-column label="耗时" width="100" align="center">
            <template #default="{ row }">{{ row.duration != null ? row.duration + ' ms' : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
                <el-tag size="small" type="danger">失败</el-tag>
              </el-tooltip>
              <el-tag v-else size="small" type="success">成功</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="showLog(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 系统指标 -->
      <template v-else-if="activeTab === 'metrics'">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-select v-model="metricQuery.metricType" placeholder="全部类型" clearable style="width:120px"
                       @change="loadMetricCharts">
              <el-option v-for="t in metricTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-date-picker
              v-model="metricQuery.dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 360px"
              @change="loadMetricCharts"
            />
            <el-button type="primary" :loading="loading" @click="loadMetricCharts">查询</el-button>
          </div>
          <div class="toolbar-right">
            <el-button type="success" :loading="collecting" @click="handleCollect">
              <el-icon><Odometer /></el-icon> 立即采集
            </el-button>
          </div>
        </div>

        <div v-if="!hasMetricsData && !loading" class="empty-hint">
          <el-empty description="暂无指标数据，请点击【立即采集】或等待自动采集" />
        </div>

        <template v-else>
          <el-alert v-if="metricsTruncated" type="warning" :closable="false" show-icon
                    :title="`时间范围内共 ${metricsTotal} 条指标，图表仅展示最新 ${systemMetrics.length} 条，建议缩小时间范围或按类型筛选`"
                    style="margin-bottom:12px" />

          <!-- 同一类型内量纲不同的指标拆成多张图，避免坐标轴被最大量程拉伸后小量级曲线贴底 -->
          <div class="metrics-charts">
            <el-card v-for="g in chartGroups" :key="g" class="chart-card" shadow="hover">
              <template #header><span class="chart-title">{{ chartData[g].title }}</span></template>
              <div :ref="el => setChartRef(g, el)" class="chart-box"></div>
            </el-card>
          </div>
        </template>
      </template>

      <!-- 调用追踪 -->
      <template v-else-if="activeTab === 'traces'">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="traceQuery.traceId" placeholder="TraceId" clearable style="width:220px"
                      @keyup.enter="loadTraces" />
            <el-input v-model="traceQuery.module" placeholder="模块" clearable style="width:150px"
                      @keyup.enter="loadTraces" />
            <el-input v-model="traceQuery.action" placeholder="操作" clearable style="width:150px"
                      @keyup.enter="loadTraces" />
            <el-button type="primary" @click="loadTraces">查询</el-button>
          </div>
        </div>
        <el-table :data="callTraces" v-loading="loading" stripe>
          <el-table-column label="开始时间" width="180">
            <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="TraceId" prop="traceId" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" @click="filterByTrace(row.traceId)">{{ row.traceId }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="模块" prop="module" width="130" />
          <el-table-column label="操作" prop="action" width="150" />
          <el-table-column label="服务" prop="serviceName" width="140" />
          <el-table-column label="耗时" width="100" align="center">
            <template #default="{ row }">{{ row.duration != null ? row.duration + ' ms' : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="信息" prop="message" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>

      <!-- LLM 调用追踪 -->
      <template v-else-if="activeTab === 'llm-traces'">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-input v-model="llmTraceQuery.traceId" placeholder="TraceId" clearable style="width:200px"
                      @keyup.enter="loadLlmTraces" />
            <el-input v-model="llmTraceQuery.appName" placeholder="应用名称" clearable style="width:150px"
                      @keyup.enter="loadLlmTraces" />
            <el-input v-model="llmTraceQuery.modelName" placeholder="模型名称" clearable style="width:150px"
                      @keyup.enter="loadLlmTraces" />
            <el-input v-model="llmTraceQuery.sessionId" placeholder="会话ID" clearable style="width:180px"
                      @keyup.enter="loadLlmTraces" />
            <el-input v-model="llmTraceQuery.promptKeyword" placeholder="输入关键词" clearable style="width:140px"
                      @keyup.enter="loadLlmTraces" />
            <el-input v-model="llmTraceQuery.responseKeyword" placeholder="输出关键词" clearable style="width:140px"
                      @keyup.enter="loadLlmTraces" />
            <el-select v-model="llmTraceQuery.status" placeholder="全部状态" clearable style="width:120px" @change="loadLlmTraces">
              <el-option label="成功" value="success" />
              <el-option label="失败" value="fail" />
            </el-select>
            <el-date-picker
              v-model="llmTraceQuery.timeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 340px"
              @change="loadLlmTraces"
            />
            <el-button type="primary" @click="loadLlmTraces">查询</el-button>
          </div>
        </div>
        <el-table :data="llmTraces" v-loading="loading" stripe @row-click="showLlmDetail">
          <el-table-column label="开始时间" width="170">
            <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="TraceId" prop="traceId" min-width="180" show-overflow-tooltip />
          <el-table-column label="应用名称" prop="appName" min-width="120" />
          <el-table-column label="模型" prop="modelName" min-width="100" />
          <el-table-column label="输入" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ truncateContent(row.promptContent) }}</template>
          </el-table-column>
          <el-table-column label="输出" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ truncateContent(row.responseContent) }}</template>
          </el-table-column>
          <el-table-column label="Token" width="130" align="center">
            <template #default="{ row }">
              <span style="font-size:12px">{{ row.inputTokens ?? '-' }} / {{ row.outputTokens ?? '-' }} / {{ row.totalTokens ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90" align="center">
            <template #default="{ row }">{{ row.duration != null ? row.duration + ' ms' : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click.stop="showLlmDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-pagination v-if="activeTab !== 'metrics'" class="pager" background layout="total, sizes, prev, pager, next"
                     :total="total" v-model:current-page="pageNum" v-model:page-size="pageSize"
                     :page-sizes="[10, 20, 50, 100]" @size-change="reload" @current-change="reload" />
    </el-card>

    <!-- 日志详情 -->
    <el-dialog v-model="logDialog" title="日志详情" width="720px" destroy-on-close>
      <el-descriptions v-if="currentLog" :column="2" border size="small">
        <el-descriptions-item label="模块">{{ currentLog.module }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ currentLog.action }}</el-descriptions-item>
        <el-descriptions-item label="接口">{{ currentLog.method }} {{ currentLog.uri }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.duration ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="用户">{{ currentLog.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="TraceId" :span="2">{{ currentLog.traceId || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="currentLog?.errorMessage" type="error" :closable="false" show-icon
                :title="currentLog.errorMessage" style="margin-top:12px" />
      <template v-if="currentLog">
        <el-divider>请求参数</el-divider>
        <pre class="code-block">{{ pretty(currentLog.params) }}</pre>
        <el-divider>响应结果</el-divider>
        <pre class="code-block">{{ pretty(currentLog.result) }}</pre>
      </template>
    </el-dialog>

    <!-- LLM 调用详情抽屉 -->
    <el-drawer v-model="llmDetailDrawer" title="LLM 调用详情" size="720px" destroy-on-close>
      <template v-if="currentLlmTrace">
        <el-descriptions :column="2" border size="small" class="llm-detail-desc">
          <el-descriptions-item label="开始时间">{{ formatTime(currentLlmTrace.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ currentLlmTrace.duration != null ? currentLlmTrace.duration + ' ms' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="应用">{{ currentLlmTrace.appName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ currentLlmTrace.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话 ID">{{ currentLlmTrace.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ currentLlmTrace.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="TraceId" :span="2">{{ currentLlmTrace.traceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="RequestId" :span="2">{{ currentLlmTrace.requestId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="输入 Token">{{ currentLlmTrace.inputTokens ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="输出 Token">{{ currentLlmTrace.outputTokens ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="总 Token">{{ currentLlmTrace.totalTokens ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="FinishReason">{{ currentLlmTrace.finishReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态" :span="2">
            <el-tag size="small" :type="currentLlmTrace.status === 'success' ? 'success' : 'danger'">
              {{ currentLlmTrace.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="currentLlmTrace.errorMessage" type="error" :closable="false" show-icon
                  :title="currentLlmTrace.errorMessage" style="margin-top:12px" />
        <el-divider>输入（Prompt）</el-divider>
        <div class="llm-content-block">{{ currentLlmTrace.promptContent || '-' }}</div>
        <el-divider>输出（Response）</el-divider>
        <div class="llm-content-block">{{ currentLlmTrace.responseContent || '-' }}</div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Odometer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { observabilityApi } from '@admin/api/observability-api'

const metricTypes = [
  { label: 'CPU', value: 'cpu' },
  { label: '内存', value: 'memory' },
  { label: '内存池', value: 'memory_pool' },
  { label: '堆外内存', value: 'buffer_pool' },
  { label: '线程', value: 'thread' },
  { label: '文件句柄', value: 'file_descriptor' },
  { label: 'JVM', value: 'jvm' },
  { label: '磁盘', value: 'disk' },
  { label: 'GC', value: 'gc' },
]

const activeTab = ref<'logs' | 'metrics' | 'traces' | 'llm-traces'>('logs')
const loading = ref(false)
const collecting = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dashboard = ref<Record<string, any>>({})
const stats = computed(() => [
  { key: 'chat', label: '今日对话', value: dashboard.value.todayChatCount ?? 0, unit: '次', color: '#409eff' },
  { key: 'model', label: '模型调用', value: dashboard.value.todayModelCallCount ?? 0, unit: '次', color: '#67c23a' },
  { key: 'error', label: '今日错误', value: dashboard.value.todayErrorCount ?? 0, unit: '条', color: '#f56c6c' },
  { key: 'avg', label: '平均耗时', value: formatNumber(dashboard.value.avgDuration), unit: 'ms', color: '#e6a23c' },
  { key: 'log', label: '操作日志', value: dashboard.value.todayLogCount ?? 0, unit: '条', color: '#909399' },
  { key: 'trace', label: '调用追踪', value: dashboard.value.todayTraceCount ?? 0, unit: '条', color: '#7c4dff' }
])

const operationLogs = ref<any[]>([])
const logQuery = reactive({ module: '', action: '', status: '' })
const metricQuery = reactive({ metricType: '', dateRange: null as [string, string] | null })
const callTraces = ref<any[]>([])
const traceQuery = reactive({ traceId: '', module: '', action: '' })
const llmTraces = ref<any[]>([])
const llmTraceQuery = reactive({ traceId: '', appName: '', modelName: '', sessionId: '', promptKeyword: '', responseKeyword: '', status: '', timeRange: null as [string, string] | null })

// 图表相关
interface ChartGroup {
  title: string
  categories: string[]
  series: { name: string; data: (number | null)[] }[]
  multiDay: boolean
}

const systemMetrics = ref<any[]>([])
const hasMetricsData = computed(() => systemMetrics.value.length > 0)
const metricsTotal = ref(0)
const metricsTruncated = computed(() => metricsTotal.value > systemMetrics.value.length)
const chartRefs: Record<string, any> = {}
const chartInstances: Record<string, echarts.ECharts> = {}
const chartData = reactive<Record<string, ChartGroup>>({})
// buildChartData 已按类型、单位排好序，这里保持卡片顺序与 metricTypes 一致
const chartGroups = computed(() => Object.keys(chartData))

function setChartRef(key: string, el: any) {
  if (el) chartRefs[key] = el
}

const TYPE_LABEL: Record<string, string> = Object.fromEntries(metricTypes.map(t => [t.value, t.label]))

/** 拆分后图表的标题，未命中时回退为「类型 (单位)」 */
const GROUP_TITLE_CN: Record<string, string> = {
  'cpu#核': 'CPU 核数 / 负载',
  'jvm#秒': 'JVM 运行时长 (秒)',
  'jvm#个': '类加载数量 (个)',
  'file_descriptor#%': '文件句柄使用率 (%)',
  'gc#次': 'GC 回收次数 (次)',
  'gc#ms': 'GC 回收耗时 (ms)',
  'buffer_pool#个': '堆外缓冲区数量 (个)',
}

/**
 * 图表分组键：同一类型下单位不同的指标不能共用一个坐标轴，
 * 否则小量级曲线会被大量程压成直线（如 GC 的回收次数与耗时、JVM 的运行时长与类加载数）。
 * CPU 可用核数与系统负载均值同为「核」量级，合并到一张图更有参照意义。
 */
function resolveGroupKey(metricType: string, unit?: string): string {
  const u = (unit || '').trim()
  if (metricType === 'cpu' && (u === '核' || u === '')) return 'cpu#核'
  return `${metricType}#${u}`
}

function groupTitle(metricType: string, unit: string): string {
  const key = resolveGroupKey(metricType, unit)
  if (GROUP_TITLE_CN[key]) return GROUP_TITLE_CN[key]
  const label = TYPE_LABEL[metricType] || metricType
  return unit ? `${label} (${unit})` : label
}

// 指标名中英映射
const METRIC_NAME_CN: Record<string, string> = {
  // 内存
  heap_used: '堆内存已用',
  heap_max: '堆内存上限',
  heap_committed: '堆内存已提交',
  non_heap_used: '非堆内存已用',
  // 线程
  thread_count: '线程数',
  daemon_thread_count: '守护线程数',
  peak_thread_count: '峰值线程数',
  total_started_thread_count: '累计启动线程数',
  runnable_thread_count: '可运行线程数',
  blocked_thread_count: '阻塞线程数',
  waiting_thread_count: '等待线程数',
  timed_waiting_thread_count: '超时等待线程数',
  deadlock_thread_count: '死锁线程数',
  // 文件句柄
  open_file_descriptors: '已打开句柄数',
  max_file_descriptors: '句柄数上限',
  file_descriptor_usage: '句柄使用率',
  // JVM
  uptime: 'JVM 运行时长',
  loaded_class_count: '已加载类数',
  total_loaded_class_count: '累计加载类数',
  unloaded_class_count: '已卸载类数',
  // CPU
  available_processors: '可用处理器',
  system_load_average: '系统负载均值',
  process_cpu_load: '进程CPU使用率',
  system_cpu_load: '系统CPU使用率',
  // GC
  collection_count: 'GC次数',
  collection_time: 'GC耗时',
  full_gc_count: 'Full GC次数',
  full_gc_time: 'Full GC耗时',
}

// GC 收集器名中英映射（JVM 按收集器实现上报，未命中时保留原名）
const GC_COLLECTOR_CN: Record<string, string> = {
  // G1
  'G1 Young Generation': 'G1 年轻代',
  'G1 Old Generation': 'G1 老年代',
  // Parallel / Serial / CMS
  'PS Scavenge': 'Parallel 年轻代',
  'PS MarkSweep': 'Parallel 老年代',
  'ParNew': 'CMS 年轻代',
  'ConcurrentMarkSweep': 'CMS 老年代',
  'Serial Scavenge': 'Serial 年轻代',
  'Serial Old': 'Serial 老年代',
  'Copy': 'Serial 年轻代',
  'MarkSweepCompact': 'Serial 老年代',
  // ZGC（非分代时每次回收整个堆，Pauses 为单次回收内的停顿）
  'ZGC': 'ZGC 回收',
  'ZGC Cycles': 'ZGC 整堆回收',
  'ZGC Pauses': 'ZGC 停顿',
  'ZGC Minor GC': 'ZGC 年轻代',
  'ZGC Major GC': 'ZGC 老年代',
  // Shenandoah
  'Shenandoah': 'Shenandoah 回收',
  'Shenandoah Cycles': 'Shenandoah 整堆回收',
  'Shenandoah Pauses': 'Shenandoah 停顿',
  'Shenandoah Young': 'Shenandoah 年轻代',
  'Shenandoah Old': 'Shenandoah 老年代',
}

// 内存池 / 堆外缓冲区名中英映射（JVM 按实现上报池名，未命中时保留原名）
const POOL_NAME_CN: Record<string, string> = {
  Metaspace: '元空间',
  'Compressed Class Space': '压缩类空间',
  "CodeHeap 'non-nmethods'": '代码堆(非方法)',
  "CodeHeap 'profiled nmethods'": '代码堆(profiled)',
  "CodeHeap 'non-profiled nmethods'": '代码堆(non-profiled)',
  direct: '直接内存',
  mapped: '映射内存',
  "mapped - 'non-volatile memory'": '映射内存(非易失)',
}

// 池类指标后缀含义
const POOL_SUFFIX_CN: Record<string, string> = {
  used: '已用量',
  max: '上限',
  count: '缓冲区数量',
  memory_used: '已用量',
  total_capacity: '总容量',
}

function translateMetricName(name: string): string {
  // 精确匹配
  if (METRIC_NAME_CN[name]) return METRIC_NAME_CN[name]
  // 内存池 / 堆外缓冲区指标: <池名>_used / _max / _count / _memory_used / _total_capacity
  const pool = name.match(/^(.+?)_(total_capacity|memory_used|count|max|used)$/)
  if (pool && POOL_NAME_CN[pool[1]]) return POOL_NAME_CN[pool[1]] + ' ' + POOL_SUFFIX_CN[pool[2]]
  // 磁盘指标: xxx_total / xxx_usable / xxx_used
  if (name.endsWith('_total')) return name.replace(/_total$/, '') + ' 总空间'
  if (name.endsWith('_usable')) return name.replace(/_usable$/, '') + ' 可用空间'
  if (name.endsWith('_used')) return name.replace(/_used$/, '') + ' 已用空间'
  // GC 指标: <收集器名>_collection_count / <收集器名>_collection_time
  const gcCount = name.match(/^(.+)_collection_count$/)
  if (gcCount) return translateGcCollector(gcCount[1]) + ' GC次数'
  const gcTime = name.match(/^(.+)_collection_time$/)
  if (gcTime) return translateGcCollector(gcTime[1]) + ' GC耗时'
  return name
}

function translateGcCollector(collectorName: string): string {
  return GC_COLLECTOR_CN[collectorName] || collectorName
}

function buildChartData(records: any[]): Record<string, ChartGroup> {
  const result: Record<string, ChartGroup> = {}
  const sorted = [...records].sort((a, b) =>
    new Date(a.collectTime).getTime() - new Date(b.collectTime).getTime()
  )
  const timeSet = new Set<string>()
  sorted.forEach(r => timeSet.add(r.collectTime))
  const times = Array.from(timeSet)

  // 判断是否跨天
  let multiDay = false
  if (times.length >= 2) {
    const first = times[0].substring(0, 10)
    const last = times[times.length - 1].substring(0, 10)
    multiDay = first !== last
  }

  const grouped: Record<string, Record<string, Map<string, number>>> = {}
  const meta: Record<string, { metricType: string; unit: string }> = {}
  for (const r of sorted) {
    const key = resolveGroupKey(r.metricType, r.unit)
    if (!grouped[key]) {
      grouped[key] = {}
      meta[key] = { metricType: r.metricType, unit: (r.unit || '').trim() }
    }
    if (!grouped[key][r.metricName]) grouped[key][r.metricName] = new Map()
    grouped[key][r.metricName].set(r.collectTime, r.metricValue)
  }

  // 卡片顺序与筛选器里的类型顺序一致，同一类型内按单位排序
  const typeOrder = metricTypes.map(t => t.value)
  const keys = Object.keys(grouped).sort((a, b) => {
    const diff = typeOrder.indexOf(meta[a].metricType) - typeOrder.indexOf(meta[b].metricType)
    return diff !== 0 ? diff : a.localeCompare(b)
  })
  for (const key of keys) {
    const series: { name: string; data: (number | null)[] }[] = []
    for (const [name, map] of Object.entries(grouped[key])) {
      series.push({ name: translateMetricName(name), data: times.map(t => map.get(t) ?? null) })
    }
    result[key] = { title: groupTitle(meta[key].metricType, meta[key].unit), categories: times, series, multiDay }
  }
  return result
}

function formatAxisTime(t: string, multiDay: boolean): string {
  if (!t) return ''
  // t 格式: "2026-08-20T23:38:34"
  const datePart = t.substring(0, 10)  // "2026-08-20"
  const timePart = t.substring(11, 16) // "23:38"
  // 显示简短日期: "08-20" + 时间
  return multiDay ? datePart.substring(5) + ' ' + timePart : timePart
}

function renderChart(key: string, data: ChartGroup) {
  const dom = chartRefs[key]
  if (!dom) return
  if (chartInstances[key]) {
    chartInstances[key].dispose()
    delete chartInstances[key]
  }
  const chart = echarts.init(dom)
  chartInstances[key] = chart
  const multiDay = data.multiDay || false
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter(params: any) {
        if (!params || params.length === 0) return ''
        // tooltip 中显示完整日期时间
        const fullTime = params[0].axisValue
        let html = fullTime + '<br/>'
        for (const p of params) {
          html += `<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};margin-right:6px"></span>`
          html += `${p.seriesName}: ${p.value != null ? p.value : '-'}<br/>`
        }
        return html
      }
    },
    legend: { top: 0, type: 'scroll' },
    grid: { left: 60, right: 24, top: 36, bottom: data.categories.length > 200 ? 56 : 48 },
    xAxis: {
      type: 'category',
      data: data.categories.map((t: string) => formatAxisTime(t, multiDay)),
      axisLabel: { rotate: 30, fontSize: 11 }
    },
    yAxis: { type: 'value' },
    dataZoom: data.categories.length > 200
      ? [
          { type: 'inside', start: 0, end: 100 },
          { type: 'slider', start: 0, end: 100, height: 16, bottom: 8 }
        ]
      : [],
    series: data.series.map((s: any) => ({
      name: s.name,
      type: 'line',
      data: s.data,
      sampling: 'lttb',
      smooth: true,
      connectNulls: true,
      symbol: 'none'
    }))
  })
}

function renderAllCharts() {
  nextTick(() => {
    // 先释放上一次的实例与分组：切换类型筛选后残留的分组会一直显示旧数据
    for (const key of Object.keys(chartInstances)) {
      chartInstances[key].dispose()
      delete chartInstances[key]
    }
    for (const key of Object.keys(chartRefs)) delete chartRefs[key]
    for (const key of Object.keys(chartData)) delete chartData[key]
    Object.assign(chartData, buildChartData(systemMetrics.value))
    nextTick(() => {
      for (const key of Object.keys(chartData)) {
        renderChart(key, chartData[key])
      }
    })
  })
}

function formatNumber(value: any) {
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return Number.isInteger(num) ? String(num) : num.toFixed(2)
}

// 时间格式化为 年-月-日 时:分:秒
function formatTime(value?: string) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function pretty(text?: string) {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

async function loadDashboard() {
  try {
    dashboard.value = await observabilityApi.dashboard()
  } catch {
    dashboard.value = {}
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const page = await observabilityApi.logs({ ...logQuery, pageNum: pageNum.value, pageSize: pageSize.value })
    operationLogs.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

function formatTimeParam(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function loadMetricCharts() {
  loading.value = true
  try {
    // 后端每 60s 按类型采集约 55 条指标；按时间范围动态放大 pageSize，超出上限时提示截断
    const DEFAULT_SIZE = 500
    const MAX_SIZE = 5000
    let size = DEFAULT_SIZE
    if (metricQuery.dateRange) {
      const start = new Date(metricQuery.dateRange[0]).getTime()
      const end = new Date(metricQuery.dateRange[1]).getTime()
      const minutes = Math.max(1, Math.ceil((end - start) / 60000))
      size = Math.min(MAX_SIZE, Math.max(DEFAULT_SIZE, minutes * 60 + 100))
    }
    const params: any = { metricType: metricQuery.metricType || undefined, pageNum: 1, pageSize: size }
    if (metricQuery.dateRange) {
      params.startTime = metricQuery.dateRange[0]
      params.endTime = metricQuery.dateRange[1]
    }
    const page = await observabilityApi.metrics(params)
    systemMetrics.value = page?.records || []
    metricsTotal.value = page?.total || 0
    renderAllCharts()
  } finally {
    loading.value = false
  }
}

async function loadTraces() {
  loading.value = true
  try {
    const page = await observabilityApi.traces({ ...traceQuery, pageNum: pageNum.value, pageSize: pageSize.value })
    callTraces.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadLlmTraces() {
  loading.value = true
  try {
    const params: any = { ...llmTraceQuery, pageNum: pageNum.value, pageSize: pageSize.value }
    if (llmTraceQuery.timeRange) {
      params.startTime = llmTraceQuery.timeRange[0]
      params.endTime = llmTraceQuery.timeRange[1]
    }
    delete params.timeRange
    const page = await observabilityApi.llmTraces(params)
    llmTraces.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  if (activeTab.value === 'logs') loadLogs()
  else if (activeTab.value === 'metrics') loadMetricCharts()
  else if (activeTab.value === 'llm-traces') loadLlmTraces()
  else loadTraces()
}

function onTabChange() {
  pageNum.value = 1
  total.value = 0
  if (activeTab.value === 'metrics') loadMetricCharts()
  else reload()
}

function refresh() {
  loadDashboard()
  reload()
}

async function handleCollect() {
  collecting.value = true
  try {
    const result = await observabilityApi.collectMetrics()
    ElMessage.success(`采集完成，新增 ${result?.length ?? 0} 条指标`)
    loadMetricCharts()
  } finally {
    collecting.value = false
  }
}

function filterByTrace(traceId: string) {
  traceQuery.traceId = traceId
  pageNum.value = 1
  loadTraces()
}

const logDialog = ref(false)
const currentLog = ref<any>(null)
function showLog(row: any) {
  currentLog.value = row
  logDialog.value = true
}

// LLM 调用详情抽屉
const llmDetailDrawer = ref(false)
const currentLlmTrace = ref<any>(null)

/** 从列表行直接展示（已有完整数据） */
function showLlmDetail(row: any) {
  currentLlmTrace.value = row
  llmDetailDrawer.value = true
}

/** 截断内容用于列表预览（取 JSON 摘要） */
function truncateContent(content: string | null | undefined): string {
  if (!content) return '-'
  let text = content
  try {
    // 尝试解析 JSON 列表，提取每条消息的 role + content 摘要
    const arr = JSON.parse(content)
    if (Array.isArray(arr)) {
      text = arr.map((m: any) => {
        const role = m.role || '?'
        const c = typeof m.content === 'string' ? m.content : (m.content ? JSON.stringify(m.content) : '')
        return `[${role}] ${c}`
      }).join('  ')
    }
  } catch { /* 不是 JSON 或不需解析 */ }
  return text.length > 120 ? text.substring(0, 120) + '...' : text
}

onMounted(() => {
  loadDashboard()
  loadLogs()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  for (const key of Object.keys(chartInstances)) {
    chartInstances[key]?.dispose()
  }
})

function handleResize() {
  for (const key of Object.keys(chartInstances)) {
    chartInstances[key]?.resize()
  }
}
</script>

<style lang="scss" scoped>
.stat-grid {
  display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; margin-bottom: 16px;
}
.stat-card { text-align: center; }
.stat-label { font-size: 13px; color: #909399; }
.stat-value { font-size: 26px; font-weight: 600; margin: 6px 0 2px; }
.stat-unit { font-size: 12px; color: #c0c4cc; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.toolbar-left { display: flex; gap: 12px; flex-wrap: wrap; }
.pager { margin-top: 16px; justify-content: flex-end; }
.chart-card {
  background: #fff; border: 1px solid #e8eaef; border-radius: 10px;
  padding: 0; overflow: hidden; min-height: 320px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04); transition: box-shadow .25s;
}
.chart-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.code-block {
  background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px;
  max-height: 220px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 12px; line-height: 1.7; margin: 0;
}
@media (max-width: 1440px) {
  .stat-grid { grid-template-columns: repeat(3, 1fr); }
}
.metrics-charts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.chart-box {
  width: 100%;
  height: 300px;
}
.empty-hint {
  padding: 40px 0;
}
.llm-detail-desc {
  margin-bottom: 4px;
}
.llm-content-block {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.8;
  font-family: 'Menlo', 'Consolas', monospace;
}
@media (max-width: 1200px) {
  .metrics-charts { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 10px; }
  .stat-value { font-size: 22px; }
  .chart-card { min-height: auto; }
  .chart-box { height: 240px; }
  .pager { justify-content: center; }
}
</style>