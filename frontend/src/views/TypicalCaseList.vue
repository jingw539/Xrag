<template>
  <div class="page-wrap">
    <el-card class="full-card">
      <template #header>
        <div class="card-header">
          <span class="page-title"><el-icon><Star /></el-icon>典型病例库</span>
          <el-text type="info">共 {{ total }} 条典型病例，用作 RAG 检索参考</el-text>
        </div>
      </template>

      <el-form :model="query" inline class="toolbar-form">
        <el-form-item label="检查号">
          <el-input v-model="query.examNo" clearable placeholder="输入检查号" style="width:160px" />
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="query.department" clearable placeholder="如：影像科" style="width:140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button class="secondary-btn" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="cases" v-loading="loading" border stripe>
        <el-table-column prop="examNo" label="检查号" width="150" />
        <el-table-column prop="patientAnonId" label="患者匿名ID" width="140" />
        <el-table-column prop="gender" label="性别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.gender === 'M' ? 'primary' : 'danger'" size="small">
              {{ row.gender === 'M' ? '男' : '女' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="80" align="center" />
        <el-table-column prop="bodyPart" label="部位" width="100" />
        <el-table-column prop="examTime" label="检查时间" width="170">
          <template #default="{ row }">{{ formatDate(row.examTime) }}</template>
        </el-table-column>
        <el-table-column prop="typicalTags" label="典型标签" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="tag in splitTags(row.typicalTags)" :key="tag" size="small" style="margin:2px">
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="typicalRemark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="goDetail(row)">详情</el-button>
            <el-divider direction="vertical" />
            <el-button size="small" type="warning" link @click="handleUnmark(row)">取消标记</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pagination" :current-page="query.page" :page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="v => { query.page = v; fetchList() }" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCases, markTypical } from '@/api/case'

const router = useRouter()
const loading = ref(false)
const cases = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 20, isTypical: 1, examNo: '', department: '' })

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listCases(query)
    cases.value = res.data.list || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  Object.assign(query, { page: 1, examNo: '', department: '' })
  fetchList()
}

const goDetail = (row) => router.push({ path: '/cases', query: { caseId: row.caseId } })
const splitTags = (tags) => (tags ? tags.split(',').filter(Boolean) : [])
const formatDate = (val) => (val ? val.replace('T', ' ').substring(0, 16) : '-')

const handleUnmark = async (row) => {
  try {
    await ElMessageBox.confirm(`确认将“${row.examNo}”从典型病例库移除吗？`, '提示', { type: 'warning' })
  } catch (_) {
    return
  }
  await markTypical(row.caseId, { isTypical: 0 })
  ElMessage.success('已取消典型标记')
  fetchList()
}

onMounted(fetchList)
</script>
<style scoped>
.page-wrap {
  padding: 20px;
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #0d1420;
}

.full-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #0d1420;
  border: 1px solid rgba(111, 134, 166, 0.16);
  box-shadow: none;
}

:deep(.full-card .el-card__header),
:deep(.full-card .el-card__body) {
  background: #0d1420;
  border-color: rgba(111, 134, 166, 0.16);
  color: #D0DCF0;
}

:deep(.full-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 20px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #EAF2FF;
}

.toolbar-form {
  margin-bottom: 8px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
  flex-shrink: 0;
}

:deep(.el-form-item__label),
:deep(.el-text),
:deep(.el-table .cell),
:deep(.el-pagination__total),
:deep(.el-pagination__jump) {
  color: #D0DCF0;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper) {
  background: rgba(233, 238, 245, 0.05);
  border-color: rgba(111, 134, 166, 0.28);
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.18) inset;
}

:deep(.el-input__inner),
:deep(.el-textarea__inner),
:deep(.el-select__placeholder),
:deep(.el-select__selected-item) {
  color: #DCE7F7;
}

:deep(.el-table),
:deep(.el-table__inner-wrapper),
:deep(.el-table tr),
:deep(.el-table th.el-table__cell),
:deep(.el-table td.el-table__cell),
:deep(.el-table__body),
:deep(.el-table__header),
:deep(.el-table__empty-block) {
  background: #0f1923 !important;
  color: #D0DCF0;
  border-color: rgba(111, 134, 166, 0.16);
}

:deep(.el-table--border::before),
:deep(.el-table--border::after),
:deep(.el-table__inner-wrapper::before),
:deep(.el-table td.el-table__cell),
:deep(.el-table th.el-table__cell) {
  border-color: rgba(111, 134, 166, 0.24);
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: #0f1923 !important;
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background: rgba(111, 134, 166, 0.12) !important;
}

:deep(.el-button),
:deep(.el-button span),
:deep(.el-button .el-icon),
:deep(.el-link),
:deep(.el-link span) {
  color: #DCE7F7;
}

:deep(.el-button--default),
:deep(.el-button.is-link) {
  background: rgba(233, 238, 245, 0.06);
  border-color: rgba(111, 134, 166, 0.3);
}

:deep(.el-button--primary) {
  background: linear-gradient(180deg, #4A9EFF 0%, #3A86E8 100%);
  border-color: #4A9EFF;
  color: #fff;
}

:deep(.el-button--warning),
:deep(.el-button--warning.is-link) {
  background: rgba(224, 164, 74, 0.14);
  border-color: rgba(224, 164, 74, 0.34);
  color: #FFD79A;
}

:deep(.el-tag),
:deep(.el-badge__content) {
  background: rgba(111, 134, 166, 0.2) !important;
  color: #d0dcf0 !important;
  border-color: rgba(111, 134, 166, 0.35) !important;
}
:deep(.el-tag.el-tag--primary) {
  background: rgba(64, 158, 255, 0.25) !important;
  border-color: rgba(64, 158, 255, 0.4) !important;
  color: #7eb8ff !important;
}
:deep(.el-tag.el-tag--danger) {
  background: rgba(245, 108, 108, 0.2) !important;
  border-color: rgba(245, 108, 108, 0.4) !important;
  color: #f89898 !important;
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

/* ===== Final button cleanup ===== */
:deep(.el-button--default),
:deep(.el-button.is-link),
:deep(.el-button--warning.is-link),
:deep(.el-button--primary.is-link) {
  background: rgba(233, 238, 245, 0.06) !important;
  border-color: rgba(111, 134, 166, 0.28) !important;
  color: #DCE7F7 !important;
}

:deep(.el-button:hover),
:deep(.el-button.is-link:hover) {
  background: rgba(74, 158, 255, 0.10) !important;
  border-color: rgba(74, 158, 255, 0.28) !important;
}

.secondary-btn {
  background: rgba(233, 238, 245, 0.06) !important;
  border: 1px solid rgba(111, 134, 166, 0.28) !important;
  color: #DCE7F7 !important;
  box-shadow: none !important;
}

.secondary-btn:hover {
  background: rgba(74, 158, 255, 0.10) !important;
  border-color: rgba(74, 158, 255, 0.28) !important;
  color: #F4F8FF !important;
}

/* ===== Radius alignment ===== */
.full-card,
:deep(.full-card.el-card),
:deep(.el-card__header),
:deep(.el-card__body),
:deep(.el-table),
:deep(.el-table__inner-wrapper),
:deep(.el-input__wrapper),
:deep(.el-select__wrapper),
:deep(.el-textarea__inner),
:deep(.el-button),
:deep(.el-tag),
:deep(.el-dialog),
:deep(.el-dialog__header),
:deep(.el-dialog__body),
:deep(.el-dialog__footer) {
  border-radius: 14px !important;
}

:deep(.el-button),
.secondary-btn,
.suggest-chip {
  border-radius: 10px !important;
}

:deep(.el-tag),
:deep(.el-badge__content) {
  border-radius: 8px !important;
}

:deep(.el-table th.el-table__cell:first-child) {
  border-top-left-radius: 12px !important;
}

:deep(.el-table th.el-table__cell:last-child) {
  border-top-right-radius: 12px !important;
}
</style>




