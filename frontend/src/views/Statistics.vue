<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><DataAnalysis /></el-icon> 统计分析</div>
        <div class="page-subtitle">报告生成与签发的运行概览</div>
      </div>
      <div class="header-actions">
        <el-select v-model="filters.groupBy" size="small" class="select-w-110" @change="loadData">
          <el-option label="按日" value="day" />
          <el-option label="按周" value="week" />
          <el-option label="按月" value="month" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          size="small"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="date-w-260"
          @change="loadData"
        />
        <el-button size="small" plain @click="loadData"><el-icon><Refresh /></el-icon>刷新</el-button>
      </div>
    </div>

    <div class="kpi-grid perf-section">
      <div v-for="card in statCards" :key="card.label" class="kpi-card">
        <div class="kpi-icon" :style="{ background: card.color }">
          <el-icon :size="20"><component :is="card.icon" /></el-icon>
        </div>
        <div class="kpi-content">
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value">{{ card.value }}</div>
          <div v-if="card.sub" class="kpi-sub">{{ card.sub }}</div>
        </div>
      </div>
    </div>

    <div v-if="chartReady" class="chart-grid perf-section">
      <div class="chart-card">
        <div class="card-head">
          <div class="section-title"><el-icon><TrendCharts /></el-icon>报告生成趋势</div>
          <div class="card-meta">总量 {{ reportTotal }}</div>
        </div>
        <svg :class="['trend-chart', !reportTrend.length && 'trend-chart-empty']" viewBox="0 0 420 220" preserveAspectRatio="none">
          <line v-for="line in yGrid" :key="line" x1="36" :x2="400" :y1="line" :y2="line" class="grid-line" />
          <polyline :points="reportPolyline" class="report-line" />
          <circle v-for="point in reportPoints" :key="point.key" :cx="point.x" :cy="point.y" r="4" class="report-dot" />
        </svg>
        <div v-if="!reportTrend.length" class="chart-empty">暂无趋势数据</div>
        <div class="axis-labels">
          <span v-for="point in reportPoints" :key="point.key">{{ shortDate(point.label) }}</span>
        </div>
      </div>

      <div class="chart-card">
        <div class="card-head">
          <div class="section-title"><el-icon><DataBoard /></el-icon>报告状态分布</div>
          <div class="card-meta">总计 {{ overview.totalReports || 0 }}</div>
        </div>
        <div v-if="statusRows.length" class="bar-list">
          <div v-for="row in statusRows" :key="row.status" class="bar-row">
            <span class="bar-label">{{ row.label }}</span>
            <div class="bar-track"><span class="bar-fill" :style="{ width: `${row.percent}%`, background: row.color }" /></div>
            <span class="bar-value">{{ row.count }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无状态统计" :image-size="60" />
      </div>
    </div>
    <div v-else class="chart-placeholder">图表加载中...</div>
  

    <div v-if="evalReady" class="chart-grid chart-grid-spaced perf-section">
      <div class="chart-card">
        <div class="card-head">
          <div class="section-title"><el-icon><DataAnalysis /></el-icon>评测标签指标</div>
          <div class="card-meta">{{ evalRunMeta }}</div>
        </div>
        <div class="eval-controls">
          <el-select v-model="selectedRunId" size="small" class="select-w-220" @change="loadEvalMetrics">
            <el-option v-for="run in evalRuns" :key="run.runId" :label="`${run.runName || run.runId} · ${run.modelName || '-'} · ${run.datasetName || '-'}`" :value="run.runId" />
          </el-select>
          <el-select v-model="evalCompareMetric" size="small" class="select-w-120" @change="loadEvalCompare">
            <el-option label="F1" value="f1" />
            <el-option label="Recall" value="recall" />
            <el-option label="Precision" value="precision" />
            <el-option label="AUC" value="auc" />
          </el-select>
          <el-select v-model="evalCompareTag" size="small" class="select-w-140" @change="loadEvalCompare">
            <el-option label="全部标签" value="ALL" />
            <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </div>

        <div v-if="evalTagMetrics.length" class="eval-table">
          <div class="eval-row eval-head">
            <span>??</span>
            <span>??</span>
            <span>??</span>
            <span>???</span>
          </div>
          <VirtualList
            v-if="perfMode"
            :items="evalTagMetrics"
            :item-height="38"
            :height="evalListHeight"
            class="eval-virtual"
          >
            <template #default="{ item }">
              <div class="eval-row">
                <span>{{ item.tagName || 'ALL' }}</span>
                <span>{{ item.metricName }}</span>
                <span>{{ formatMetric(item.metricValue) }}</span>
                <span>{{ item.support || '-' }}</span>
              </div>
            </template>
          </VirtualList>
          <div v-else>
            <div v-for="row in evalTagMetrics" :key="`${row.tagName}-${row.metricName}`" class="eval-row">
              <span>{{ row.tagName || 'ALL' }}</span>
              <span>{{ row.metricName }}</span>
              <span>{{ formatMetric(row.metricValue) }}</span>
              <span>{{ row.support || '-' }}</span>
            </div>
          </div>
        </div>
<el-empty v-else description="暂无标签级评测" :image-size="60" />
      </div>

      <div class="chart-card">
        <div class="card-head">
          <div class="section-title"><el-icon><TrendCharts /></el-icon>模型对比</div>
          <div class="card-meta">{{ evalCompareMetric.toUpperCase() }} / {{ evalCompareTagLabel }}</div>
        </div>
        <div v-if="evalCompareRows.length" class="eval-table">
          <div class="eval-row eval-head">
            <span>??</span>
            <span>???</span>
            <span>??</span>
            <span>??</span>
          </div>
          <VirtualList
            v-if="perfMode"
            :items="evalCompareRows"
            :item-height="38"
            :height="evalListHeight"
            class="eval-virtual"
          >
            <template #default="{ item }">
              <div class="eval-row">
                <span>{{ item.modelName || '-' }}</span>
                <span>{{ item.datasetName || '-' }}</span>
                <span>{{ item.metricName }}</span>
                <span>{{ formatMetric(item.metricValue) }}</span>
              </div>
            </template>
          </VirtualList>
          <div v-else>
            <div v-for="row in evalCompareRows" :key="`${row.modelName}-${row.tagName}`" class="eval-row">
              <span>{{ row.modelName || '-' }}</span>
              <span>{{ row.datasetName || '-' }}</span>
              <span>{{ row.metricName }}</span>
              <span>{{ formatMetric(row.metricValue) }}</span>
            </div>
          </div>
        </div>
<el-empty v-else description="暂无对比数据" :image-size="60" />
      </div>
    </div>
    <div v-else class="chart-placeholder">评测数据加载中...</div>

</div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis,
  DataBoard,
  Document,
  Refresh,
  TrendCharts
} from '@element-plus/icons-vue'
import { getOverview, getReportTrend } from '@/api/statistics'
import { listEvaluationRuns, getEvaluationMetrics, compareEvaluationModels } from '@/api/evaluation'
import { runWhenIdle } from '@/utils/idle'
import VirtualList from '@/components/VirtualList.vue'

const overview = ref({})
const reportTrend = ref([])
const evalRuns = ref([])
const selectedRunId = ref(null)
const evalTagMetrics = ref([])
const evalCompareRows = ref([])
const evalCompareMetric = ref('f1')
const evalCompareTag = ref('ALL')
const dateRange = ref([])
const filters = reactive({ groupBy: 'month' })
const perfMode = import.meta.env.VITE_PERF_MODE === 'true'
const chartReady = ref(!perfMode)
const evalReady = ref(!perfMode)
const evalListHeight = ref(260)

const SIX_MONTHS_AGO = new Date()
SIX_MONTHS_AGO.setMonth(SIX_MONTHS_AGO.getMonth() - 6)
const startDate6m = SIX_MONTHS_AGO.toISOString().slice(0, 10)

const statCards = computed(() => {
  const ov = overview.value || {}
  const signedRate = ov.totalReports ? `${((ov.signedReports || 0) / ov.totalReports * 100).toFixed(1)}%` : '0.0%'
  return [
    { label: '总病例数', value: ov.totalCases || 0, icon: Document, color: '#3A86E8' },
    { label: '总报告数', value: ov.totalReports || 0, icon: DataBoard, color: '#4A9EFF' },
    { label: '已签发报告', value: ov.signedReports || 0, icon: TrendCharts, color: '#34A86F', sub: `签发率 ${signedRate}` }
  ]
})

const tagOptions = computed(() => {
  const tags = new Set()
  evalTagMetrics.value.forEach(row => {
    if (row?.tagName) tags.add(row.tagName)
  })
  return Array.from(tags)
})

const evalCompareTagLabel = computed(() => (
  evalCompareTag.value === 'ALL' ? '全部标签' : (evalCompareTag.value || '全部标签')
))

const evalRunMeta = computed(() => (
  evalRuns.value.length ? `评测批次 ${evalRuns.value.length}` : '暂无评测'
))

const statusRows = computed(() => {
  const stats = overview.value?.reportStatusStats || {}
  const total = Object.values(stats).reduce((sum, item) => sum + Number(item || 0), 0) || 1
  const labels = {
    NONE: '未生成',
    AI_DRAFT: 'AI 草稿',
    EDITING: '人工编辑',
    SIGNED: '已签发'
  }
  const colors = {
    NONE: '#6F86A6',
    AI_DRAFT: '#4A9EFF',
    EDITING: '#E0A44A',
    SIGNED: '#34A86F'
  }
  return Object.entries(stats).map(([status, count]) => ({
    status,
    label: labels[status] || status,
    count: Number(count || 0),
    percent: Math.round(Number(count || 0) * 100 / total),
    color: colors[status] || '#6F86A6'
  }))
})

const reportTotal = computed(() => reportTrend.value.reduce((sum, item) => sum + Number(item.count || 0), 0))

const yGrid = [24, 72, 120, 168]
const reportPoints = computed(() => buildChartPoints(reportTrend.value, 'count'))
const reportPolyline = computed(() => reportPoints.value.map(point => `${point.x},${point.y}`).join(' '))

function buildChartPoints(rows, key) {
  if (!rows?.length) return []
  const values = rows.map(item => Number(item[key] || 0))
  const max = Math.max(...values, 1)
  const width = 364
  const startX = 36
  const baseY = 188
  const height = 148
  const step = rows.length > 1 ? width / (rows.length - 1) : 0
  return rows.map((item, index) => ({
    key: `${item.date}-${index}`,
    label: item.date,
    value: Number(item[key] || 0),
    x: startX + step * index,
    y: baseY - (Number(item[key] || 0) / max) * height
  }))
}

const loadEvalRuns = async () => {
  try {
    const res = await listEvaluationRuns()
    evalRuns.value = res.data || []
    if (!selectedRunId.value && evalRuns.value.length) {
      selectedRunId.value = evalRuns.value[0].runId
    }
    await loadEvalMetrics()
  } catch {
    evalRuns.value = []
  } finally {
    if (perfMode && !evalReady.value) {
      runWhenIdle(() => { evalReady.value = true }, { timeout: 1600 })
    }
  }
}

const loadEvalMetrics = async () => {
  if (!selectedRunId.value) {
    evalTagMetrics.value = []
    return
  }
  try {
    const res = await getEvaluationMetrics(selectedRunId.value, { scope: 'TAG' })
    evalTagMetrics.value = res.data || []
  } catch {
    evalTagMetrics.value = []
  }
  const tags = evalTagMetrics.value.map(row => row?.tagName).filter(Boolean)
  if (evalCompareTag.value !== 'ALL' && !tags.includes(evalCompareTag.value)) {
    evalCompareTag.value = 'ALL'
  }
  await loadEvalCompare()
}

const loadEvalCompare = async () => {
  const run = evalRuns.value.find(r => r.runId === selectedRunId.value)
  if (!run) { evalCompareRows.value = []; return }
  try {
    const res = await compareEvaluationModels({
      datasetName: run.datasetName,
      metricName: evalCompareMetric.value,
      tagName: evalCompareTag.value === 'ALL' ? null : evalCompareTag.value
    })
    evalCompareRows.value = res.data || []
  } catch {
    evalCompareRows.value = []
  }
}

const loadData = async () => {
  try {
    const params = {
      startDate: dateRange.value?.[0] || startDate6m,
      endDate: dateRange.value?.[1],
      groupBy: filters.groupBy
    }
    const [overviewRes, reportRes] = await Promise.all([
      getOverview(),
      getReportTrend(params)
    ])
    overview.value = overviewRes.data || {}
    reportTrend.value = reportRes.data || []
  } catch {
    ElMessage.error('统计数据加载失败')
  }
}

const shortDate = (value) => value ? String(value).slice(5, 10) : '-'
const formatMetric = (value) => {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(4) : '-'
}

onMounted(async () => {
  await loadData()
  if (perfMode && !chartReady.value) {
    runWhenIdle(() => { chartReady.value = true }, { timeout: 1200 })
  } else {
    chartReady.value = true
  }
  runWhenIdle(() => loadEvalRuns(), { timeout: 1500 })
})
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: var(--xrag-bg);
  color: var(--xrag-text);
}
.page-header,
.kpi-card,
.chart-card {
  background: var(--xrag-panel);
  border: 1px solid var(--xrag-border);
  box-shadow: var(--xrag-shadow);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px;
  border-radius: 14px;
  margin-bottom: 16px;
}
.page-title,
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--xrag-text);
  font-weight: 600;
}
.page-title { font-size: 18px; }
.page-subtitle { margin-top: 6px; color: var(--xrag-text-soft); font-size: 13px; }
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.select-w-110 { width: 110px; }
.date-w-260 { width: 260px; }
.select-w-220 { width: 220px; }
.select-w-120 { width: 120px; }
.select-w-140 { width: 140px; }
.chart-grid-spaced { margin-top: 16px; }
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.kpi-card {
  border-radius: 14px;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.kpi-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.kpi-label { color: var(--xrag-text-soft); font-size: 13px; }
.kpi-value { color: var(--xrag-text); font-size: 28px; font-weight: 700; margin-top: 4px; }
.kpi-sub { color: var(--xrag-text-soft); font-size: 12px; margin-top: 4px; }

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.chart-card { border-radius: 14px; padding: 16px; position: relative; }
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.card-meta { color: var(--xrag-text-soft); font-size: 12px; }
.trend-chart {
  width: 100%;
  height: 220px;
  background: linear-gradient(180deg, rgba(74, 158, 255, 0.08), rgba(74, 158, 255, 0.01));
  border-radius: 12px;
  border: 1px solid rgba(111, 134, 166, 0.12);
}
.trend-chart-empty .grid-line { stroke: rgba(111, 134, 166, 0.14); }
.grid-line { stroke: rgba(111, 134, 166, 0.18); stroke-width: 1; }
.report-line { fill: none; stroke-width: 3; stroke-linecap: round; stroke-linejoin: round; stroke: #4a9eff; }
.report-dot { fill: #4a9eff; }
.chart-empty {
  position: absolute;
  left: 50%;
  top: 54%;
  transform: translate(-50%, -50%);
  color: rgba(220, 231, 247, 0.7);
  font-size: 12px;
  pointer-events: none;
}
.axis-labels {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--xrag-text-soft);
  margin-top: 10px;
}
.bar-list { display: flex; flex-direction: column; gap: 12px; margin-top: 14px; }
.bar-row {
  display: grid;
  grid-template-columns: 72px 1fr 44px;
  align-items: center;
  gap: 10px;
}
.bar-label, .bar-value { font-size: 12px; color: var(--xrag-text); }
.bar-track {
  width: 100%;
  height: 10px;
  background: rgba(111, 134, 166, 0.16);
  border-radius: 999px;
  overflow: hidden;
}

:deep(.el-empty__description) {
  color: rgba(220, 231, 247, 0.75) !important;
}
.bar-fill { display: block; height: 100%; border-radius: 999px; }

.chart-placeholder {
  padding: 32px 0;
  text-align: center;
  color: var(--xrag-text-faint);
  border: 1px dashed var(--xrag-border);
  border-radius: 12px;
  background: rgba(15, 25, 35, 0.5);
}

:deep(.el-select__wrapper),
:deep(.el-input__wrapper),
:deep(.el-date-editor.el-input__wrapper) {
  background: rgba(233, 238, 245, 0.05) !important;
  border-color: var(--xrag-border-strong) !important;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.18) inset !important;
}
:deep(.el-select__placeholder),
:deep(.el-input__inner),
:deep(.el-range-input),
:deep(.el-form-item__label),
:deep(.el-empty__description) {
  color: var(--xrag-text) !important;
}
:deep(.el-button--default),
:deep(.el-button.is-plain),
:deep(.el-button.is-text) {
  background: rgba(233, 238, 245, 0.06) !important;
  border-color: rgba(111, 134, 166, 0.28) !important;
  color: var(--xrag-text) !important;
}

@media (max-width: 1200px) {
  .kpi-grid, .chart-grid { grid-template-columns: 1fr; }
}
</style>
