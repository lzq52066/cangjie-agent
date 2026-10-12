<template>
  <div class="eval-view">
    <div class="page-toolbar">
      <el-button @click="openRunHistory">
        <el-icon><Clock /></el-icon> 运行历史
      </el-button>
    </div>
    <el-row :gutter="16">
      <!-- 左侧：数据集列表 -->
      <el-col :xs="24" :sm="24" :md="8" :lg="8">
        <el-card class="dataset-card">
          <template #header>
            <div class="card-header">
              <span>评估数据集</span>
              <el-button type="primary" size="small" @click="openDatasetDialog()">
                <el-icon><Plus /></el-icon> 新建
              </el-button>
            </div>
          </template>
          <div v-loading="loading">
            <div v-for="ds in datasets" :key="ds.id" class="dataset-item"
                 :class="{ active: current?.id === ds.id }" @click="selectDataset(ds)">
              <div class="ds-main">
                <div class="ds-name">{{ ds.name }}</div>
                <div class="ds-desc">{{ ds.description || '暂无描述' }}</div>
              </div>
              <div class="ds-meta">
                <el-tag size="small" type="info" effect="plain">{{ ds.caseCount ?? 0 }} 用例</el-tag>
                <el-dropdown trigger="click" @command="(cmd) => onDatasetCmd(cmd, ds)">
                  <el-button link size="small" @click.stop><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
            <el-empty v-if="!datasets.length" description="暂无数据集，点击右上角新建" :image-size="80" />
            <DataPager v-model:current-page="pageNum" v-model:page-size="pageSize"
                       :total="total" @change="loadDatasets" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：用例管理 + 运行 -->
      <el-col :xs="24" :sm="24" :md="16" :lg="16" class="case-col">
        <el-card v-if="current">
          <template #header>
            <div class="card-header">
              <span>{{ current.name }} — 测试用例</span>
              <div>
                <el-button size="small" @click="openImportDialog">从对话回流</el-button>
                <el-button size="small" @click="openRunDialog">运行评估</el-button>
                <el-button type="primary" size="small" @click="openCaseDialog()">
                  <el-icon><Plus /></el-icon> 添加用例
                </el-button>
              </div>
            </div>
          </template>
          <el-table :data="cases" v-loading="caseLoading" stripe size="small">
            <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="expectedAnswer" label="期望回答" min-width="180" show-overflow-tooltip />
            <el-table-column label="来源" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.source && row.source !== 'manual'" size="small" type="info" effect="plain">
                  {{ sourceLabel(row.source) }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="openCaseDialog(row)">编辑</el-button>
                <el-popconfirm title="确定删除该用例？" @confirm="deleteCase(row.id)">
                  <template #reference><el-button link size="small" type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <DataPager v-model:current-page="casePageNum" v-model:page-size="casePageSize"
                     :total="caseTotal" @change="loadCases" />
        </el-card>
        <el-card v-else>
          <el-empty description="请选择左侧数据集，或先新建一个数据集" :image-size="100" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据集编辑 -->
    <el-dialog v-model="dsDialog" :title="editingDs ? '编辑数据集' : '新建数据集'" width="520px" destroy-on-close>
      <el-form :model="dsForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="dsForm.name" placeholder="如：合同问答测试集" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dsForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-select v-model="dsForm.knowledgeBaseId" filterable clearable style="width:100%"
                     placeholder="用于召回率评估">
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDataset">保存</el-button>
      </template>
    </el-dialog>

    <!-- 用例编辑 -->
    <el-dialog v-model="caseDialog" :title="editingCase ? '编辑用例' : '添加用例'" width="560px" destroy-on-close>
      <el-form :model="caseForm" label-width="90px">
        <el-form-item label="问题" required>
          <el-input v-model="caseForm.question" type="textarea" :rows="2" placeholder="测试问题" />
        </el-form-item>
        <el-form-item label="期望回答">
          <el-input v-model="caseForm.expectedAnswer" type="textarea" :rows="2"
                    placeholder="用于正确性评估（LLM judge）" />
        </el-form-item>
        <el-form-item label="参考文档ID">
          <el-input v-model="caseForm.referenceDocsText" type="textarea" :rows="3"
                    placeholder="每行一个文档 ID，用于召回率计算" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCase">保存</el-button>
      </template>
    </el-dialog>

    <!-- 线上对话回流：把带反馈 / 人工修正的那批问答拉回回归集 -->
    <el-dialog v-model="importDialog" title="从线上对话回流用例" width="520px" destroy-on-close>
      <el-form :model="importForm" label-width="100px">
        <el-form-item label="应用">
          <el-select v-model="importForm.applicationId" filterable clearable style="width:100%"
                     placeholder="不选则拉全部应用的对话">
            <el-option v-for="app in applications" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户反馈">
          <el-select v-model="importForm.feedback" clearable style="width:100%" placeholder="不限反馈">
            <el-option label="只看踩的（回归集主力）" value="dislike" />
            <el-option label="只看赞的" value="like" />
            <el-option label="只看未反馈的" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="人工标注">
          <el-select v-model="importForm.annotatedOnly" style="width:100%">
            <el-option label="不限" value="" />
            <el-option label="只要有人工修正的（带回期望答案）" value="true" />
            <el-option label="只要没人工修正的" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="最多扫">
          <el-input-number v-model="importForm.limit" :min="1" :max="200" />
          <span class="form-hint">条（服务端硬上限 200：一次灌几千条既烧 token 又让回归集失焦）</span>
        </el-form-item>
      </el-form>
      <div class="import-hint">
        回流会把用户问题与其后的 AI 回答配成一对，人工修正当期望答案、当时的检索片段当参考依据；
        同一条回复重复回流只算一票。
      </div>
      <template #footer>
        <el-button @click="importDialog = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始回流</el-button>
      </template>
    </el-dialog>

    <!-- 运行配置 -->
    <el-dialog v-model="runDialog" title="运行评估" width="560px" destroy-on-close>
      <el-form :model="runForm" label-width="110px">
        <el-form-item label="回答模型">
          <el-select v-model="runForm.modelId" filterable clearable style="width:100%"
                     placeholder="不选则只测检索召回率">
            <el-option v-for="m in models" :key="m.id" :label="`${m.name}（${m.modelName || ''}）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="判定模型">
          <el-select v-model="runForm.judgeModelId" filterable clearable style="width:100%"
                     placeholder="不选则与回答模型同一个">
            <el-option v-for="m in models" :key="m.id" :label="`${m.name}（${m.modelName || ''}）`" :value="m.id" />
          </el-select>
          <span class="form-hint">judge 不要求多强的生成能力，单独指一个便宜的能把成本降下来</span>
        </el-form-item>
        <el-form-item label="知识库">
          <el-select v-model="runForm.knowledgeBaseIds" multiple filterable clearable style="width:100%"
                     placeholder="不选则用数据集关联的知识库">
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="召回条数 TopK">
          <el-input-number v-model="runForm.topK" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="检索模式">
          <el-select v-model="runForm.searchMode" clearable style="width:100%"
                     placeholder="不选则按知识库自身配置">
            <el-option label="向量检索" value="vector" />
            <el-option label="全文检索" value="full_text" />
            <el-option label="混合检索" value="hybrid" />
            <el-option label="两阶段（先召后重排）" value="two_stage" />
          </el-select>
        </el-form-item>
        <el-form-item label="相似度阈值">
          <el-input-number v-model="runForm.similarityThreshold" :min="0" :max="1" :step="0.05"
                           :precision="2" placeholder="不限" />
          <span class="form-hint">留空则不卡阈值</span>
        </el-form-item>
        <el-form-item label="重排模型">
          <el-select v-model="runForm.rerankType" clearable style="width:100%"
                     placeholder="不选则不重排">
            <el-option label="BGE" value="bge" />
            <el-option label="无" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="评测指标">
          <el-select v-model="runForm.metrics" multiple clearable style="width:100%"
                     placeholder="全部开启（不选即为全跑）">
            <el-option v-for="mt in METRIC_OPTIONS" :key="mt.value" :label="mt.label" :value="mt.value" />
          </el-select>
          <span class="form-hint">召回率与正确性是内置列，这里选的是 RAGAS 口径的四个补充指标</span>
        </el-form-item>
        <el-form-item label="用例并行度">
          <el-input-number v-model="runForm.parallelism" :min="1" :max="8" />
          <span class="form-hint">上限 8，再多就不是「快一点」而是把评测跑成压测</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialog = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="startRun">开始评估</el-button>
      </template>
    </el-dialog>

    <!-- 运行报告 -->
    <el-drawer v-model="reportDrawer" :title="'评估报告 - ' + (current?.name || '')" size="780px" destroy-on-close>
      <div v-if="report" class="report-body">
        <el-alert v-if="report.status === 'failed'" type="error" :closable="false"
                  :title="report.errorMessage || '评估失败'" class="report-error" />
        <el-alert v-if="writeFailureTip" type="warning" :closable="false" show-icon
                  :title="writeFailureTip" class="report-error" />
        <div v-if="isLive(report)" class="report-actions">
          <el-button size="small" type="warning" plain :loading="cancelling" @click="cancelRun(report)">
            停止评估
          </el-button>
          <span class="report-actions-hint">停止后当前用例跑完就结束，已完成的部分仍会出报告</span>
        </div>
        <div class="report-actions">
          <el-button size="small" :disabled="isLive(report)" @click="openCompare(report)">与上一次对比</el-button>
          <span class="report-actions-hint">只看一份报告答不了「这次改动到底让水位涨了还是跌了」</span>
        </div>
        <el-progress :percentage="report.progress ?? 0"
                     :status="report.status === 'failed' ? 'exception' : (report.progress >= 100 ? 'success' : '')"
                     class="report-progress" />
        <el-row :gutter="12" class="summary-row">
          <el-col :xs="12" :sm="6"><div class="metric-card"><div class="metric-value">{{ fmt(summary.avgRecall) }}</div><div class="metric-label">平均召回率{{ sampleText(summary.recallCases) }}</div></div></el-col>
          <el-col :xs="12" :sm="6"><div class="metric-card"><div class="metric-value">{{ fmt(summary.avgCorrectness) }}</div><div class="metric-label">平均正确性{{ sampleText(summary.correctnessCases) }}</div></div></el-col>
          <el-col :xs="12" :sm="6"><div class="metric-card"><div class="metric-value">{{ summary.avgLatency != null ? Math.round(summary.avgLatency) + 'ms' : '-' }}</div><div class="metric-label">平均耗时</div></div></el-col>
          <el-col :xs="12" :sm="6"><div class="metric-card"><div class="metric-value">{{ summary.totalTokens ?? 0 }}</div><div class="metric-label">总 Token</div></div></el-col>
        </el-row>
        <!-- SPI 指标按名字动态渲染：加一个指标不需要改报告代码，没出分的一个也不进来 -->
        <el-row v-if="extraMetrics.length" :gutter="12" class="summary-row">
          <el-col v-for="mt in extraMetrics" :key="mt.name" :xs="12" :sm="6">
            <div class="metric-card">
              <div class="metric-value">{{ fmt(mt.avg) }}</div>
              <div class="metric-label">{{ metricLabel(mt.name) }}{{ sampleText(mt.cases) }}</div>
            </div>
          </el-col>
        </el-row>
        <el-table :data="resultList" size="small" stripe max-height="480"
                  :row-key="rowKey" @expand-change="onExpandResult">
          <el-table-column type="expand">
            <template #default="{ row }">
              <EvalResultDetail :row="row" :record="details[rowKey(row)]" :loading="!!detailLoading[rowKey(row)]" />
            </template>
          </el-table-column>
          <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
          <el-table-column label="召回" width="80" align="center">
            <template #default="{ row }">{{ fmt(row.recall) }}</template>
          </el-table-column>
          <el-table-column label="正确性" width="90" align="center">
            <template #default="{ row }">{{ row.correctness != null ? fmt(row.correctness) : '-' }}</template>
          </el-table-column>
          <!-- SPI 分数按行上真实出现过的指标开列：本次没跑的指标不开一个空列占位 -->
          <el-table-column v-for="name in extraColumns" :key="name" :label="metricLabel(name)"
                           width="110" align="center">
            <template #default="{ row }">
              <span v-if="rowScores(row)[name] != null">{{ fmt(rowScores(row)[name]) }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="latency" label="耗时(ms)" width="90" align="center" />
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status && row.status !== 'success'" size="small"
                      :type="row.status === 'fail' ? 'danger' : 'warning'">
                {{ row.status === 'fail' ? '失败' : '部分' }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-else class="report-loading" v-loading="true"></div>
    </el-drawer>

    <!-- 运行历史（跨数据集） -->
    <el-drawer v-model="historyDrawer" title="评估运行历史" size="860px" destroy-on-close>
      <div class="history-filters">
        <el-select v-model="historyQuery.datasetId" placeholder="全部数据集" clearable filterable
                   style="width:200px" @change="reloadHistory">
          <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
        </el-select>
        <el-select v-model="historyQuery.status" placeholder="全部状态" clearable style="width:140px"
                   @change="reloadHistory">
          <el-option label="排队中" value="pending" />
          <el-option label="执行中" value="running" />
          <el-option label="已完成" value="completed" />
          <el-option label="失败" value="failed" />
          <el-option label="已停止" value="cancelled" />
        </el-select>
      </div>
      <el-table :data="history" v-loading="historyLoading" stripe size="small">
        <el-table-column label="开始时间" prop="startTime" width="160" />
        <el-table-column label="数据集" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ datasetName(row.datasetId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="runStatusTag(row.status)">{{ runStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="140" align="center">
          <template #default="{ row }">
            <el-progress :percentage="row.progress ?? 0" :stroke-width="8"
                         :status="row.status === 'failed' ? 'exception' : (row.progress >= 100 ? 'success' : '')" />
          </template>
        </el-table-column>
        <el-table-column label="平均召回" width="90" align="center">
          <template #default="{ row }">{{ fmt(parseSummary(row.summary).avgRecall) }}</template>
        </el-table-column>
        <el-table-column label="平均正确性" width="100" align="center">
          <template #default="{ row }">{{ fmt(parseSummary(row.summary).avgCorrectness) }}</template>
        </el-table-column>
        <el-table-column label="Token" prop="summary" width="90" align="center">
          <template #default="{ row }">{{ parseSummary(row.summary).totalTokens ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="viewRunReport(row)">报告</el-button>
            <el-button size="small" link type="primary" :disabled="isLive(row)" @click="openCompare(row)">对比</el-button>
            <el-button v-if="isLive(row)" size="small" link type="warning" @click="cancelRun(row)">停止</el-button>
            <el-popconfirm title="确定删除该运行记录？" @confirm="deleteRun(row)">
              <template #reference>
                <el-button size="small" link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <DataPager v-model:current-page="historyPageNum" v-model:page-size="historyPageSize"
                 :total="historyTotal" @change="loadHistory" />
    </el-drawer>

    <!-- 运行对比：汇总差值 + 成本差值 + 逐用例差值 -->
    <el-drawer v-model="compareDrawer" title="运行对比" size="920px" destroy-on-close>
      <div class="compare-bar">
        <div class="compare-run">
          当前运行 {{ shortRunId(compareTarget?.id) }}
          <span class="compare-run-meta">{{ runStatusLabel(compareTarget?.status) }} · {{ compareTarget?.startTime || '-' }}</span>
        </div>
        <el-select v-model="compareBaseline" placeholder="基准：自动取上一次已结束运行" clearable filterable
                   style="width:300px" @change="loadCompare">
          <el-option v-for="r in baselineOptions" :key="r.id" :label="baselineLabel(r)" :value="r.id" />
        </el-select>
      </div>
      <div class="compare-hint">
        基准不选就是同数据集里紧邻它之前的那一次已结束运行；差值统一是「本次 - 基准」，
        正数代表本次更好（成本项反过来看：涨了是变差）。
      </div>

      <div v-loading="comparing" class="compare-body">
        <template v-if="compare">
          <el-alert v-if="!compare.sameDataset" type="warning" :closable="false" show-icon
                    title="两次运行不是同一个数据集：逐用例对比没有意义，均值差值里混的是题目差异，不能直接当回归读"
                    class="compare-alert" />
          <el-alert v-if="compareRegress.length" type="error" :closable="false" show-icon
                    :title="`回归告警：${compareRegress.length} 项指标均值比基准跌过 ${regressThresholdText}，这次改动建议先别上`"
                    class="compare-alert" />
          <div v-if="compareRegress.length" class="regress-list">
            <div v-for="a in compareRegress" :key="a.name" class="regress-item">
              <span class="regress-name">{{ metricLabel(a.name) }}</span>
              <span>{{ fmt(a.from) }} → {{ fmt(a.to) }}</span>
              <b class="down">{{ fmtDelta(a.delta) }}</b>
              <span class="cell-sm">本次样本 n={{ a.toCases ?? '-' }}</span>
            </div>
            <div class="compare-hint">
              阈值取自后端 cangjie.eval.regress.threshold（配 0 即关掉门禁）；它只标出问题、不把这次运行判成失败 ——
              分数是真实读数，要不要放行是人的决定。样本过少的均值不进告警：那多数是噪声，不是一次回归。
            </div>
          </div>

          <div class="compare-counts">
            <span>回归 <b class="down">{{ compare.regressed }}</b> 条</span>
            <span>改善 <b class="up">{{ compare.improved }}</b> 条</span>
            <span>只有基准侧有 {{ compare.onlyInFrom }} 条</span>
            <span>只有本次有 {{ compare.onlyInTo }} 条</span>
          </div>

          <div class="compare-block">
            <div class="compare-title">指标差值</div>
            <el-table :data="compareMetricRows" size="small" border>
              <el-table-column label="指标" prop="label" min-width="140" />
              <el-table-column label="基准" width="100" align="center">
                <template #default="{ row }">{{ fmt(row.from) }}</template>
              </el-table-column>
              <el-table-column label="本次" width="100" align="center">
                <template #default="{ row }">{{ fmt(row.to) }}</template>
              </el-table-column>
              <el-table-column label="差值" width="110" align="center">
                <template #default="{ row }">
                  <span :style="{ color: deltaColor(row.delta) }">{{ fmtDelta(row.delta) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="样本（基准/本次）" width="150" align="center">
                <template #default="{ row }">{{ row.fromCases ?? '-' }} / {{ row.toCases ?? '-' }}</template>
              </el-table-column>
            </el-table>
            <div class="compare-hint">
              任一侧没出分则差值为空：把「没评测」当成 0 参与差值，只会算出一个看着像崩盘的假回归
            </div>
          </div>

          <div class="compare-block">
            <div class="compare-title">开销差值</div>
            <el-table :data="compareCostRows" size="small" border>
              <el-table-column label="项" prop="label" min-width="140" />
              <el-table-column label="基准" width="120" align="center">
                <template #default="{ row }">{{ row.fromText }}</template>
              </el-table-column>
              <el-table-column label="本次" width="120" align="center">
                <template #default="{ row }">{{ row.toText }}</template>
              </el-table-column>
              <el-table-column label="差值" min-width="120" align="center">
                <template #default="{ row }">
                  <span :style="{ color: row.deltaColor }">{{ row.deltaText }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="compare-block">
            <div class="compare-title">逐用例差值（跌得最狠的在前，最多列 {{ MAX_DIFF_CASES_UI }} 条）</div>
            <el-table :data="compare.cases" size="small" stripe max-height="420"
                      :row-key="(row: any) => row.caseId">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <el-table :data="caseMetricRows(row)" size="small" border class="case-delta-table">
                    <el-table-column label="指标" prop="label" min-width="140" />
                    <el-table-column label="基准" width="100" align="center">
                      <template #default="{ row: m }">{{ fmt(m.from) }}</template>
                    </el-table-column>
                    <el-table-column label="本次" width="100" align="center">
                      <template #default="{ row: m }">{{ fmt(m.to) }}</template>
                    </el-table-column>
                    <el-table-column label="差值" width="110" align="center">
                      <template #default="{ row: m }">
                        <span :style="{ color: deltaColor(m.delta) }">{{ fmtDelta(m.delta) }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                  <div v-if="!caseMetricRows(row).length" class="compare-hint">这条用例两侧都没有逐项分数</div>
                </template>
              </el-table-column>
              <el-table-column label="问题" prop="question" min-width="240" show-overflow-tooltip />
              <el-table-column label="基准综合" width="100" align="center">
                <template #default="{ row }">{{ fmt(row.compositeFrom) }}</template>
              </el-table-column>
              <el-table-column label="本次综合" width="100" align="center">
                <template #default="{ row }">{{ fmt(row.compositeTo) }}</template>
              </el-table-column>
              <el-table-column label="差值" width="100" align="center">
                <template #default="{ row }">
                  <span :style="{ color: deltaColor(row.delta) }">{{ fmtDelta(row.delta) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态（基准→本次）" width="160" align="center">
                <template #default="{ row }">
                  <span class="cell-sm">{{ resultStatusLabel(row.fromStatus) }} → {{ resultStatusLabel(row.toStatus) }}</span>
                </template>
              </el-table-column>
            </el-table>
            <div class="compare-hint">
              综合分是该条用例所有出了分指标的均值，它会把「是哪一项跌的」掩盖掉，所以展开看逐项
            </div>
          </div>
        </template>
        <el-empty v-else-if="!comparing" description="没有可用的对比结果" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, MoreFilled, Clock } from '@element-plus/icons-vue'
import DataPager from '@admin/components/DataPager.vue'
import EvalResultDetail from '@admin/components/EvalResultDetail.vue'
import { evalApi } from '@admin/api/eval-api'
import type { EvalRunCompare, MetricDelta } from '@admin/api/eval-api'
import { modelApi } from '@admin/api/model-api'
import { knowledgeApi } from '@admin/api/knowledge-api'
import { applicationApi } from '@admin/api/application-api'
import { deltaColor, fmtDelta, metricLabel } from '@admin/utils/metrics'

const loading = ref(false)
const datasets = ref<any[]>([])
const current = ref<any>(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const cases = ref<any[]>([])
const caseLoading = ref(false)
const casePageNum = ref(1)
const casePageSize = ref(10)
const caseTotal = ref(0)

const knowledgeBases = ref<any[]>([])
const models = ref<any[]>([])
const applications = ref<any[]>([])

/** SPI 指标名与后端 EvalMetric#name() 对齐，选与不选只影响跑哪几个 */
const METRIC_OPTIONS = [
  { value: 'faithfulness', label: '忠实度 faithfulness（回答有没有超出检索上下文）' },
  { value: 'answerRelevancy', label: '答案相关性 answerRelevancy（答的是不是这个问题）' },
  { value: 'contextPrecision', label: '上下文精度 contextPrecision（召回片段有没有噪声）' },
  { value: 'contextRecall', label: '上下文召回 contextRecall（参考片段有没有漏）' }
]

function sourceLabel(s?: string) {
  return ({ trace: '对话回流', synthetic: '合成' } as Record<string, string>)[s || ''] || s || '-'
}

// ============ 数据集 ============
const dsDialog = ref(false)
const editingDs = ref(false)
const dsForm = reactive({ id: '', name: '', description: '', knowledgeBaseId: '' })

async function loadDatasets() {
  loading.value = true
  try {
    const page = await evalApi.listDatasets({ pageNum: pageNum.value, pageSize: pageSize.value })
    datasets.value = page?.list || []
    total.value = page?.total || 0
    const matched = datasets.value.find(d => d.id === current.value?.id)
    if (matched) {
      current.value = matched
    }
  } finally {
    loading.value = false
  }
}

function selectDataset(ds: any) {
  current.value = ds
  casePageNum.value = 1
  loadCases()
}

async function loadCases() {
  if (!current.value?.id) return
  caseLoading.value = true
  try {
    const page = await evalApi.listCases(current.value.id, {
      pageNum: casePageNum.value, pageSize: casePageSize.value
    })
    cases.value = page?.list || []
    caseTotal.value = page?.total || 0
  } finally {
    caseLoading.value = false
  }
}

function openDatasetDialog(ds?: any) {
  editingDs.value = !!ds
  Object.assign(dsForm, {
    id: ds?.id || '',
    name: ds?.name || '',
    description: ds?.description || '',
    knowledgeBaseId: ds?.knowledgeBaseId || ''
  })
  dsDialog.value = true
}

function onDatasetCmd(cmd: string, ds: any) {
  if (cmd === 'edit') openDatasetDialog(ds)
  if (cmd === 'delete') deleteDataset(ds)
}

async function saveDataset() {
  if (!dsForm.name.trim()) {
    ElMessage.error('请输入数据集名称')
    return
  }
  const payload = {
    name: dsForm.name.trim(),
    description: dsForm.description,
    knowledgeBaseId: dsForm.knowledgeBaseId || null
  }
  if (editingDs.value) {
    await evalApi.updateDataset(dsForm.id, payload)
    ElMessage.success('更新成功')
  } else {
    await evalApi.createDataset(payload)
    ElMessage.success('创建成功')
  }
  dsDialog.value = false
  if (!editingDs.value) {
    pageNum.value = 1 // 列表按创建时间倒序，新建后回到首页以确保可见
  }
  loadDatasets()
}

async function deleteDataset(ds: any) {
  await ElMessage.confirm(`确定删除数据集「${ds.name}」及其用例吗？`, '提示', { type: 'warning' })
  await evalApi.deleteDataset(ds.id)
  if (current.value?.id === ds.id) {
    current.value = null
    cases.value = []
    caseTotal.value = 0
  }
  ElMessage.success('删除成功')
  loadDatasets()
}

// ============ 用例 ============
const caseDialog = ref(false)
const editingCase = ref(false)
const caseForm = reactive({ id: '', question: '', expectedAnswer: '', referenceDocsText: '' })

function openCaseDialog(c?: any) {
  editingCase.value = !!c
  Object.assign(caseForm, {
    id: c?.id || '',
    question: c?.question || '',
    expectedAnswer: c?.expectedAnswer || '',
    referenceDocsText: toTextLines(c?.referenceDocs)
  })
  caseDialog.value = true
}

function toTextLines(json?: string): string {
  if (!json) return ''
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.join('\n') : ''
  } catch {
    return ''
  }
}

function toJsonList(text: string): string {
  const ids = text.split('\n').map(s => s.trim()).filter(Boolean)
  return ids.length ? JSON.stringify(ids) : '[]'
}

async function saveCase() {
  if (!current.value) return
  if (!caseForm.question.trim()) {
    ElMessage.error('请输入问题')
    return
  }
  const payload = {
    question: caseForm.question.trim(),
    expectedAnswer: caseForm.expectedAnswer,
    referenceDocs: toJsonList(caseForm.referenceDocsText)
  }
  if (editingCase.value) {
    await evalApi.updateCase(caseForm.id, payload)
    ElMessage.success('更新成功')
  } else {
    await evalApi.createCase(current.value.id, payload)
    ElMessage.success('添加成功')
  }
  caseDialog.value = false
  loadCases()
  loadDatasets()
}

async function deleteCase(id: string) {
  await evalApi.deleteCase(id)
  ElMessage.success('删除成功')
  if (current.value) loadCases()
  loadDatasets()
}

// ============ 线上对话回流 ============
const importDialog = ref(false)
const importing = ref(false)
const importForm = reactive({ applicationId: '', feedback: '', annotatedOnly: '', limit: 50 })

function openImportDialog() {
  if (!current.value) {
    ElMessage.warning('请先选择数据集')
    return
  }
  Object.assign(importForm, { applicationId: '', feedback: '', annotatedOnly: '', limit: 50 })
  importDialog.value = true
}

async function submitImport() {
  if (!current.value) return
  importing.value = true
  try {
    const result = await evalApi.importCasesFromConversations(current.value.id, {
      applicationId: importForm.applicationId || null,
      feedback: importForm.feedback || null,
      // 空串是「不限」，不能当 false 递上去 —— 那会把「未标注的也要」读成只找没标注的
      annotatedOnly: importForm.annotatedOnly === '' ? null : importForm.annotatedOnly === 'true',
      limit: importForm.limit
    })
    importDialog.value = false
    ElMessage.success(`扫了 ${result.scanned ?? 0} 条，新增 ${result.imported ?? 0} 条` +
      `，已回流过 ${result.duplicated ?? 0} 条，配不上问题 ${result.unusable ?? 0} 条`)
    loadCases()
    loadDatasets()
  } finally {
    importing.value = false
  }
}

// ============ 运行评估 ============
const runDialog = ref(false)
const running = ref(false)
const runForm = reactive({
  modelId: '', judgeModelId: '', knowledgeBaseIds: [] as string[], topK: 5,
  searchMode: '', similarityThreshold: null as number | null,
  rerankType: '', metrics: [] as string[], parallelism: 1
})

const reportDrawer = ref(false)
const report = ref<any>(null)
const summary = reactive<Record<string, any>>({})
const resultList = ref<any[]>([])
/** 展开行拉来的正文，按行主键缓存；收起再展开不重复请求，切换 run 时清空 */
const details = reactive<Record<string, any>>({})
const detailLoading = reactive<Record<string, boolean>>({})
const cancelling = ref(false)
let pollTimer: any = null
let resultsAt = 0
/** 明细拉取节流间隔：跑动中也能看到已完成的 case，又不必跟着 1.5s 的进度轮询重传全文 */
const RESULTS_INTERVAL_MS = 5000

function openRunDialog() {
  if (!caseTotal.value) {
    ElMessage.warning('该数据集暂无用例，请先添加用例')
    return
  }
  Object.assign(runForm, {
    modelId: '', judgeModelId: '', knowledgeBaseIds: [], topK: 5,
    searchMode: '', similarityThreshold: null, rerankType: '', metrics: [], parallelism: 1
  })
  runDialog.value = true
}

async function startRun() {
  if (!current.value) return
  running.value = true
  try {
    // 检索口径不传则一律留 null，由后端按知识库配置解析：评测与生产必须走同一条检索路径
    const config: Record<string, any> = {
      topK: runForm.topK,
      parallelism: runForm.parallelism
    }
    if (runForm.modelId) config.modelId = runForm.modelId
    if (runForm.judgeModelId) config.judgeModelId = runForm.judgeModelId
    if (runForm.knowledgeBaseIds.length) config.knowledgeBaseIds = runForm.knowledgeBaseIds
    if (runForm.searchMode) config.searchMode = runForm.searchMode
    if (runForm.similarityThreshold != null) config.similarityThreshold = runForm.similarityThreshold
    if (runForm.rerankType) config.rerankType = runForm.rerankType
    if (runForm.metrics.length) config.metrics = runForm.metrics
    const run = await evalApi.run(current.value.id, config)
    runDialog.value = false
    openReport(run.id)
  } finally {
    running.value = false
  }
}

/** 换一个 run 就把上一份正文缓存清掉，免得旧数据串到新行上 */
function openReport(runId: string) {
  reportDrawer.value = true
  report.value = null
  resultList.value = []
  resultsAt = 0
  Object.keys(summary).forEach(k => delete summary[k])
  Object.keys(details).forEach(k => delete details[k])
  Object.keys(detailLoading).forEach(k => delete detailLoading[k])
  pollReport(runId)
}

function pollReport(runId: string) {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = setTimeout(async () => {
    try {
      const run = await evalApi.getRun(runId)
      report.value = run
      if (run.summary) Object.assign(summary, safeParse(run.summary, {}))
      const terminal = run.status !== 'running' && run.status !== 'pending'
      await loadResults(run, terminal)
      if (!terminal) {
        pollReport(runId)
      }
    } catch {
      pollReport(runId)
    }
  }, 1500)
}

/**
 * 逐 case 明细从 eval_result 单拉。历史运行没有明细行，退回读整包 run.results，
 * 否则拆表之前的报告会变成空白。
 */
async function loadResults(run: any, force: boolean) {
  const now = Date.now()
  if (!force && now - resultsAt < RESULTS_INTERVAL_MS) return
  resultsAt = now
  let rows: any[] = []
  try {
    rows = (await evalApi.listRunResults(run.id)) || []
  } catch {
    rows = []
  }
  resultList.value = rows.length ? rows : safeParse(run.results, [])
}

function safeParse<T>(text: string | undefined | null, fallback: T): T {
  if (!text) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

/** 行主键：拆表后的明细有 id，历史运行只有整包 JSON 拆出来的 caseId / question */
function rowKey(row: any) {
  return row?.id || row?.caseId || row?.question || ''
}

/**
 * 展开某一行才拉正文。
 * <p>
 * 列表接口只返精简列，上百个 case 都带着回答与检索片段跟着轮询重传就是每次 MB 级响应；
 * 历史运行没有明细 id，正文本来就在行上，不用再拉。
 */
function onExpandResult(row: any, expanded?: any) {
  const key = rowKey(row)
  if (!key || !row?.id) return
  if (Array.isArray(expanded) && !expanded.includes(row)) return
  if (details[key] || detailLoading[key]) return
  detailLoading[key] = true
  evalApi.runResultDetail(report.value?.id || row.runId, row.id)
    .then(data => { details[key] = data || {} })
    .catch(() => { details[key] = {} })
    .finally(() => { delete detailLoading[key] })
}

/** 均值齐全但明细缺行 = 落库失败被吞掉了，得让这一眼能看出来，不能伪装成一份完整报告 */
const writeFailureTip = computed(() => {
  if (!report.value || report.value.status !== 'completed') return ''
  const done = Number(summary.completed ?? 0)
  const failures = Number(summary.resultWriteFailures ?? 0)
  if (failures > 0 && failures >= done) {
    return `${failures} 条用例明细全部落库失败，上方指标没有对应明细可查`
  }
  if (failures > 0) {
    return `其中 ${failures} 条用例明细落库失败，下方列表缺这几行`
  }
  if (done && resultList.value.length && done !== resultList.value.length) {
    return `汇总记了 ${done} 条已完成，明细只有 ${resultList.value.length} 行，两者不一致`
  }
  return ''
})

/** 均值背后的样本数：没参考文档、没跑判定的 case 不拉分母，但得让人看出这个均值是多少条算出来的 */
function sampleText(n: any) {
  return n ? `（n=${n}）` : ''
}

/** summary.metrics 是 SPI 指标的 {avg, cases} 表，没出一条分的指标不在这张表里 */
const extraMetrics = computed(() => {
  const table = summary.metrics
  if (!table || typeof table !== 'object') return []
  return Object.keys(table).map(name => ({
    name,
    avg: table[name]?.avg,
    cases: table[name]?.cases
  }))
})

/** eval_result.extra_scores 是 {指标名: 分值} 的扁表（精简列里就带着，不必展开重拉正文） */
function rowScores(row: any): Record<string, any> {
  return safeParse<Record<string, any>>(row?.extraScores, {})
}

/** 逐 case 的动态列：从行数据里聚合出现过哪些指标 */
const extraColumns = computed(() => {
  const names = new Set<string>()
  resultList.value.forEach(row => Object.keys(rowScores(row)).forEach(n => names.add(n)))
  return Array.from(names)
})

function fmt(v: any) {
  return v == null || isNaN(v) ? '-' : (Number(v) * 100).toFixed(1) + '%'
}

// ============ 运行历史 ============
const historyDrawer = ref(false)
const history = ref<any[]>([])
const historyLoading = ref(false)
const historyPageNum = ref(1)
const historyPageSize = ref(10)
const historyTotal = ref(0)
const historyQuery = reactive({ datasetId: '', status: '' })

function runStatusLabel(s?: string) {
  return ({ pending: '排队中', running: '执行中', completed: '已完成', failed: '失败', cancelled: '已停止' } as Record<string, string>)[s || ''] || s || '-'
}
function runStatusTag(s?: string): any {
  return ({ running: 'warning', completed: 'success', failed: 'danger', cancelled: 'info' } as Record<string, string>)[s || ''] || 'info'
}
/** 还没跑完的运行：可以停，但不能直接删（后端也会拒） */
function isLive(run: any) {
  return run?.status === 'running' || run?.status === 'pending'
}
function datasetName(id?: string) {
  return datasets.value.find(d => d.id === id)?.name || id || '-'
}
function parseSummary(text?: string) {
  return safeParse<Record<string, any>>(text, {})
}

function openRunHistory() {
  historyDrawer.value = true
  historyPageNum.value = 1
  loadHistory()
}
function reloadHistory() {
  historyPageNum.value = 1
  loadHistory()
}
async function loadHistory() {
  historyLoading.value = true
  try {
    const page = await evalApi.listRuns({
      datasetId: historyQuery.datasetId || undefined,
      status: historyQuery.status || undefined,
      pageNum: historyPageNum.value,
      pageSize: historyPageSize.value
    })
    history.value = page?.list || []
    historyTotal.value = page?.total || 0
  } finally {
    historyLoading.value = false
  }
}

function viewRunReport(run: any) {
  current.value = { id: run.datasetId, name: datasetName(run.datasetId) }
  historyDrawer.value = false
  openReport(run.id)
}

/** 停止评估：后端只登记取消意向，任务在下一个用例边界收尾，已完成部分照旧出报告 */
async function cancelRun(run: any) {
  cancelling.value = true
  try {
    await evalApi.cancelRun(run.id)
    ElMessage.success('已请求停止，当前用例跑完后结束')
    if (report.value?.id === run.id) pollReport(run.id)
    else loadHistory()
  } finally {
    cancelling.value = false
  }
}

async function deleteRun(run: any) {
  await evalApi.deleteRun(run.id)
  ElMessage.success('运行记录已删除')
  if (report.value?.id === run.id) reportDrawer.value = false
  loadHistory()
}

// ============ 运行对比 ============
/** 后端逐用例差值只列跌幅最大的 200 条（回归计数仍按全量算），标题要说的是截断后的那个数 */
const MAX_DIFF_CASES_UI = 200

const compareDrawer = ref(false)
const comparing = ref(false)
const compare = ref<EvalRunCompare | null>(null)
const compareTarget = ref<any>(null)
/** 空串 = 不传 baseline，由后端往前找上一次已结束运行 */
const compareBaseline = ref('')
const baselineOptions = ref<any[]>([])

/** 开销项的单量换算：这些不是 0~1 的分数，拿百分号去显示会把 1500ms 说成 150000% */
const COST_LABELS: Record<string, { label: string; unit: string }> = {
  avgLatency: { label: '平均耗时', unit: 'ms' },
  totalTokens: { label: '总 Token', unit: '' }
}

async function openCompare(run: any) {
  if (!run?.id) return
  compareTarget.value = run
  compareBaseline.value = ''
  compare.value = null
  compareDrawer.value = true
  // 候选只拉同数据集的运行：拿另一个数据集当基准能比出一个好看的数，但那个数没有意义
  baselineOptions.value = []
  try {
    const page = await evalApi.listRuns({ datasetId: run.datasetId, pageNum: 1, pageSize: 50 })
    baselineOptions.value = (page?.list || []).filter((r: any) => r.id !== run.id && !isLive(r))
  } catch {
    baselineOptions.value = []
  }
  loadCompare()
}

async function loadCompare() {
  if (!compareTarget.value) return
  comparing.value = true
  try {
    compare.value = await evalApi.compareRuns(compareTarget.value.id, compareBaseline.value || undefined)
  } catch {
    // 失败文案（比如「这是首次评估」）已经由 http 层弹出来了，这里只需要不把上一份结果留着显示
    compare.value = null
  } finally {
    comparing.value = false
  }
}

const compareMetricRows = computed(() => toDeltaRows(compare.value?.metrics || {}))
const compareCostRows = computed(() => {
  const table = compare.value?.cost || {}
  return Object.keys(table).map(name => {
    const d = table[name]
    const meta = COST_LABELS[name] || { label: metricLabel(name), unit: '' }
    return {
      name,
      label: `${meta.label}${meta.unit ? `（${meta.unit}）` : ''}`,
      fromText: fmtNumber(d.from, meta.unit),
      toText: fmtNumber(d.to, meta.unit),
      deltaText: fmtDeltaNumber(d.delta, meta.unit),
      // 成本是「越高越坏」，颜色语义跟分数相反：沿用分数的配色会把成本翻倍画成绿色
      deltaColor: d.delta == null || Number(d.delta) === 0 ? '' : (Number(d.delta) > 0 ? '#f56c6c' : '#67c23a')
    }
  })
})

function toDeltaRows(table: Record<string, MetricDelta>) {
  return Object.keys(table || {}).map(name => {
    const d = table[name]
    return { name, label: metricLabel(name), from: d.from, to: d.to, delta: d.delta, fromCases: d.fromCases, toCases: d.toCases }
  })
}

/**
 * 跌过阈值的指标（后端算好）。
 * <p>
 * 阈值判定不在前端重做一份：两边各写一套规则，迟早会出现「页面说通过、接口返回拦下」这种分裂读数。
 */
const compareRegress = computed(() => compare.value?.regressAlerts || [])

/** 0~1 的绝对跌幅说成百分点：跟表里的分数用同一个单位，不让人再去换算到底是 5% 还是 5 个点 */
const regressThresholdText = computed(() => {
  const v = Number(compare.value?.regressThreshold ?? 0)
  return v > 0 ? (v * 100).toFixed(1) + '%' : '未启用'
})

/** 展开行：综合分只看总体，定位永远回到「是哪一项跌的」这一层 */
function caseMetricRows(row: any) {
  return toDeltaRows(row?.metrics || {})
}

function fmtNumber(v: any, unit: string) {
  if (v == null || isNaN(Number(v))) return '-'
  return `${Math.round(Number(v) * 100) / 100}${unit ? ' ' + unit : ''}`
}

function fmtDeltaNumber(v: any, unit: string) {
  if (v == null || isNaN(Number(v))) return '-'
  const n = Number(v)
  return `${n > 0 ? '+' : ''}${Math.round(n * 100) / 100}${unit ? ' ' + unit : ''}`
}

function shortRunId(id?: string) {
  if (!id) return '-'
  return id.length > 14 ? `${id.substring(0, 6)}…${id.substring(id.length - 4)}` : id
}

function baselineLabel(run: any) {
  return `${run.startTime || '无开始时间'} · ${runStatusLabel(run.status)} · 召回 ${fmt(parseSummary(run.summary).avgRecall)}`
}

/** eval_result 的状态：success / partial（部分指标未出分）/ fail；为空表示这一侧压根没有这条用例 */
function resultStatusLabel(s?: string) {
  return ({ success: '成功', partial: '部分', fail: '失败' } as Record<string, string>)[s || ''] || '缺失'
}

onMounted(async () => {
  loadDatasets()
  const [m, k, a] = await Promise.allSettled([modelApi.options(), knowledgeApi.options(), applicationApi.options()])
  if (m.status === 'fulfilled') models.value = m.value
  if (k.status === 'fulfilled') knowledgeBases.value = k.value
  if (a.status === 'fulfilled') applications.value = a.value
})
</script>

<style lang="scss" scoped>
.page-toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.history-filters { display: flex; gap: 10px; margin-bottom: 12px; }
.card-header {
  display: flex; justify-content: space-between; align-items: center;
}
.dataset-card {
  .dataset-item {
    display: flex; justify-content: space-between; align-items: center;
    padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 8px;
    margin-bottom: 8px; cursor: pointer; transition: all 0.2s;

    &:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64,158,255,.12); }
    &.active { border-color: #409eff; background: #ecf5ff; }

    .ds-main { flex: 1; min-width: 0; margin-right: 8px; }
    .ds-name { font-size: 13px; font-weight: 600; color: #303133; }
    .ds-desc {
      font-size: 12px; color: #909399; margin-top: 2px;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .ds-meta { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
  }
}
.report-body { padding: 4px 8px; }
.form-hint { font-size: 12px; color: #909399; margin-left: 8px; line-height: 1.6; }
.import-hint { font-size: 12px; color: #909399; line-height: 1.7; }
.report-error { margin-bottom: 12px; }
.report-actions { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.report-actions-hint { font-size: 12px; color: #909399; }
.report-progress { margin-bottom: 16px; }
.summary-row { margin-bottom: 16px; }
.metric-card {
  background: #f7f8fa; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 14px 8px; text-align: center;
  .metric-value { font-size: 20px; font-weight: 700; color: #303133; }
  .metric-label { font-size: 12px; color: #909399; margin-top: 4px; }
}
.report-loading { height: 200px; }
.compare-bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.compare-run { font-size: 13px; font-weight: 600; color: #303133; }
.compare-run-meta { font-weight: 400; color: #909399; margin-left: 6px; font-size: 12px; }
.compare-hint { font-size: 12px; color: #909399; line-height: 1.7; margin-top: 6px; }
.compare-body { margin-top: 12px; min-height: 160px; }
.compare-alert { margin-bottom: 10px; }
.regress-list {
  margin: 0 0 12px; padding: 8px 12px; font-size: 13px;
  background: #fef0f0; border: 1px solid #fde2e2; border-radius: 8px;
}
.regress-item { display: flex; gap: 12px; align-items: baseline; flex-wrap: wrap; padding: 2px 0; }
.regress-item b { color: #f56c6c; }
.regress-name { min-width: 96px; font-weight: 600; color: #303133; }
.regress-list .compare-hint { margin-top: 6px; }
.compare-counts {
  display: flex; gap: 18px; flex-wrap: wrap; font-size: 13px; color: #606266;
  padding: 10px 12px; background: #f7f8fa; border: 1px solid #ebeef5; border-radius: 8px;
}
.compare-counts b { font-size: 15px; }
.compare-counts .down { color: #f56c6c; }
.compare-counts .up { color: #67c23a; }
.compare-block { margin-top: 16px; }
.compare-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.case-delta-table { width: auto; margin: 0 12px; }
.cell-sm { font-size: 12px; }

@media (max-width: 992px) {
  .case-col { margin-top: 16px; }
}
@media (max-width: 768px) {
  .metric-card { margin-bottom: 12px; padding: 12px 4px; }
  .metric-card .metric-value { font-size: 18px; }
}
</style>
