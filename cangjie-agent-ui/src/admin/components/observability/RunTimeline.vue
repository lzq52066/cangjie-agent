<template>
  <div class="run-timeline">
    <div class="tl-summary">
      模型循环 {{ roundGroups.length }} 轮 · {{ detail?.steps?.length || 0 }} 步 · {{ llmCallCount }} 次模型调用
    </div>

    <!-- 上下文装配：还原"模型这一轮到底收到了哪些片段、各占多少预算" -->
    <div v-if="contextSlots.length" class="tl-node tl-node--context">
      <div class="tl-head" @click="toggle('ctx')">
        <el-tag size="small" type="info">context</el-tag>
        <span class="tl-name">上下文装配</span>
        <span class="tl-meta">{{ contextSlots.length }} 个槽位 · 约 {{ contextTokens }} tokens</span>
        <span class="tl-caret">{{ caret('ctx') }}</span>
      </div>
      <div v-if="isOpen('ctx')" class="tl-body">
        <el-table :data="contextSlots" size="small" border>
          <el-table-column label="槽位" prop="slot" width="130" />
          <el-table-column label="来源" prop="source" min-width="120" show-overflow-tooltip />
          <el-table-column label="消息" prop="messages" width="66" align="center" />
          <el-table-column label="占用" prop="estTokens" width="76" align="center" />
          <el-table-column label="预算" prop="budgetTokens" width="76" align="center" />
          <el-table-column label="丢弃" width="76" align="center">
            <template #default="{ row }">
              <span :style="row.droppedTokens ? 'color:#f56c6c' : ''">{{ row.droppedTokens || 0 }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div v-for="(g, gi) in roundGroups" :key="gi" class="tl-round">
      <div class="tl-round-head">
        <span class="tl-round-title">{{ g.round == null ? '未归属轮次' : `第 ${g.round} 轮` }}</span>
        <span class="tl-meta">
          {{ g.nodes.length }} 步 · {{ g.totalTokens }} tokens · {{ fmtDuration(g.duration) }}
        </span>
      </div>

      <div v-for="n in g.nodes" :key="n.key" class="tl-node" :class="`tl-node--${n.type}`">
        <div class="tl-head" @click="toggle(n.key)">
          <el-tag size="small" :type="stepTagType(n.type)">{{ n.type }}</el-tag>
          <span class="tl-name">{{ n.name || '-' }}</span>
          <span v-if="n.inputTokens != null" class="tl-meta">{{ n.inputTokens }}/{{ n.outputTokens }} tok</span>
          <span class="tl-meta">{{ fmtDuration(n.duration) }}</span>
          <el-tag v-if="n.calls.length > 1" size="small" type="warning" effect="plain">
            {{ n.calls.length }} 次调用
          </el-tag>
          <el-tag v-if="n.truncated" size="small" type="info" effect="plain">增量已截断</el-tag>
          <el-tag size="small" :type="callStatusTagType(n.status)" effect="plain">
            {{ callStatusLabel(n.status) }}
          </el-tag>
          <span class="tl-caret">{{ caret(n.key) }}</span>
        </div>

        <div v-if="isOpen(n.key)" class="tl-body">
          <template v-if="n.input">
            <div class="tl-io-label">入参{{ n.type === 'llm' ? '（本轮增量）' : '' }}</div>
            <pre class="code-block">{{ prettyJson(n.input) }}</pre>
          </template>
          <template v-if="n.output">
            <div class="tl-io-label">产出</div>
            <pre class="code-block">{{ prettyJson(n.output) }}</pre>
          </template>
          <el-alert v-if="n.errorMessage" type="error" :closable="false" show-icon
                    :title="n.errorMessage" style="margin-top:8px" />

          <!-- 该步骤名下的真实模型调用：完整 prompt、厂商 requestId、重试与降级都在这层 -->
          <div v-if="n.calls.length" class="tl-calls">
            <div class="tl-io-label">模型调用（{{ n.calls.length }}）</div>
            <div v-for="(c, ci) in n.calls" :key="c.id" class="tl-call">
              <div class="tl-call-head" @click="toggle(c.id)">
                <el-tag size="small" :type="callStatusTagType(c.status)" effect="plain">{{ ci + 1 }}</el-tag>
                <span class="tl-name">{{ c.modelName || '-' }}</span>
                <span class="tl-meta">
                  {{ c.inputTokens ?? '-' }}/{{ c.outputTokens ?? '-' }} tok · {{ fmtDuration(c.duration) }}
                  · {{ c.finishReason || '-' }}
                </span>
                <span class="tl-meta">req {{ c.requestId || '-' }}</span>
                <span class="tl-caret">{{ caret(c.id) }}</span>
              </div>
              <div v-if="isOpen(c.id)" class="tl-call-body">
                <LlmCallBody :trace-id="c.id" :cache="cache" />
              </div>
            </div>
          </div>
          <div v-else-if="n.type === 'llm'" class="tl-hint">
            无模型调用明细：该 run 早于 run 关联上线，或步骤留痕未开启
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!roundGroups.length && !contextSlots.length"
              description="无步骤明细（agent-run.record-steps 已关闭）" :image-size="60" />

    <template v-if="detail?.approvals?.length">
      <el-divider content-position="left">审批单（{{ detail.approvals.length }}）</el-divider>
      <el-table :data="detail.approvals" size="small" border stripe>
        <el-table-column label="工具" prop="toolName" min-width="130" show-overflow-tooltip />
        <el-table-column label="风险" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="riskTagType(row.riskLevel)">{{ row.riskLevel || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="approvalStatusTagType(row.status)">
              {{ approvalStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批人" prop="decidedBy" width="120" show-overflow-tooltip />
        <el-table-column label="审批时间" prop="decideTime" width="160" />
        <el-table-column label="过期时间" prop="expireTime" width="160" />
      </el-table>
    </template>

    <template v-if="detail?.run?.finalText">
      <el-divider content-position="left">最终输出</el-divider>
      <div class="final-text">{{ detail.run.finalText }}</div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 单个 Agent run 的执行时间线：步骤按轮次分组，llm 步骤按时间窗挂上真实模型调用。
 * <p>
 * 一条链路里可能有多个 run（主 Agent + 子 Agent），折叠状态用 runId 作前缀隔离，
 * 模型调用正文缓存由调用方持有以便切换链路时整体清空。
 */
import { computed, ref } from 'vue'
import LlmCallBody from '@admin/components/observability/LlmCallBody.vue'
import { prettyJson } from '@admin/utils/format'
import {
  approvalStatusLabel, approvalStatusTagType, callStatusLabel, callStatusTagType,
  fmtDuration, riskTagType, stepTagType, toMs
} from '@admin/utils/observability'

const props = withDefaults(defineProps<{
  detail?: any
  runId?: string
  cache: Record<string, any>
}>(), { runId: 'run' })

const openKeys = ref<string[]>([])

function keyOf(key: string) {
  return `${props.runId}:${key}`
}
function isOpen(key: string) {
  return openKeys.value.includes(keyOf(key))
}
function toggle(key: string) {
  const k = keyOf(key)
  openKeys.value = openKeys.value.includes(k) ? openKeys.value.filter(x => x !== k) : [...openKeys.value, k]
}
function caret(key: string) {
  return isOpen(key) ? '−' : '+'
}

const contextSlots = computed<any[]>(() => {
  const raw = props.detail?.run?.contextUsage
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
})
const contextTokens = computed(() =>
  contextSlots.value.reduce((sum, s) => sum + (s.estTokens || 0), 0))

const llmCallCount = computed(() => props.detail?.llmTraces?.length || 0)

/**
 * 步骤与模型调用按时间窗对齐：一轮 exchange 只写一个 llm 步骤，
 * 但重试与供应商降级会在该窗口内产生多条调用，所以是一对多，不做压缩。
 */
const roundGroups = computed<any[]>(() => {
  const steps: any[] = props.detail?.steps || []
  const traces: any[] = props.detail?.llmTraces || []
  const claimed = new Set<string>()
  const groups: any[] = []
  let current: any = null

  for (const s of steps) {
    if (!current || current.round !== s.round) {
      current = { round: s.round ?? 0, nodes: [] }
      groups.push(current)
    }
    const node: any = { ...s, key: `s-${s.stepNo ?? s.id}`, calls: [] }
    if (s.type === 'llm') {
      // 留痕时间各自取整，两端各放宽 1s；已认领的调用不再被后面的步骤抢走
      const from = toMs(s.startTime) - 1000
      const to = toMs(s.endTime) + 1000
      node.calls = traces.filter((t: any) => {
        if (claimed.has(t.id)) return false
        const at = toMs(t.startTime)
        if (at < from || at > to) return false
        claimed.add(t.id)
        return true
      })
    }
    current.nodes.push(node)
  }

  const leftovers = traces.filter((t: any) => !claimed.has(t.id))
  if (leftovers.length) {
    groups.push({
      round: null,
      nodes: leftovers.map((t: any) => ({
        key: `t-${t.id}`,
        type: 'llm',
        name: t.modelName,
        status: t.status,
        inputTokens: t.inputTokens,
        outputTokens: t.outputTokens,
        duration: t.duration,
        input: '',
        output: '',
        errorMessage: t.errorMessage,
        truncated: 0,
        calls: [t]
      }))
    })
  }

  return groups.map(g => ({
    ...g,
    totalTokens: g.nodes.reduce(
      (n: number, x: any) => n + (x.inputTokens || 0) + (x.outputTokens || 0), 0),
    duration: g.nodes.reduce((n: number, x: any) => n + (x.duration || 0), 0)
  }))
})
</script>

<style lang="scss" scoped>
.tl-summary { font-size: 12px; color: #909399; margin-bottom: 10px; }
.tl-io-label { font-size: 12px; color: #909399; margin: 6px 0 4px; }
.code-block {
  background: #f9fafc; border: 1px solid #ebeef5; border-radius: 8px; padding: 10px;
  max-height: 240px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-size: 12px; line-height: 1.7; margin: 0;
}
.final-text {
  background: #fafafa; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px;
  white-space: pre-wrap; word-break: break-all; font-size: 13px; line-height: 1.8;
}

.tl-round { margin-bottom: 14px; }
.tl-round-head {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 0; border-bottom: 1px dashed #ebeef5;
}
.tl-round-title { font-size: 13px; font-weight: 600; color: #303133; }

.tl-node {
  display: flex; flex-direction: column; margin-top: 8px;
  background: #fff; border: 1px solid #ebeef5; border-radius: 8px;
}
.tl-node--llm { border-left: 3px solid #409eff; }
.tl-node--tool { border-left: 3px solid #e6a23c; }
.tl-node--error { border-left: 3px solid #f56c6c; }
.tl-node--hook, .tl-node--context { border-left: 3px solid #909399; }

.tl-head, .tl-call-head {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; cursor: pointer; font-size: 12px;
}
.tl-head:hover, .tl-call-head:hover { background: #f9fafc; }
.tl-name { font-size: 12px; font-weight: 600; color: #303133; }
.tl-meta { font-size: 12px; color: #909399; }
.tl-caret { margin-left: auto; font-size: 14px; line-height: 1; color: #c0c4cc; }
.tl-body { padding: 0 12px 12px; border-top: 1px solid #f2f3f5; }

.tl-calls { margin-top: 10px; }
.tl-call { margin-bottom: 6px; border: 1px solid #ebeef5; border-radius: 6px; }
.tl-call-head { padding: 6px 8px; }
.tl-call-body { padding: 0 10px 10px; border-top: 1px solid #f2f3f5; }
.tl-hint { margin-top: 8px; font-size: 12px; color: #a8abb2; }
</style>
