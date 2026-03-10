<template>
  <div class="page-wrap">
    <el-card class="full-card">
      <template #header>
        <div class="header-row">
          <span class="page-title"><el-icon><List /></el-icon> 操作日志</span>
          <div class="header-actions">
            <el-button size="small" plain @click="exportLogs" :disabled="!logs.length">导出当前页</el-button>
          </div>
        </div>
      </template>

      <el-form :model="query" inline>
        <el-form-item label="操作类型">
          <el-input v-model="query.operationType" clearable placeholder="如：SIGN_REPORT" style="width: 160px" />
        </el-form-item>
        <el-form-item label="执行结果">
          <el-select v-model="query.resultType" clearable placeholder="全部" style="width: 120px">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="error" />
          </el-select>
        </el-form-item>
        <el-form-item label="失败原因">
          <el-input v-model="query.errorKeyword" clearable placeholder="按错误信息筛选" style="width: 180px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
            @change="onDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table class="admin-table" :data="logs" v-loading="loading" border>
        <el-table-column prop="userName" label="操作用户" width="120" />
        <el-table-column prop="operationType" label="操作类型" width="160">
          <template #default="{ row }">
            <el-tag size="small" :type="row.errorMsg ? 'danger' : 'info'">{{ row.operationType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.errorMsg ? 'danger' : 'success'">{{ row.errorMsg ? '失败' : '成功' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标ID" width="120" />
        <el-table-column prop="apiPath" label="接口路径" show-overflow-tooltip />
        <el-table-column prop="errorMsg" label="失败原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="row.errorMsg ? 'error-text' : 'muted-text'">{{ row.errorMsg || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="客户端IP" width="130" />
        <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" align="right" />
        <el-table-column prop="createdAt" label="操作时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="详情" width="80" align="center">
          <template #default="{ row }">
            <el-button link size="small" @click="showDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        :current-page="query.page"
        :page-size="query.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 50, 100]"
        @current-change="value => { query.page = value; fetchList() }"
        @size-change="value => { query.pageSize = value; query.page = 1; fetchList() }"
      />
    </el-card>
  </div>

  <el-dialog v-model="detailVisible" title="日志详情" width="560px">
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="操作用户">{{ current.userName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="操作类型">{{ current.operationType || '-' }}</el-descriptions-item>
      <el-descriptions-item label="执行结果">{{ current.errorMsg ? '失败' : '成功' }}</el-descriptions-item>
      <el-descriptions-item label="目标ID">{{ current.targetId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="操作详情">{{ current.detail || '-' }}</el-descriptions-item>
      <el-descriptions-item label="接口路径">{{ current.apiPath || '-' }}</el-descriptions-item>
      <el-descriptions-item label="客户端IP">{{ current.clientIp || '-' }}</el-descriptions-item>
      <el-descriptions-item label="耗时">{{ current.elapsedMs ?? '-' }} ms</el-descriptions-item>
      <el-descriptions-item v-if="current.errorMsg" label="错误信息">
        <span class="error-text">{{ current.errorMsg }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="操作时间">{{ formatDate(current.createdAt) }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { List } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { exportLogsFile, listLogs } from '@/api/log'

const loading = ref(false)
const rawLogs = ref([])
const total = ref(0)
const dateRange = ref([])
const detailVisible = ref(false)
const current = ref({})
const query = reactive({
  page: 1,
  pageSize: 20,
  operationType: '',
  startTime: '',
  endTime: '',
  resultType: '',
  errorKeyword: ''
})

const logs = computed(() => rawLogs.value.filter(item => {
  const matchResult = !query.resultType || (query.resultType === 'error' ? !!item.errorMsg : !item.errorMsg)
  const matchError = !query.errorKeyword || String(item.errorMsg || '').toLowerCase().includes(query.errorKeyword.toLowerCase())
  return matchResult && matchError
}))

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listLogs({
      page: query.page,
      pageSize: query.pageSize,
      operationType: query.operationType,
      startTime: query.startTime,
      endTime: query.endTime,
      resultType: query.resultType,
      errorKeyword: query.errorKeyword
    })
    rawLogs.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  Object.assign(query, { page: 1, pageSize: 20, operationType: '', startTime: '', endTime: '', resultType: '', errorKeyword: '' })
  dateRange.value = []
  fetchList()
}

const onDateChange = (value) => {
  query.startTime = value ? `${value[0]}T00:00:00` : ''
  query.endTime = value ? `${value[1]}T23:59:59` : ''
}

const showDetail = (row) => {
  current.value = row
  detailVisible.value = true
}

const formatDate = (value) => (value ? String(value).replace('T', ' ').substring(0, 19) : '-')

const exportLogs = () => {
  if (!logs.value.length) {
    ElMessage.warning('当前页没有可导出的日志')
    return
  }
  const rows = logs.value.map(item => ({
    '操作用户': item.userName || '',
    '操作类型': item.operationType || '',
    '执行结果': item.errorMsg ? '失败' : '成功',
    '失败原因': item.errorMsg || '',
    '目标ID': item.targetId || '',
    '接口路径': item.apiPath || '',
    '客户端IP': item.clientIp || '',
    '耗时(ms)': item.elapsedMs ?? '',
    '操作时间': formatDate(item.createdAt)
  }))
  const header = Object.keys(rows[0]).join(',')
  const body = rows.map(row => Object.values(row).map(v => `"${String(v ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([`\ufeff${header}\n${body}`], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `操作日志-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(fetchList)
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: #0d1420;
  color: #d0dcf0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.full-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(111, 134, 166, 0.16);
  background: #0d1420;
  box-shadow: none;
}

:deep(.full-card .el-card__header) {
  border-bottom: 1px solid rgba(111, 134, 166, 0.16);
  background: #0d1420;
}

:deep(.full-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 20px 20px;
  background: #0d1420;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #e8f0ff;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
  flex-shrink: 0;
}

.error-text {
  color: #f56c6c;
}

.muted-text {
  color: #8ca0b3;
}

:deep(.admin-table) {
  --el-table-bg-color: #0f1923;
  --el-table-tr-bg-color: #0f1923;
  --el-table-row-striped-bg-color: #0f1923;
  --el-table-row-hover-bg-color: rgba(111, 134, 166, 0.12);
  --el-table-header-bg-color: rgba(13, 20, 32, 0.95);
  --el-table-border-color: rgba(111, 134, 166, 0.16);
  --el-table-text-color: #d0dcf0;
  --el-table-header-text-color: #9fb3cc;
}

:deep(.admin-table .el-table__inner-wrapper),
:deep(.admin-table th.el-table__cell),
:deep(.admin-table tr),
:deep(.admin-table td.el-table__cell) {
  background: #0f1923 !important;
}

:deep(.el-pagination .btn-prev),
:deep(.el-pagination .btn-next),
:deep(.el-pagination .el-pager li) {
  background: #0f1923 !important;
  color: #d0dcf0 !important;
  border: 1px solid rgba(111, 134, 166, 0.2) !important;
}

:deep(.el-pagination .el-pager li.is-active) {
  background: rgba(64, 158, 255, 0.35) !important;
  color: #fff !important;
  border-color: rgba(64, 158, 255, 0.5) !important;
}

:deep(.admin-table .el-tag) {
  background: rgba(111, 134, 166, 0.2) !important;
  border-color: rgba(111, 134, 166, 0.35) !important;
  color: #d0dcf0 !important;
}

:deep(.admin-table .el-tag.el-tag--danger) {
  background: rgba(245, 108, 108, 0.2) !important;
  border-color: rgba(245, 108, 108, 0.4) !important;
  color: #f89898 !important;
}

:deep(.admin-table .el-tag.el-tag--success) {
  background: rgba(52, 168, 111, 0.2) !important;
  border-color: rgba(52, 168, 111, 0.4) !important;
  color: #7ad6a5 !important;
}

:deep(.admin-table .el-tag.el-tag--info) {
  background: rgba(111, 134, 166, 0.25) !important;
  border-color: rgba(111, 134, 166, 0.4) !important;
  color: #9fb3cc !important;
}

:deep(.el-form-item__label),
:deep(.el-dialog__title),
:deep(.el-descriptions__label),
:deep(.el-descriptions__content),
:deep(.el-pagination) {
  color: #d0dcf0;
}

:deep(.el-input__wrapper),
:deep(.el-date-editor.el-input__wrapper),
:deep(.el-select__wrapper) {
  background: #0f1923;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.2) inset;
}

:deep(.el-input__inner),
:deep(.el-range-input),
:deep(.el-select__placeholder) {
  color: #d0dcf0;
}

:deep(.el-dialog),
:deep(.el-descriptions__body),
:deep(.el-descriptions__table) {
  background: #0f1923;
  color: #d0dcf0;
}
</style>

<style scoped>
.page-wrap {
  background: var(--xrag-bg) !important;
  color: var(--xrag-text) !important;
}

.full-card {
  background: var(--xrag-bg) !important;
  border-color: var(--xrag-border) !important;
  box-shadow: var(--xrag-shadow) !important;
}

:deep(.full-card .el-card__header),
:deep(.full-card .el-card__body) {
  background: var(--xrag-bg) !important;
  border-color: var(--xrag-border) !important;
  color: var(--xrag-text) !important;
}

:deep(.el-form-item__label),
:deep(.el-dialog__title),
:deep(.el-empty__description),
:deep(.el-pagination__total),
:deep(.el-pagination__jump),
:deep(.el-table .cell),
:deep(.el-descriptions__label),
:deep(.el-descriptions__content) {
  color: var(--xrag-text) !important;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper),
:deep(.el-date-editor.el-input__wrapper) {
  background: rgba(233, 238, 245, 0.05) !important;
  border-color: var(--xrag-border-strong) !important;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.18) inset !important;
}

:deep(.el-input__inner),
:deep(.el-textarea__inner),
:deep(.el-select__placeholder),
:deep(.el-select__selected-item),
:deep(.el-range-input),
:deep(.el-switch__label) {
  color: var(--xrag-text) !important;
}

:deep(.el-button--default),
:deep(.el-button.is-link),
:deep(.el-button.is-plain) {
  background: rgba(233, 238, 245, 0.06) !important;
  border-color: rgba(111, 134, 166, 0.28) !important;
  color: var(--xrag-text) !important;
}

:deep(.el-button--default:hover),
:deep(.el-button.is-link:hover),
:deep(.el-button.is-plain:hover) {
  background: rgba(74, 158, 255, 0.10) !important;
  border-color: rgba(74, 158, 255, 0.28) !important;
  color: #f4f8ff !important;
}

:deep(.el-button--primary) {
  background: linear-gradient(180deg, #4A9EFF 0%, #3A86E8 100%) !important;
  border-color: #4A9EFF !important;
  color: #fff !important;
}

:deep(.el-table),
:deep(.el-table__inner-wrapper),
:deep(.el-table tr),
:deep(.el-table th.el-table__cell),
:deep(.el-table td.el-table__cell),
:deep(.el-table__body),
:deep(.el-table__header),
:deep(.el-table__empty-block),
:deep(.el-descriptions__body),
:deep(.el-descriptions__table) {
  background: var(--xrag-panel) !important;
  color: var(--xrag-text) !important;
  border-color: var(--xrag-border) !important;
}

:deep(.el-table--border::before),
:deep(.el-table--border::after),
:deep(.el-table__inner-wrapper::before),
:deep(.el-table td.el-table__cell),
:deep(.el-table th.el-table__cell),
:deep(.el-descriptions__cell) {
  border-color: rgba(111, 134, 166, 0.24) !important;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background: rgba(111, 134, 166, 0.12) !important;
}

:deep(.el-tag),
:deep(.el-badge__content) {
  background: rgba(111, 134, 166, 0.2) !important;
  color: var(--xrag-text) !important;
  border-color: rgba(111, 134, 166, 0.35) !important;
}

:deep(.el-dialog),
:deep(.el-dialog__header),
:deep(.el-dialog__body),
:deep(.el-dialog__footer) {
  background: var(--xrag-panel) !important;
  color: var(--xrag-text) !important;
  border-color: var(--xrag-border) !important;
}

:deep(.el-pagination .btn-prev),
:deep(.el-pagination .btn-next),
:deep(.el-pagination .el-pager li) {
  background: var(--xrag-panel) !important;
  color: var(--xrag-text) !important;
  border: 1px solid rgba(111, 134, 166, 0.2) !important;
}

:deep(.el-pagination .el-pager li.is-active) {
  background: rgba(64, 158, 255, 0.35) !important;
  color: #fff !important;
  border-color: rgba(64, 158, 255, 0.5) !important;
}
</style>
