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
          <el-input v-model="query.examNo" clearable placeholder="输入检查号" class="input-w-160" />
        </el-form-item>
        <el-form-item label="患者ID">
          <el-input v-model="query.patientAnonId" clearable placeholder="输入患者ID" class="input-w-160" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="query.gender" clearable placeholder="全部" class="input-w-140">
            <el-option label="男" value="M" />
            <el-option label="女" value="F" />
          </el-select>
        </el-form-item>
        <el-form-item label="部位">
          <el-input v-model="query.bodyPart" clearable placeholder="如：胸部" class="input-w-140" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button class="secondary-btn" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="cases" v-loading="loading" stripe class="perf-table">
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
            <el-tag v-for="tag in splitTags(row.typicalTags)" :key="tag" size="small" class="tag-chip">
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
import { runWhenIdle } from '@/utils/idle'
import { formatDateTime } from '@/utils/format'
import { Star } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const cases = ref([])
const total = ref(0)
const query = reactive({
  page: 1,
  pageSize: 20,
  isTypical: 1,
  examNo: '',
  patientAnonId: '',
  gender: '',
  bodyPart: ''
})

const buildMockCases = () => ([
  {
    caseId: 'MOCK-001',
    examNo: 'TEST-001',
    patientAnonId: 'P-0001',
    gender: 'M',
    age: 35,
    bodyPart: '胸部',
    examTime: '2026-03-14 12:00:00',
    typicalTags: '结节,随访',
    typicalRemark: '右肺下叶结节影'
  },
  {
    caseId: 'MOCK-002',
    examNo: 'TEST-002',
    patientAnonId: 'P-0002',
    gender: 'F',
    age: 48,
    bodyPart: '胸部',
    examTime: '2026-03-13 09:20:00',
    typicalTags: '积液',
    typicalRemark: '右侧胸腔积液'
  },
  {
    caseId: 'MOCK-003',
    examNo: 'TEST-003',
    patientAnonId: 'P-0003',
    gender: 'M',
    age: 62,
    bodyPart: '胸部',
    examTime: '2026-03-12 16:10:00',
    typicalTags: '炎症',
    typicalRemark: '双肺炎症改变'
  }
])

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listCases(query)
    const raw = res.data.list || []
    const noFilter = !query.examNo && !query.patientAnonId && !query.gender && !query.bodyPart
    const base = (import.meta.env.DEV && raw.length === 0)
      ? buildMockCases()
      : raw
    let filtered = base
    const qExam = (query.examNo || '').trim()
    const qPid = (query.patientAnonId || '').trim()
    const qBody = (query.bodyPart || '').trim()
    const qGender = (query.gender || '').toString().toUpperCase()
    const normalizeGender = (g) => {
      if (!g) return ''
      const v = g.toString().toUpperCase()
      if (v === '男' || v === 'M') return 'M'
      if (v === '女' || v === 'F') return 'F'
      return v
    }
    if (qExam) filtered = filtered.filter(c => String(c.examNo || '').includes(qExam))
    if (qPid) filtered = filtered.filter(c => String(c.patientAnonId || '').includes(qPid))
    if (qBody) filtered = filtered.filter(c => String(c.bodyPart || '').includes(qBody))
    if (qGender) filtered = filtered.filter(c => normalizeGender(c.gender) === normalizeGender(qGender))
    cases.value = filtered
    total.value = (qExam || qPid || qBody || qGender || import.meta.env.DEV)
      ? filtered.length
      : (res.data.total || 0)
  } catch {
    cases.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  Object.assign(query, {
    page: 1,
    examNo: '',
    patientAnonId: '',
    gender: '',
    bodyPart: ''
  })
  fetchList()
}

const goDetail = (row) => {
  const cid = row?.caseId
  if (!cid || String(cid).startsWith('MOCK-')) {
    ElMessage.info('当前为测试数据，暂无可查看详情')
    return
  }
  router.push({ path: '/cases', query: { caseId: cid } })
}
const splitTags = (tags) => (tags ? tags.split(',').filter(Boolean) : [])
const formatDate = (val) => formatDateTime(val)

const handleUnmark = async (row) => {
  try {
    await ElMessageBox.confirm(`确认将“${row.examNo}”从典型病例库移除吗？`, '提示', { type: 'warning' })
    await markTypical(row.caseId, { isTypical: 0 })
    ElMessage.success('已取消典型标记')
    fetchList()
  } catch {
    // cancel or error handled globally
  }
}

onMounted(() => {
  runWhenIdle(() => fetchList(), { timeout: 1200 })
})
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
.input-w-160 { width: 160px; }
.input-w-140 { width: 140px; }
.tag-chip { margin: 2px; }

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
  background: rgba(233, 238, 245, 0.08);
  border-color: rgba(111, 134, 166, 0.45);
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.28) inset;
}
:deep(.el-input__wrapper.is-focus),
:deep(.el-select__wrapper.is-focus) {
  border-color: rgba(74, 158, 255, 0.6);
  box-shadow: 0 0 0 1px rgba(74, 158, 255, 0.4) inset;
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

:deep(.el-table__empty-text),
:deep(.el-empty__description) {
  color: rgba(220, 231, 247, 0.75) !important;
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



