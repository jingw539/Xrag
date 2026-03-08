<template>
  <div class="page-wrap">
    <div class="page-header">
      <span class="page-title"><el-icon><DataAnalysis /></el-icon>质控统计</span>
      <div class="header-actions">
        <el-button size="small" plain @click="loadData"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button size="small" plain @click="exportCsv"><el-icon><Download /></el-icon>导出</el-button>
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

    <div v-if="aiInsights.length" class="info-card">
      <div class="section-title"><el-icon><MagicStick /></el-icon>AI 智能洞察</div>
      <div class="insight-list">
        <div v-for="(item, index) in aiInsights" :key="index" :class="['insight-item', item.type]">
          {{ item.text }}
        </div>
      </div>
    </div>

    <div v-if="actionItems.length" class="info-card warning-card">
      <div class="section-title"><el-icon><Bell /></el-icon>待处理事项</div>
      <div class="action-list">
        <div v-for="(item, index) in actionItems" :key="index" class="action-item">
          <span>{{ item.text }}</span>
          <el-button size="small" :type="item.type === 'danger' ? 'danger' : 'primary'" plain @click="handleAction(item)">
            {{ item.action }}
          </el-button>
        </div>
      </div>
    </div>

    <div class="chart-grid">
      <div class="chart-card">
        <div class="section-title"><el-icon><TrendCharts /></el-icon>报告生成趋势</div>
        <el-table :data="reportTrend" size="small" empty-text="暂无数据">
          <el-table-column prop="date" label="月份" min-width="120">
            <template #default="{ row }">{{ formatMonth(row.date) }}</template>
          </el-table-column>
          <el-table-column prop="count" label="报告数" min-width="100" align="center" />
        </el-table>
      </div>

      <div class="chart-card">
        <div class="section-title"><el-icon><PieChart /></el-icon>报告质量分布</div>
        <div v-if="gradeRows.length" class="stat-list">
          <div v-for="row in gradeRows" :key="row.grade" class="stat-row">
            <span class="stat-name">{{ row.grade }}级</span>
            <div class="stat-bar"><span class="stat-fill" :style="{ width: `${row.percent}%`, background: gradeColor(row.grade) }" /></div>
            <span class="stat-value">{{ row.count }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>

      <div class="chart-card">
        <div class="section-title"><el-icon><DataLine /></el-icon>F1 Score 趋势</div>
        <el-table :data="evalTrend" size="small" empty-text="暂无数据">
          <el-table-column prop="date" label="月份" min-width="120">
            <template #default="{ row }">{{ formatMonth(row.date) }}</template>
          </el-table-column>
          <el-table-column prop="avgF1" label="平均F1" min-width="120" align="center">
            <template #default="{ row }">{{ fmtPct(row.avgF1) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="chart-card">
        <div class="section-title"><el-icon><Warning /></el-icon>危急值预警统计</div>
        <div v-if="alertRows.length" class="stat-list">
          <div v-for="row in alertRows" :key="row.type" class="stat-row">
            <span class="stat-name">{{ row.type }}</span>
            <div class="stat-bar"><span class="stat-fill red" :style="{ width: `${row.percent}%` }" /></div>
            <span class="stat-value">{{ row.count }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无预警数据" :image-size="60" />
      </div>

      <div class="chart-card full-span">
        <div class="section-title"><el-icon><DataBoard /></el-icon>14类病理标签性能</div>
        <el-table :data="perLabelStats" size="small" empty-text="暂无数据">
          <el-table-column prop="label" label="标签" min-width="180" />
          <el-table-column prop="f1" label="F1" width="100" align="center">
            <template #default="{ row }">{{ fmtPct(row.f1) }}</template>
          </el-table-column>
          <el-table-column prop="precision" label="精确率" width="100" align="center">
            <template #default="{ row }">{{ fmtPct(row.precision) }}</template>
          </el-table-column>
          <el-table-column prop="recall" label="召回率" width="100" align="center">
            <template #default="{ row }">{{ fmtPct(row.recall) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="chart-card full-span">
        <div class="section-title"><el-icon><Cpu /></el-icon>模型对比</div>
        <el-table :data="modelComparison" size="small" empty-text="暂无数据">
          <el-table-column prop="modelName" label="模型" min-width="160" />
          <el-table-column prop="avgF1" label="平均F1" width="120" align="center">
            <template #default="{ row }">{{ fmtPct(row.avgF1) }}</template>
          </el-table-column>
          <el-table-column prop="evalCount" label="评测次数" width="120" align="center" />
        </el-table>
      </div>

      <div class="chart-card full-span" v-if="qualityIssues.length">
        <div class="section-title"><el-icon><Warning /></el-icon>质量异常报告</div>
        <el-table :data="qualityIssues" size="small" empty-text="暂无数据">
          <el-table-column prop="examNo" label="检查号" min-width="140">
            <template #default="{ row }">
              <el-link type="primary" @click="goToCase(row.caseId)" :underline="false">{{ row.examNo }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="department" label="科室" width="100" />
          <el-table-column prop="qualityGrade" label="等级" width="80" align="center" />
          <el-table-column prop="f1Score" label="F1" width="100" align="center">
            <template #default="{ row }">{{ fmtPct(row.f1Score) }}</template>
          </el-table-column>
          <el-table-column label="问题描述" min-width="220">
            <template #default="{ row }">
              <el-tag v-for="(problem, index) in row.problems || []" :key="index" size="small" style="margin:2px">
                {{ problem }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reportStatus" label="状态" width="100" align="center">
            <template #default="{ row }">{{ row.reportStatus === 'SIGNED' ? '已签发' : '未签发' }}</template>
          </el-table-column>
          <el-table-column prop="evalTime" label="评测时间" min-width="140">
            <template #default="{ row }">{{ formatDateTime(row.evalTime) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis,
  Download,
  Refresh,
  TrendCharts,
  PieChart,
  DataLine,
  DataBoard,
  Warning,
  Cpu,
  Document,
  Check,
  Timer,
  Bell,
  Histogram,
  Odometer,
  MagicStick
} from '@element-plus/icons-vue'
import { getOverview, getReportTrend, getEvalTrend, getModelComparison, getPerLabelStats, getQualityIssues } from '@/api/statistics'
import { useRouter } from 'vue-router'

const router = useRouter()
const overview = ref({})
const reportTrend = ref([])
const evalTrend = ref([])
const modelComparison = ref([])
const perLabelStats = ref([])
const qualityIssues = ref([])

const SIX_MONTHS_AGO = new Date()
SIX_MONTHS_AGO.setMonth(SIX_MONTHS_AGO.getMonth() - 6)
const startDate6m = SIX_MONTHS_AGO.toISOString().slice(0, 10)

const statCards = computed(() => {
  const ov = overview.value || {}
  const adoption = ov.adoptionRate != null ? `${(Number(ov.adoptionRate) * 100).toFixed(1)}%` : '0%'
  const genTime = ov.avgGenTimeMs != null ? `${(Number(ov.avgGenTimeMs) / 1000).toFixed(1)}s` : '0s'
  const f1 = ov.avgF1Score != null ? `${(Number(ov.avgF1Score) * 100).toFixed(1)}%` : '0%'
  return [
    { label: '总病例数', value: ov.totalCases || 0, icon: Document, color: '#3A86E8' },
    { label: '已签发报告', value: ov.signedReports || 0, icon: Check, color: '#34A86F' },
    { label: '平均 F1', value: f1, icon: Histogram, color: '#6E56CF' },
    { label: '待处理预警', value: ov.pendingAlerts || 0, icon: Bell, color: '#D45C5C' },
    { label: 'AI采纳率', value: adoption, sub: '质量 A/B 级占比', icon: Odometer, color: '#C78A2C' },
    { label: '平均生成时长', value: genTime, sub: 'AI生成到签发', icon: Timer, color: '#2A9D8F' }
  ]
})

const gradeRows = computed(() => {
  const grades = overview.value?.gradeDistribution || {}
  const total = Object.values(grades).reduce((sum, count) => sum + Number(count || 0), 0) || 1
  return Object.entries(grades).map(([grade, count]) => ({
    grade,
    count: Number(count || 0),
    percent: Math.round((Number(count || 0) / total) * 100)
  }))
})

const alertRows = computed(() => {
  const stats = overview.value?.alertTypeStats || {}
  const total = Object.values(stats).reduce((sum, count) => sum + Number(count || 0), 0) || 1
  return Object.entries(stats).map(([type, count]) => ({
    type,
    count: Number(count || 0),
    percent: Math.round((Number(count || 0) / total) * 100)
  }))
})

const aiInsights = computed(() => {
  const ov = overview.value || {}
  const insights = []
  const total = Number(ov.totalCases || 0)
  const signed = Number(ov.signedReports || 0)
  const pendingAlerts = Number(ov.pendingAlerts || 0)
  const signRate = total > 0 ? signed / total : 0

  if (signRate < 0.3) {
    insights.push({ type: 'warning', text: `签发率仅 ${(signRate * 100).toFixed(0)}%，当前 ${signed}/${total}，建议加快审核进度。` })
  } else {
    insights.push({ type: 'success', text: `累计处理 ${total} 份病例，已签发 ${signed} 份，签发率 ${(signRate * 100).toFixed(0)}%。` })
  }

  if (pendingAlerts > 0) {
    insights.push({ type: 'danger', text: `当前仍有 ${pendingAlerts} 条危急值预警待处理，需要优先关注。` })
  }

  const avgF1 = Number(ov.avgF1Score || 0)
  if (avgF1 > 0) {
    insights.push({ type: avgF1 >= 0.8 ? 'success' : 'info', text: `当前平均 F1 为 ${(avgF1 * 100).toFixed(1)}%，可作为模型质量参考。` })
  }

  return insights
})

const actionItems = computed(() => {
  const items = []
  if (Number(overview.value?.pendingAlerts || 0) > 0) {
    items.push({ type: 'danger', text: '存在未处理危急值预警', action: '查看预警', route: '/alerts' })
  }
  if (qualityIssues.value.length > 0) {
    items.push({ type: 'primary', text: `存在 ${qualityIssues.value.length} 份质量异常报告`, action: '查看异常', anchor: 'quality-issues' })
  }
  return items
})

const loadData = async () => {
  try {
    const [overviewRes, reportRes, evalRes, modelRes, labelRes, issueRes] = await Promise.all([
      getOverview(),
      getReportTrend({ startDate: startDate6m }),
      getEvalTrend({ startDate: startDate6m }),
      getModelComparison(),
      getPerLabelStats(),
      getQualityIssues({ page: 1, pageSize: 50 })
    ])

    overview.value = overviewRes.data || {}
    reportTrend.value = reportRes.data || []
    evalTrend.value = evalRes.data || []
    modelComparison.value = modelRes.data || []
    perLabelStats.value = labelRes.data || []
    qualityIssues.value = issueRes.data?.list || issueRes.data || []
  } catch (error) {
    ElMessage.error('统计数据加载失败')
  }
}

const exportCsv = () => {
  ElMessage.info('导出功能后续可接入真实报表导出')
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

const goToCase = (caseId) => {
  router.push({ path: '/cases', query: { caseId } })
}

const fmtPct = (value) => `${(Number(value || 0) * 100).toFixed(1)}%`
const formatMonth = (date) => (date ? String(date).slice(0, 7) : '-')
const formatDateTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '-')
const gradeColor = (grade) => ({ A: '#3DBE7A', B: '#4A9EFF', C: '#E0A44A', D: '#E05C5C' }[grade] || '#6F86A6')

onMounted(loadData)
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: #0E1621;
  color: #D0DCF0;
}

.page-header,
.info-card,
.chart-card,
.kpi-card {
  background: #1A2535;
  border: 1px solid rgba(111, 134, 166, 0.24);
  box-shadow: none;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  border-radius: 14px;
  margin-bottom: 16px;
}

.page-title,
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #EAF2FF;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: 14px;
}

.kpi-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.kpi-label,
.kpi-sub {
  color: rgba(208, 220, 240, 0.72);
}

.kpi-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  color: #EAF2FF;
}

.info-card,
.chart-card {
  border-radius: 14px;
  padding: 16px;
}

.info-card {
  margin-bottom: 16px;
}

.warning-card {
  border-color: rgba(224, 164, 74, 0.28);
}

.insight-list,
.action-list,
.stat-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.insight-item,
.action-item,
.stat-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(233, 238, 245, 0.05);
  border: 1px solid rgba(111, 134, 166, 0.2);
}

.insight-item.success { color: #BDEDCF; }
.insight-item.warning { color: #FFE1AB; }
.insight-item.danger { color: #FFC5C5; }
.insight-item.info { color: #B9D7FF; }

.action-item {
  justify-content: space-between;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.full-span {
  grid-column: 1 / -1;
}

.stat-name {
  width: 100px;
  color: #D0DCF0;
}

.stat-bar {
  flex: 1;
  height: 8px;
  background: rgba(233, 238, 245, 0.08);
  border-radius: 999px;
  overflow: hidden;
}

.stat-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #4A9EFF;
}

.stat-fill.red {
  background: #E05C5C;
}

.stat-value {
  width: 36px;
  text-align: right;
  color: #EAF2FF;
}

:deep(.el-table),
:deep(.el-table__inner-wrapper),
:deep(.el-table tr),
:deep(.el-table th.el-table__cell),
:deep(.el-table td.el-table__cell),
:deep(.el-table__body),
:deep(.el-table__header),
:deep(.el-table__empty-block) {
  background: #1A2535 !important;
  color: #D0DCF0 !important;
  border-color: rgba(111, 134, 166, 0.24) !important;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: rgba(233, 238, 245, 0.04) !important;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background: rgba(74, 158, 255, 0.08) !important;
}

:deep(.el-button),
:deep(.el-button span),
:deep(.el-button .el-icon),
:deep(.el-tag),
:deep(.el-link),
:deep(.el-link span) {
  color: #DCE7F7 !important;
}

:deep(.el-button--default),
:deep(.el-button.is-plain),
:deep(.el-button.is-link) {
  background: rgba(233, 238, 245, 0.06) !important;
  border-color: rgba(111, 134, 166, 0.28) !important;
}

:deep(.el-button--primary) {
  background: linear-gradient(180deg, #4A9EFF 0%, #3A86E8 100%) !important;
  border-color: #4A9EFF !important;
  color: #fff !important;
}

:deep(.el-button--danger) {
  background: rgba(224, 92, 92, 0.16) !important;
  border-color: rgba(224, 92, 92, 0.28) !important;
  color: #FFC5C5 !important;
}

:deep(.el-tag),
:deep(.el-badge__content) {
  background: rgba(233, 238, 245, 0.08) !important;
  border-color: rgba(111, 134, 166, 0.24) !important;
}

:deep(.el-empty__description p) {
  color: #D0DCF0 !important;
}

@media (max-width: 1200px) {
  .kpi-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
