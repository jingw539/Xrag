<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="page-title"><el-icon><DataAnalysis /></el-icon> 统计分析</div>
        <div class="page-subtitle">围绕生成、评测、质控与告警的综合运营视图</div>
      </div>
      <div class="header-actions">
        <el-select v-model="filters.groupBy" size="small" style="width: 110px" @change="loadData">
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
          style="width: 260px"
          @change="loadData"
        />
        <el-select v-model="filters.grade" clearable size="small" placeholder="质控等级" style="width: 120px">
          <el-option label="A 级" value="A" />
          <el-option label="B 级" value="B" />
          <el-option label="C 级" value="C" />
          <el-option label="D 级" value="D" />
        </el-select>
        <el-select v-model="filters.department" clearable size="small" placeholder="科室" style="width: 150px">
          <el-option v-for="dept in departmentOptions" :key="dept" :label="dept" :value="dept" />
        </el-select>
        <el-button size="small" plain @click="loadData"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button size="small" plain @click="exportCsv"><el-icon><Download /></el-icon>导出异常</el-button>
      </div>
    </div>

    <div class="kpi-grid">
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

    <div class="summary-grid">
      <div class="info-card">
        <div class="section-title"><el-icon><MagicStick /></el-icon>趋势摘要</div>
        <div class="summary-list">
          <div class="summary-item info">最新平均 F1 为 {{ trendSummary.latest }}，较上一周期{{ Number(trendSummary.delta) >= 0 ? '提升' : '下降' }} {{ Math.abs(Number(trendSummary.delta)).toFixed(1) }}%</div>
          <div class="summary-item success">当前筛出的低质量报告 {{ filteredQualityIssues.length }} 份，涉及 {{ departmentCoverage }} 个科室</div>
          <div class="summary-item warning">待处理危急值 {{ overview.pendingAlerts || 0 }} 条，建议优先联动质控与临床值班</div>
        </div>
      </div>
      <div class="info-card">
        <div class="section-title"><el-icon><Bell /></el-icon>处置建议</div>
        <div class="summary-list">
          <div v-for="(item, index) in actionItems" :key="index" class="summary-item" :class="item.type">
            <span>{{ item.text }}</span>
            <el-button size="small" text @click="handleAction(item)">{{ item.action }}</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="chart-grid">
      <div class="chart-card">
        <div class="card-head">
          <div class="section-title"><el-icon><TrendCharts /></el-icon>报告生成趋势</div>
          <div class="card-meta">总量 {{ reportTotal }}</div>
        </div>
        <svg class="trend-chart" viewBox="0 0 420 220" preserveAspectRatio="none">
          <line v-for="line in yGrid" :key="line" x1="36" :x2="400" :y1="line" :y2="line" class="grid-line" />
          <polyline :points="reportPolyline" class="report-line" />
          <circle v-for="point in reportPoints" :key="point.key" :cx="point.x" :cy="point.y" r="4" class="report-dot" />
        </svg>
        <div class="axis-labels">
          <span v-for="point in reportPoints" :key="point.key">{{ shortDate(point.label) }}</span>
        </div>
      </div>

      <div class="chart-card">
        <div class="card-head">
          <div class="section-title"><el-icon><DataLine /></el-icon>评测 F1 趋势</div>
          <div class="card-meta">均值 {{ trendSummary.latest }}</div>
        </div>
        <svg class="trend-chart" viewBox="0 0 420 220" preserveAspectRatio="none">
          <line v-for="line in yGrid" :key="line" x1="36" :x2="400" :y1="line" :y2="line" class="grid-line" />
          <polyline :points="evalPolyline" class="eval-line" />
          <circle v-for="point in evalPoints" :key="point.key" :cx="point.x" :cy="point.y" r="4" class="eval-dot" />
        </svg>
        <div class="axis-labels">
          <span v-for="point in evalPoints" :key="point.key">{{ shortDate(point.label) }}</span>
        </div>
      </div>

      <div class="chart-card">
        <div class="card-head">
          <div class="section-title"><el-icon><PieChart /></el-icon>质控等级分布</div>
          <div class="card-meta">A/B 占比 {{ highGradeRate }}</div>
        </div>
        <div v-if="filteredGradeRows.length" class="bar-list">
          <div v-for="row in filteredGradeRows" :key="row.grade" class="bar-row">
            <span class="bar-label">{{ row.grade }} 级</span>
            <div class="bar-track"><span class="bar-fill" :style="{ width: `${row.percent}%`, background: gradeColor(row.grade) }" /></div>
            <span class="bar-value">{{ row.count }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无等级数据" :image-size="60" />
      </div>

      <div class="chart-card">
        <div class="card-head">
          <div class="section-title"><el-icon><Warning /></el-icon>危急值类型分布</div>
          <div class="card-meta">共 {{ alertTotal }} 条</div>
        </div>
        <div v-if="filteredAlertRows.length" class="bar-list">
          <div v-for="row in filteredAlertRows" :key="row.type" class="bar-row">
            <span class="bar-label">{{ row.type }}</span>
            <div class="bar-track"><span class="bar-fill danger" :style="{ width: `${row.percent}%` }" /></div>
            <span class="bar-value">{{ row.count }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无危急值统计" :image-size="60" />
      </div>

      <div class="chart-card full-span">
        <div class="card-head">
          <div class="section-title"><el-icon><DataBoard /></el-icon>14 类病理标签能力</div>
          <div class="card-meta">按 F1 从高到低排序</div>
        </div>
        <el-table :data="perLabelStats" size="small" empty-text="暂无标签统计">
          <el-table-column prop="label" label="标签" min-width="180" />
          <el-table-column prop="count" label="命中数" width="100" align="center" />
          <el-table-column prop="precision" label="Precision" width="110" align="center">
            <template #default="{ row }">{{ fmtPct(row.precision) }}</template>
          </el-table-column>
          <el-table-column prop="recall" label="Recall" width="110" align="center">
            <template #default="{ row }">{{ fmtPct(row.recall) }}</template>
          </el-table-column>
          <el-table-column prop="f1" label="F1" min-width="220">
            <template #default="{ row }">
              <div class="score-cell">
                <div class="score-track"><span class="score-fill" :style="{ width: `${Number(row.f1 || 0) * 100}%` }" /></div>
                <span>{{ fmtPct(row.f1) }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="chart-card full-span" id="quality-issues">
        <div class="card-head">
          <div class="section-title"><el-icon><Cpu /></el-icon>低质量报告跟踪</div>
          <div class="card-meta">筛选后 {{ filteredQualityIssues.length }} 条</div>
        </div>
        <el-table :data="filteredQualityIssues" size="small" empty-text="暂无异常报告">
          <el-table-column prop="examNo" label="检查号" min-width="130" />
          <el-table-column prop="department" label="科室" width="110" />
          <el-table-column prop="qualityGrade" label="等级" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :style="tagStyle(row.qualityGrade)">{{ row.qualityGrade }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="f1Score" label="F1" width="100" align="center">
            <template #default="{ row }">{{ fmtPct(row.f1Score) }}</template>
          </el-table-column>
          <el-table-column prop="problems" label="问题摘要" min-width="260">
            <template #default="{ row }">
              <el-tag v-for="problem in row.problems || []" :key="problem" size="small" class="issue-tag">{{ problem }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reportStatus" label="报告状态" width="100" align="center" />
          <el-table-column prop="evalTime" label="评测时间" min-width="160">
            <template #default="{ row }">{{ formatDateTime(row.evalTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <el-button link size="small" @click="goToCase(row.caseId)">查看病例</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Cpu,
  DataAnalysis,
  DataBoard,
  DataLine,
  Document,
  Download,
  Histogram,
  MagicStick,
  Odometer,
  PieChart,
  Refresh,
  Timer,
  TrendCharts,
  Warning,
  Check
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getEvalTrend, getModelComparison, getOverview, getPerLabelStats, getQualityIssues, getReportTrend } from '@/api/statistics'

const router = useRouter()
const overview = ref({})
const reportTrend = ref([])
const evalTrend = ref([])
const modelComparison = ref([])
const perLabelStats = ref([])
const qualityIssues = ref([])
const dateRange = ref([])
const filters = reactive({ groupBy: 'month', grade: '', department: '' })

const SIX_MONTHS_AGO = new Date()
SIX_MONTHS_AGO.setMonth(SIX_MONTHS_AGO.getMonth() - 6)
const startDate6m = SIX_MONTHS_AGO.toISOString().slice(0, 10)

const statCards = computed(() => {
  const ov = overview.value || {}
  return [
    { label: '总病例数', value: ov.totalCases || 0, icon: Document, color: '#3A86E8' },
    { label: '已签发报告', value: ov.signedReports || 0, icon: Check, color: '#34A86F' },
    { label: '平均 F1', value: fmtPct(ov.avgF1Score), icon: Histogram, color: '#6E56CF' },
    { label: '待处理危急值', value: ov.pendingAlerts || 0, icon: Bell, color: '#D45C5C' },
    { label: 'AI 采纳率', value: fmtPct(ov.adoptionRate), sub: 'A/B 级占比', icon: Odometer, color: '#C78A2C' },
    { label: '平均生成时长', value: formatSeconds(ov.avgGenTimeMs), sub: '生成到签发链路', icon: Timer, color: '#2A9D8F' }
  ]
})

const gradeRows = computed(() => {
  const grades = overview.value?.gradeDistribution || {}
  const total = Object.values(grades).reduce((sum, item) => sum + Number(item || 0), 0) || 1
  return Object.entries(grades).map(([grade, count]) => ({
    grade,
    count: Number(count || 0),
    percent: Math.round(Number(count || 0) * 100 / total)
  })).sort((a, b) => a.grade.localeCompare(b.grade))
})

const alertRows = computed(() => {
  const stats = overview.value?.alertTypeStats || {}
  const total = Object.values(stats).reduce((sum, item) => sum + Number(item || 0), 0) || 1
  return Object.entries(stats).map(([type, count]) => ({
    type,
    count: Number(count || 0),
    percent: Math.round(Number(count || 0) * 100 / total)
  })).sort((a, b) => b.count - a.count)
})

const departmentOptions = computed(() => [...new Set(qualityIssues.value.map(item => item.department).filter(Boolean))])
const filteredGradeRows = computed(() => filters.grade ? gradeRows.value.filter(item => item.grade === filters.grade) : gradeRows.value)
const filteredAlertRows = computed(() => alertRows.value)
const filteredQualityIssues = computed(() => qualityIssues.value.filter(item => {
  const matchGrade = !filters.grade || item.qualityGrade === filters.grade
  const matchDept = !filters.department || item.department === filters.department
  return matchGrade && matchDept
}))

const reportTotal = computed(() => reportTrend.value.reduce((sum, item) => sum + Number(item.count || 0), 0))
const alertTotal = computed(() => filteredAlertRows.value.reduce((sum, item) => sum + Number(item.count || 0), 0))
const departmentCoverage = computed(() => new Set(filteredQualityIssues.value.map(item => item.department).filter(Boolean)).size)
const highGradeRate = computed(() => {
  const grades = overview.value?.gradeDistribution || {}
  const total = Object.values(grades).reduce((sum, item) => sum + Number(item || 0), 0)
  if (!total) return '0.0%'
  const value = Number(grades.A || 0) + Number(grades.B || 0)
  return `${((value / total) * 100).toFixed(1)}%`
})
const trendSummary = computed(() => {
  const latest = evalTrend.value.at(-1)
  const previous = evalTrend.value.length > 1 ? evalTrend.value.at(-2) : null
  const latestValue = Number(latest?.avgF1 || 0)
  const previousValue = Number(previous?.avgF1 || 0)
  return {
    latest: fmtPct(latestValue),
    delta: ((latestValue - previousValue) * 100).toFixed(1)
  }
})

const actionItems = computed(() => {
  const items = []
  if (Number(overview.value?.pendingAlerts || 0) > 0) {
    items.push({ type: 'warning', text: '存在待处置危急值，建议先进入告警页处理', action: '查看告警', route: '/alerts' })
  }
  if (filteredQualityIssues.value.length > 0) {
    items.push({ type: 'danger', text: `当前有 ${filteredQualityIssues.value.length} 份低质量报告待复核`, action: '定位异常', anchor: 'quality-issues' })
  }
  if (Number(overview.value?.signedReports || 0) < Number(overview.value?.totalReports || 0)) {
    items.push({ type: 'info', text: '报告签发仍有提升空间，可关注医生工作台积压情况', action: '查看病例', route: '/cases' })
  }
  return items
})

const yGrid = [24, 72, 120, 168]
const reportPoints = computed(() => buildChartPoints(reportTrend.value, 'count'))
const evalPoints = computed(() => buildChartPoints(evalTrend.value, 'avgF1', 1))
const reportPolyline = computed(() => reportPoints.value.map(point => `${point.x},${point.y}`).join(' '))
const evalPolyline = computed(() => evalPoints.value.map(point => `${point.x},${point.y}`).join(' '))

function buildChartPoints(rows, key, maxOverride = null) {
  if (!rows?.length) return []
  const values = rows.map(item => Number(item[key] || 0))
  const max = maxOverride || Math.max(...values, 1)
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
    const params = {
      startDate: dateRange.value?.[0] || startDate6m,
      endDate: dateRange.value?.[1],
      groupBy: filters.groupBy
    }
    const [overviewRes, reportRes, evalRes, modelRes, labelRes, issueRes] = await Promise.all([
      getOverview(),
      getReportTrend(params),
      getEvalTrend(params),
      getModelComparison(),
      getPerLabelStats(),
      getQualityIssues({ limit: 50 })
    ])
    overview.value = overviewRes.data || {}
    reportTrend.value = reportRes.data || []
    evalTrend.value = evalRes.data || []
    modelComparison.value = modelRes.data || []
    perLabelStats.value = modelRes.data?.length ? (labelRes.data || []) : (labelRes.data || [])
    qualityIssues.value = issueRes.data?.list || issueRes.data || []
  } catch (error) {
    ElMessage.error('统计数据加载失败')
  }
}

const exportCsv = () => {
  if (!filteredQualityIssues.value.length) {
    ElMessage.warning('当前没有可导出的异常报告')
    return
  }
  const header = ['检查号', '科室', '质控等级', 'F1', '报告状态', '评测时间', '问题摘要']
  const body = filteredQualityIssues.value.map(item => [
    item.examNo || '',
    item.department || '',
    item.qualityGrade || '',
    fmtPct(item.f1Score),
    item.reportStatus || '',
    formatDateTime(item.evalTime),
    (item.problems || []).join(' | ')
  ])
  const csv = ['\ufeff' + header.join(','), ...body.map(row => row.map(cell => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `低质量报告-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

const handleAction = (item) => {
  if (item.route) {
    router.push(item.route)
    return
  }
  if (item.anchor) {
    document.getElementById(item.anchor)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const goToCase = (caseId) => router.push({ path: '/cases', query: { caseId } })
const fmtPct = (value) => `${(Number(value || 0) * 100).toFixed(1)}%`
const formatSeconds = (value) => value ? `${(Number(value) / 1000).toFixed(1)}s` : '0.0s'
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const shortDate = (value) => value ? String(value).slice(5, 10) : '-'
const gradeColor = (grade) => ({ A: '#3DBE7A', B: '#4A9EFF', C: '#E0A44A', D: '#E05C5C' }[grade] || '#6F86A6')
const tagStyle = (grade) => ({ background: `${gradeColor(grade)}22`, color: gradeColor(grade), borderColor: `${gradeColor(grade)}55` })

onMounted(loadData)
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: #0d1420;
  color: #d0dcf0;
}
.page-header,
.kpi-card,
.info-card,
.chart-card {
  background: #0d1420;
  border: 1px solid rgba(111, 134, 166, 0.16);
  box-shadow: none;
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
  color: #eaf2ff;
  font-weight: 600;
}
.page-title { font-size: 18px; }
.page-subtitle { margin-top: 6px; color: rgba(208,220,240,0.65); font-size: 13px; }
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
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
.kpi-label { color: rgba(208,220,240,0.7); font-size: 13px; }
.kpi-value { color: #fff; font-size: 28px; font-weight: 700; margin-top: 4px; }
.kpi-sub { color: rgba(208,220,240,0.55); font-size: 12px; margin-top: 4px; }
.summary-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
  margin-bottom: 16px;
}
.info-card, .chart-card { border-radius: 14px; padding: 16px; }
.summary-list { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; }
.summary-item {
  border-radius: 10px;
  padding: 12px 14px;
  background: rgba(111,134,166,0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.summary-item.info { border-left: 3px solid #4a9eff; }
.summary-item.success { border-left: 3px solid #34a86f; }
.summary-item.warning { border-left: 3px solid #e0a44a; }
.summary-item.danger { border-left: 3px solid #e05c5c; }
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.chart-card.full-span { grid-column: 1 / -1; }
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.card-meta { color: rgba(208,220,240,0.65); font-size: 12px; }
.trend-chart {
  width: 100%;
  height: 220px;
  background: linear-gradient(180deg, rgba(74,158,255,0.06), rgba(74,158,255,0));
  border-radius: 12px;
}
.grid-line { stroke: rgba(111,134,166,0.15); stroke-width: 1; }
.report-line, .eval-line { fill: none; stroke-width: 3; stroke-linecap: round; stroke-linejoin: round; }
.report-line { stroke: #4a9eff; }
.eval-line { stroke: #9b7bff; }
.report-dot { fill: #4a9eff; }
.eval-dot { fill: #9b7bff; }
.axis-labels {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: rgba(208,220,240,0.55);
  margin-top: 10px;
}
.bar-list { display: flex; flex-direction: column; gap: 12px; margin-top: 14px; }
.bar-row {
  display: grid;
  grid-template-columns: 72px 1fr 44px;
  align-items: center;
  gap: 10px;
}
.bar-label, .bar-value { font-size: 12px; color: #d0dcf0; }
.bar-track, .score-track {
  width: 100%;
  height: 10px;
  background: rgba(111,134,166,0.12);
  border-radius: 999px;
  overflow: hidden;
}
.bar-fill, .score-fill { display: block; height: 100%; border-radius: 999px; }
.bar-fill.danger { background: linear-gradient(90deg, #ff8a8a, #e05c5c); }
.score-cell {
  display: grid;
  grid-template-columns: 1fr 60px;
  gap: 10px;
  align-items: center;
}
.score-fill { background: linear-gradient(90deg, #7dcbff, #4a9eff); }
.issue-tag { margin-right: 6px; margin-bottom: 6px; }
:deep(.el-table) {
  --el-table-bg-color: #0f1923;
  --el-table-tr-bg-color: #0f1923;
  --el-table-header-bg-color: rgba(13, 20, 32, 0.95);
  --el-table-border-color: rgba(111, 134, 166, 0.16);
  --el-table-text-color: #d0dcf0;
  --el-table-header-text-color: #9fb3cc;
}
:deep(.el-table th.el-table__cell),
:deep(.el-table tr),
:deep(.el-table td.el-table__cell),
:deep(.el-table .el-table__inner-wrapper) {
  background: #0f1923 !important;
}
:deep(.el-select__wrapper),
:deep(.el-input__wrapper),
:deep(.el-date-editor.el-input__wrapper) {
  background: #0f1923;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.2) inset;
}
:deep(.el-select__placeholder),
:deep(.el-input__inner),
:deep(.el-range-input),
:deep(.el-form-item__label) {
  color: #d0dcf0;
}
@media (max-width: 1200px) {
  .kpi-grid, .summary-grid, .chart-grid { grid-template-columns: 1fr; }
}
</style>

<style scoped>
.page-wrap {
  background: var(--xrag-bg) !important;
  color: var(--xrag-text) !important;
}

.page-header,
.kpi-card,
.info-card,
.chart-card {
  background: var(--xrag-panel) !important;
  border-color: var(--xrag-border) !important;
  box-shadow: var(--xrag-shadow) !important;
}

.page-subtitle,
.card-meta,
.kpi-label,
.kpi-sub,
.axis-labels,
.bar-label,
.bar-value {
  color: var(--xrag-text-soft) !important;
}

.summary-item {
  background: rgba(111, 134, 166, 0.08) !important;
  border: 1px solid rgba(111, 134, 166, 0.12);
}

.trend-chart {
  background: linear-gradient(180deg, rgba(74, 158, 255, 0.08), rgba(74, 158, 255, 0.01)) !important;
  border: 1px solid rgba(111, 134, 166, 0.12);
}

.bar-track,
.score-track {
  background: rgba(111, 134, 166, 0.16) !important;
}

:deep(.el-button--default),
:deep(.el-button.is-plain),
:deep(.el-button.is-text) {
  background: rgba(233, 238, 245, 0.06) !important;
  border-color: rgba(111, 134, 166, 0.28) !important;
  color: var(--xrag-text) !important;
}

:deep(.el-button--primary) {
  background: linear-gradient(180deg, #4A9EFF 0%, #3A86E8 100%) !important;
  border-color: #4A9EFF !important;
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

:deep(.el-table),
:deep(.el-table__inner-wrapper),
:deep(.el-table tr),
:deep(.el-table th.el-table__cell),
:deep(.el-table td.el-table__cell),
:deep(.el-table__body),
:deep(.el-table__header),
:deep(.el-table__empty-block) {
  background: var(--xrag-panel) !important;
  color: var(--xrag-text) !important;
  border-color: var(--xrag-border) !important;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background: rgba(111, 134, 166, 0.12) !important;
}

:deep(.el-tag) {
  border-radius: 8px !important;
}
</style>
