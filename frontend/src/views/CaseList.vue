<template>
  <div class="page-wrap">
    <el-card class="page-card full-card">
      <template #header>
        <div class="card-header">
          <span class="page-title"><el-icon><Folder /></el-icon> 病例管理</span>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon> 新建病例
          </el-button>
        </div>
      </template>

      <el-form :model="query" inline class="search-form">
        <el-form-item label="检查号">
          <el-input v-model="query.examNo" placeholder="输入检查号" clearable style="width:150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.reportStatus" clearable placeholder="全部" style="width:120px">
            <el-option label="待生成" value="NONE" />
            <el-option label="AI草稿" value="AI_DRAFT" />
            <el-option label="编辑中" value="EDITING" />
            <el-option label="已签发" value="SIGNED" />
          </el-select>
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="query.department" placeholder="如：影像科" clearable style="width:120px" />
        </el-form-item>
        <el-form-item label="检查时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:240px"
            @change="onDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="cases" v-loading="loading" border stripe row-key="caseId" @row-click="goDetail" style="cursor:pointer">
        <el-table-column prop="examNo" label="检查号" width="130" />
        <el-table-column prop="patientAnonId" label="患者ID" width="130" />
        <el-table-column prop="gender" label="性别" width="60" align="center">
          <template #default="{ row }">
            <el-tag :type="row.gender === 'M' ? 'primary' : row.gender === 'F' ? 'danger' : 'info'" size="small">
              {{ row.gender === 'M' ? '男' : row.gender === 'F' ? '女' : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="70" align="center" />
        <el-table-column prop="bodyPart" label="检查部位" width="100" />
        <el-table-column prop="department" label="科室" min-width="100" />
        <el-table-column prop="examTime" label="检查时间" width="160">
          <template #default="{ row }">{{ formatDate(row.examTime) }}</template>
        </el-table-column>
        <el-table-column prop="reportStatus" label="报告状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.reportStatus)" size="small">
              {{ statusLabel(row.reportStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isTypical" label="典型病例" width="90" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.isTypical" color="#f5a623"><Star /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click.stop="goDetail(row)">详情</el-button>
            <el-button size="small" type="danger" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        :current-page="query.page"
        :page-size="query.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        @current-change="v => { query.page = v; fetchList() }"
        @size-change="v => { query.pageSize = v; query.page = 1; fetchList() }"
      />
    </el-card>
  </div>

  <el-dialog v-model="showCreateDialog" title="新建病例" width="520px">
    <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="80px">
      <el-form-item label="检查号" prop="examNo">
        <el-input v-model="createForm.examNo" placeholder="如：CXR20240301001" />
      </el-form-item>
      <el-form-item label="患者ID" prop="patientAnonId">
        <el-input v-model="createForm.patientAnonId" placeholder="匿名患者标识" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="性别">
            <el-select v-model="createForm.gender" style="width:100%">
              <el-option label="男" value="M" />
              <el-option label="女" value="F" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="年龄">
            <el-input-number v-model="createForm.age" :min="0" :max="120" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="检查部位">
        <el-input v-model="createForm.bodyPart" placeholder="如：胸部" />
      </el-form-item>
      <el-form-item label="科室">
        <el-input v-model="createForm.department" />
      </el-form-item>
      <el-form-item label="检查时间" prop="examTime">
        <el-date-picker v-model="createForm.examTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showCreateDialog = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCases, createCase, deleteCase } from '@/api/case'

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const cases = ref([])
const total = ref(0)
const showCreateDialog = ref(false)
const createFormRef = ref(null)
const dateRange = ref([])

const query = reactive({ page: 1, pageSize: 20, examNo: '', reportStatus: '', department: '', startTime: '', endTime: '' })
const createForm = reactive({ examNo: '', patientAnonId: '', gender: 'M', age: null, bodyPart: '胸部', department: '', examTime: '' })
const createRules = {
  examNo: [{ required: true, message: '请输入检查号', trigger: 'blur' }],
  patientAnonId: [{ required: true, message: '请输入患者ID', trigger: 'blur' }],
  examTime: [{ required: true, message: '请选择检查时间', trigger: 'change' }]
}

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
  Object.assign(query, { page: 1, examNo: '', reportStatus: '', department: '', startTime: '', endTime: '' })
  dateRange.value = []
  fetchList()
}

const onDateChange = (val) => {
  query.startTime = val ? val[0] : ''
  query.endTime = val ? val[1] : ''
}

const goDetail = (row) => router.push(`/cases/${row.caseId}`)

const handleCreate = async () => {
  await createFormRef.value.validate()
  creating.value = true
  try {
    const res = await createCase(createForm)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    router.push(`/cases/${res.data}`)
  } finally {
    creating.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除病例“${row.examNo}”吗？此操作不可恢复。`, '警告', { type: 'warning' })
  await deleteCase(row.caseId)
  ElMessage.success('删除成功')
  fetchList()
}

const formatDate = (val) => val ? val.replace('T', ' ').substring(0, 16) : '-'
const statusLabel = (s) => ({ NONE: '待生成', AI_DRAFT: 'AI草稿', EDITING: '编辑中', SIGNED: '已签发' }[s] || s || '-')
const statusType = (s) => ({ NONE: 'info', AI_DRAFT: 'info', EDITING: 'warning', SIGNED: 'success' }[s] || 'info')

onMounted(fetchList)
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: #0d1420;
  color: #d0dcf0;
  box-sizing: border-box;
}
.page-card { min-height: calc(100vh - 100px); }
.full-card {
  border: 1px solid rgba(111, 134, 166, 0.16);
  background: #0d1420;
  box-shadow: none;
}
:deep(.full-card .el-card__header) {
  border-bottom: 1px solid rgba(111, 134, 166, 0.16);
  background: #0d1420;
}
:deep(.full-card .el-card__body) {
  background: #0d1420;
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 6px; color: #e8f0ff; }
.search-form { margin-bottom: 12px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
:deep(.el-table) {
  --el-table-bg-color: #0f1923;
  --el-table-tr-bg-color: #0f1923;
  --el-table-row-striped-bg-color: #0f1923;
  --el-table-row-hover-bg-color: rgba(111, 134, 166, 0.12);
  --el-table-header-bg-color: rgba(13, 20, 32, 0.95);
  --el-table-border-color: rgba(111, 134, 166, 0.16);
  --el-table-text-color: #d0dcf0;
  --el-table-header-text-color: #9fb3cc;
}
:deep(.el-table th.el-table__cell),
:deep(.el-table td.el-table__cell) {
  background: #0f1923 !important;
}
:deep(.el-pagination) { color: #d0dcf0; }
:deep(.el-pagination .btn-prev),
:deep(.el-pagination .btn-next),
:deep(.el-pagination .el-pager li) {
  background: #0f1923 !important;
  color: #d0dcf0 !important;
  border: 1px solid rgba(111, 134, 166, 0.2) !important;
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
