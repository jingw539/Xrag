<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><DataAnalysis /></el-icon> 统计分析</div>
        <div class="page-subtitle">报告生成与签发概览</div>
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

    </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  DataAnalysis,
  DataBoard,
  Document,
  Refresh,
  TrendCharts
} from '@element-plus/icons-vue'
import { getOverview, getReportTrend } from '@/api/statistics'
import { runWhenIdle } from '@/utils/idle'

const userStore = useUserStore()
const overview = ref({})
const reportTrend = ref([])
const dateRange = ref([])
const filters = reactive({ groupBy: 'month' })
const perfMode = import.meta.env.VITE_PERF_MODE === 'true'
const chartReady = ref(!perfMode)

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

const statusRows = computed(() => {
  const stats = overview.value?.reportStatusStats || {}
  const total = Object.values(stats).reduce((sum, item) => sum + Number(item || 0), 0) || 1
  const labels = { NONE: '未生成', AI_DRAFT: 'AI 草稿', EDITING: '人工编辑', SIGNED: '已签发' }
  const colors = { NONE: '#6F86A6', AI_DRAFT: '#4A9EFF', EDITING: '#E0A44A', SIGNED: '#34A86F' }
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

const loadData = async () => {
  try {
    const doctorId = userStore.isDoctor ? userStore.userInfo?.userId : undefined
    const params = {
      startDate: dateRange.value?.[0] || startDate6m,
      endDate: dateRange.value?.[1],
      groupBy: filters.groupBy,
      doctorId
    }
    const [overviewRes, reportRes] = await Promise.all([
      getOverview(doctorId ? { doctorId } : {}),
      getReportTrend(params)
    ])
    overview.value = overviewRes.data || {}
    reportTrend.value = reportRes.data || []
  } catch {
    ElMessage.error('统计数据加载失败')
  }
}

const shortDate = (value) => value ? String(value).slice(5, 10) : '-'

onMounted(async () => {
  await loadData()
  if (perfMode && !chartReady.value) {
    runWhenIdle(() => { chartReady.value = true }, { timeout: 1200 })
  } else {
    chartReady.value = true
  }
})
</script>

<style scoped>
.page-wrap { min-height: 100%; padding: 20px; background: var(--xrag-bg); color: var(--xrag-text); }
.page-header, .kpi-card, .chart-card { background: var(--xrag-panel); border: 1px solid var(--xrag-border); box-shadow: var(--xrag-shadow); }
.page-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 14px 16px; border-radius: 10px; }
.page-title { font-size: 18px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.page-subtitle { color: var(--xrag-text-soft); font-size: 13px; margin-top: 4px; }
.header-actions { display: flex; align-items: center; gap: 10px; }
.select-w-110 { width: 110px; }
.date-w-260 { width: 260px; }
.perf-section { margin-bottom: 24px; }
.kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); column-gap: 12px; row-gap: 16px; }
.kpi-grid + .chart-grid { margin-top: 24px; }
.chart-grid + .chart-grid { margin-top: 24px; }
.kpi-card { padding: 14px; border-radius: 10px; display: flex; gap: 12px; align-items: center; }
.kpi-icon { width: 42px; height: 42px; border-radius: 10px; display: grid; place-items: center; color: #fff; }
.kpi-label { color: var(--xrag-text-soft); font-size: 13px; }
.kpi-value { font-size: 22px; font-weight: 700; }
.kpi-sub { color: var(--xrag-text-soft); font-size: 12px; }
.perf-section + .perf-section { margin-top: 18px; }
.chart-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 12px; }
.chart-card { border-radius: 12px; padding: 12px 14px; }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.section-title { display: flex; align-items: center; gap: 8px; font-weight: 700; }
.card-meta { color: var(--xrag-text-soft); font-size: 12px; }
.trend-chart { width: 100%; height: 220px; }
.trend-chart-empty .report-line, .trend-chart-empty .report-dot { display: none; }
.grid-line { stroke: rgba(255,255,255,0.05); stroke-width: 1; }
.report-line { fill: none; stroke: #4A9EFF; stroke-width: 2; }
.report-dot { fill: #4A9EFF; }
.axis-labels { display: grid; grid-template-columns: repeat(auto-fit, minmax(40px, 1fr)); gap: 6px; margin-top: 6px; font-size: 11px; color: var(--xrag-text-soft); }
.bar-list { display: flex; flex-direction: column; gap: 8px; }
.bar-row { display: grid; grid-template-columns: 82px 1fr 46px; align-items: center; gap: 8px; }
.bar-label { color: var(--xrag-text-soft); font-size: 13px; }
.bar-track { width: 100%; height: 10px; border-radius: 6px; background: rgba(255,255,255,0.04); overflow: hidden; }
.bar-fill { display: block; height: 100%; border-radius: 6px; }
.bar-value { text-align: right; font-weight: 700; }
.chart-empty, .chart-placeholder { text-align: center; padding: 16px 0; color: var(--xrag-text-soft); }

/* 日期选择深色适配 */
:deep(.el-picker-panel) { background: #0f1724; color: var(--xrag-text); border-color: var(--xrag-border); }
:deep(.el-picker-panel__content) { color: var(--xrag-text); }
:deep(.el-date-table) { background: transparent; }
:deep(.el-date-table-cell .el-date-table-cell__text) { background: transparent !important; color: var(--xrag-text); }
:deep(.el-date-table-cell.is-in-range .el-date-table-cell__text) {
  background: rgba(36,87,166,0.16) !important;
  color: #e9eef5;
  border-radius: 0;
}
:deep(.el-date-table-cell.is-range-start .el-date-table-cell__text),
:deep(.el-date-table-cell.is-range-end .el-date-table-cell__text),
:deep(.el-date-table-cell.is-selected .el-date-table-cell__text) {
  background: #2457a6;
  color: #fff;
  border-radius: 4px;
}
:deep(.el-picker-panel .el-date-table td.today .el-date-table-cell__text) {
  color: #69c0ff;
}
:deep(.el-picker-panel .el-date-table th) { color: var(--xrag-text-soft); }

/* popper attaches to body, so force dark panel globally */
:global(.el-popper .el-picker-panel) {
  background: #0f1724 !important;
  color: var(--xrag-text) !important;
  border-color: var(--xrag-border) !important;
}
:global(.el-popper .el-date-table) { background: transparent !important; }
:global(.el-popper .el-date-table th),
:global(.el-popper .el-date-table td) { color: var(--xrag-text) !important; }
:global(.el-popper .el-date-table-cell__text) { color: var(--xrag-text) !important; }
:global(.el-popper .el-date-table-cell.is-in-range .el-date-table-cell__text) {
  background: rgba(36,87,166,0.16) !important;
  color: #e9eef5 !important;
}
:global(.el-popper .el-date-table-cell.is-range-start .el-date-table-cell__text),
:global(.el-popper .el-date-table-cell.is-range-end .el-date-table-cell__text),
:global(.el-popper .el-date-table-cell.is-selected .el-date-table-cell__text) {
  background: #2457a6 !important;
  color: #fff !important;
}
</style>
