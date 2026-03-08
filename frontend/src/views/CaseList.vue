<template>
  <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span class="page-title"><el-icon><Folder /></el-icon> 病例管理</span>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon> 新建病例
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
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
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD"
            style="width:240px" @change="onDateChange" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="cases" v-loading="loading" border stripe row-key="caseId"
        @row-click="goDetail" style="cursor:pointer">
        <el-table-column prop="examNo" label="检查号" width="130" />
        <el-table-column prop="patientAnonId" label="患者ID" width="130" />
        <el-table-column prop="gender" label="性别" width="60" align="center">
          <template #default="{ row }">
            <el-tag :type="row.gender === 'M' ? 'primary' : 'danger'" size="small">
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

      <el-pagination class="pagination" :current-page="query.page" :page-size="query.pageSize"
        :total="total" layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        @current-change="v => { query.page = v; fetchList() }"
        @size-change="v => { query.pageSize = v; query.page = 1; fetchList() }" />
    </el-card>

    <!-- 新建病例对话框 -->
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
          <el-date-picker v-model="createForm.examTime" type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
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
  await ElMessageBox.confirm(`确认删除病例「${row.examNo}」？此操作不可恢复`, '警告', { type: 'warning' })
  await deleteCase(row.caseId)
  ElMessage.success('删除成功')
  fetchList()
}

const formatDate = (val) => val ? val.replace('T', ' ').substring(0, 16) : '-'
const statusLabel = (s) => ({ NONE: '待生成', AI_DRAFT: 'AI草稿', EDITING: '编辑中', SIGNED: '已签发' }[s] || s || '-')
const statusType = (s) => ({ NONE: 'info', AI_DRAFT: '', EDITING: 'warning', SIGNED: 'success' }[s] || '')

onMounted(fetchList)
</script>

<style scoped>
.page-card { min-height: calc(100vh - 100px); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.search-form { margin-bottom: 12px; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
