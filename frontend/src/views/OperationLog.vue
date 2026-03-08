<template>
  <div class="page-wrap">
    <el-card class="full-card">
      <template #header>
        <span class="page-title"><el-icon><List /></el-icon> 操作日志</span>
      </template>

      <el-form :model="query" inline>
        <el-form-item label="操作类型">
          <el-input v-model="query.operationType" clearable placeholder="如：SIGN_REPORT" style="width: 150px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width: 220px"
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
            <el-tag size="small" :type="row.isError ? 'danger' : 'info'">{{ row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标ID" width="120" />
        <el-table-column prop="apiPath" label="接口路径" show-overflow-tooltip />
        <el-table-column prop="clientIp" label="客户端IP" width="130" />
        <el-table-column prop="elapsedMs" label="耗时(ms)" width="90" align="right" />
        <el-table-column prop="createdAt" label="操作时间" width="160">
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

  <el-dialog v-model="detailVisible" title="日志详情" width="500px">
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="操作用户">{{ current.userName }}</el-descriptions-item>
      <el-descriptions-item label="操作类型">{{ current.operationType }}</el-descriptions-item>
      <el-descriptions-item label="目标ID">{{ current.targetId }}</el-descriptions-item>
      <el-descriptions-item label="操作详情">{{ current.detail }}</el-descriptions-item>
      <el-descriptions-item label="接口路径">{{ current.apiPath }}</el-descriptions-item>
      <el-descriptions-item label="客户端IP">{{ current.clientIp }}</el-descriptions-item>
      <el-descriptions-item label="耗时">{{ current.elapsedMs }} ms</el-descriptions-item>
      <el-descriptions-item v-if="current.errorMsg" label="错误信息">
        <span class="error-text">{{ current.errorMsg }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="操作时间">{{ formatDate(current.createdAt) }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { listLogs } from '@/api/log'

const loading = ref(false)
const logs = ref([])
const total = ref(0)
const dateRange = ref([])
const detailVisible = ref(false)
const current = ref({})
const query = reactive({ page: 1, pageSize: 20, operationType: '', startTime: '', endTime: '' })

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listLogs(query)
    logs.value = res.data.list || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  Object.assign(query, { page: 1, operationType: '', startTime: '', endTime: '' })
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

const formatDate = (value) => (value ? value.replace('T', ' ').substring(0, 19) : '-')

onMounted(fetchList)
</script>

<style scoped>
.page-wrap {
  min-height: 100%;
  padding: 20px;
  background: #0e1621;
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
  border: 1px solid rgba(111, 134, 166, 0.24);
  background: #1a2535;
  box-shadow: none;
}

:deep(.full-card .el-card__header) {
  border-bottom: 1px solid rgba(111, 134, 166, 0.24);
  background: #1a2535;
}

:deep(.full-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 20px 20px;
  background: #1a2535;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 16px 20px;
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

:deep(.admin-table) {
  --el-table-bg-color: #121c2a;
  --el-table-tr-bg-color: #121c2a;
  --el-table-row-striped-bg-color: #121c2a;
  --el-table-row-hover-bg-color: #1c2a3d;
  --el-table-header-bg-color: #182538;
  --el-table-border-color: rgba(111, 134, 166, 0.22);
  --el-table-text-color: #d0dcf0;
  --el-table-header-text-color: #9fb3cc;
}

:deep(.admin-table .el-table__inner-wrapper),
:deep(.admin-table th.el-table__cell),
:deep(.admin-table tr),
:deep(.admin-table td.el-table__cell) {
  background: #121c2a !important;
}

:deep(.el-form-item__label),
:deep(.el-dialog__title),
:deep(.el-descriptions__label),
:deep(.el-descriptions__content),
:deep(.el-pagination) {
  color: #d0dcf0;
}

:deep(.el-input__wrapper),
:deep(.el-date-editor.el-input__wrapper) {
  background: #111a27;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.22) inset;
}
</style>
