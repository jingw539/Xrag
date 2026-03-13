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

      <div class="table-wrap" v-loading="loading">
        <el-table
          v-if="!isMobile"
          :data="cases"
          border
          stripe
          row-key="caseId"
          @row-click="goDetail"
          style="cursor:pointer"
        >
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

        <div v-else class="card-list">
          <div v-for="row in cases" :key="row.caseId" class="case-card" @click="goDetail(row)">
            <div class="case-card-top">
              <span class="case-no">{{ row.examNo }}</span>
              <el-tag :type="statusType(row.reportStatus)" size="small">
                {{ statusLabel(row.reportStatus) }}
              </el-tag>
            </div>
            <div class="case-card-meta">
              <span>{{ row.patientAnonId }}</span>
              <span>{{ row.gender === 'M' ? '男' : row.gender === 'F' ? '女' : '-' }}</span>
              <span>{{ row.age }}岁</span>
            </div>
            <div class="case-card-info">
              <span>{{ row.bodyPart || '胸部' }}</span>
              <span>{{ row.department || '—' }}</span>
              <span>{{ formatDate(row.examTime) }}</span>
            </div>
            <div class="case-card-actions">
              <el-button size="small" @click.stop="goDetail(row)">详情</el-button>
              <el-button size="small" type="danger" @click.stop="handleDelete(row)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="cases.length === 0" description="暂无病例" :image-size="60" />
        </div>
      </div>

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
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
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
const isMobile = ref(false)

const query = reactive({ page: 1, pageSize: 20, examNo: '', reportStatus: '', department: '', startTime: '', endTime: '' })
const createForm = reactive({ examNo: '', patientAnonId: '', gender: 'M', age: null, bodyPart: '胸部', department: '', examTime: '' })
const createRules = {
  examNo: [{ required: true, message: '请输入检查号', trigger: 'blur' }],
  patientAnonId: [{ required: true, message: '请输入患者ID', trigger: 'blur' }],
  examTime: [{ required: true, message: '请选择检查时间', trigger: 'change' }]
}

const updateIsMobile = () => {
  isMobile.value = window.innerWidth <= 768
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
  query.startTime = val ? `${val[0]}T00:00:00` : ''
  query.endTime = val ? `${val[1]}T23:59:59` : ''
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

onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  fetchList()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateIsMobile)
})
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: var(--xrag-bg);
  color: var(--xrag-text);
  box-sizing: border-box;
}
.page-card { min-height: calc(100vh - 100px); }
.full-card {
  border: 1px solid var(--xrag-border);
  background: var(--xrag-bg);
  box-shadow: var(--xrag-shadow);
}
:deep(.full-card .el-card__header) {
  border-bottom: 1px solid var(--xrag-border);
  background: var(--xrag-bg);
}
:deep(.full-card .el-card__body) {
  background: var(--xrag-bg);
}
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.page-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 6px; color: #e8f0ff; }
.search-form { margin-bottom: 12px; }
.pagination { margin-top: 16px; justify-content: flex-end; }

.table-wrap { min-height: 240px; }

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

.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.case-card {
  padding: 14px;
  border-radius: 12px;
  border: 1px solid var(--xrag-border);
  background: rgba(15, 25, 35, 0.9);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.case-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.case-no {
  font-weight: 600;
  color: var(--xrag-text);
}

.case-card-meta,
.case-card-info {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--xrag-text-soft);
}

.case-card-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 768px) {
  .page-wrap {
    padding: 12px;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .search-form :deep(.el-form-item) {
    margin-right: 0;
    width: 100%;
  }

  .search-form {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .pagination {
    justify-content: center;
  }
}
</style>
