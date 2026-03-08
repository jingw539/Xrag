<template>
  <div class="page-wrap">
    <el-card class="full-card">
      <template #header>
        <div class="card-header">
          <span class="page-title"><el-icon><Bell /></el-icon>危急值预警</span>
          <el-badge :value="pendingCount" :hidden="!pendingCount">
            <el-tag type="danger">待处理 {{ pendingCount }} 条</el-tag>
          </el-badge>
        </div>
      </template>

      <el-form :model="query" inline class="toolbar-form">
        <el-form-item label="状态">
          <el-select v-model="query.alertStatus" clearable placeholder="全部" style="width:120px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已处理" value="ACKNOWLEDGED" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警类型">
          <el-input v-model="query.labelType" clearable placeholder="如：Pneumothorax" style="width:180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button class="secondary-btn" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="alerts" v-loading="loading" border stripe>
        <el-table-column prop="labelType" label="预警类型" width="180">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.labelType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="labelProb" label="置信度" width="120" align="center">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.labelProb * 100)" :stroke-width="8" :color="row.labelProb > 0.9 ? '#f56c6c' : '#e6a23c'" />
          </template>
        </el-table-column>
        <el-table-column prop="alertStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.alertStatus === 'PENDING' ? 'danger' : 'success'" size="small">
              {{ row.alertStatus === 'PENDING' ? '待处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responseNote" label="处理备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="responderName" label="处理人" width="100" />
        <el-table-column prop="alertTime" label="预警时间" width="170">
          <template #default="{ row }">{{ formatDate(row.alertTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="goCase(row)">查看病例</el-button>
            <el-button v-if="row.alertStatus === 'PENDING'" size="small" type="primary" @click="openRespond(row)" style="margin-top:4px">
              处理
            </el-button>
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

  <el-dialog v-model="respondVisible" title="处理危急值预警" width="420px">
    <el-form label-width="80px">
      <el-descriptions :column="1" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="预警类型">{{ current.labelType }}</el-descriptions-item>
        <el-descriptions-item label="置信度">{{ ((current.labelProb || 0) * 100).toFixed(1) }}%</el-descriptions-item>
      </el-descriptions>
      <el-form-item label="处理动作">
        <el-select v-model="respondAction" style="width:100%">
          <el-option label="确认已知" value="ACKNOWLEDGED" />
          <el-option label="上转处理" value="ESCALATED" />
          <el-option label="驳回" value="DISMISSED" />
        </el-select>
      </el-form-item>
      <el-form-item label="AI建议">
        <div class="suggest-chips">
          <el-tag v-for="s in smartSuggestions" :key="s" class="suggest-chip" type="info" effect="plain" @click="respondNote = s">
            {{ s }}
          </el-tag>
        </div>
      </el-form-item>
      <el-form-item label="处理备注">
        <el-input v-model="respondNote" type="textarea" :rows="3" placeholder="填写处理说明，可点击上方建议快速填入" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="respondVisible = false">取消</el-button>
      <el-button type="primary" :loading="responding" @click="handleRespond">确认处理</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listAlerts, respondAlert, getPendingCount } from '@/api/alert'

const router = useRouter()
const loading = ref(false)
const responding = ref(false)
const alerts = ref([])
const total = ref(0)
const pendingCount = ref(0)
const respondVisible = ref(false)
const respondNote = ref('')
const respondAction = ref('ACKNOWLEDGED')
const current = ref({})
const query = reactive({ page: 1, pageSize: 20, alertStatus: '', labelType: '' })

const SMART_RESPONSES = {
  Pneumothorax: [
    '已通知值班医生，建议立即复查胸片确认，必要时行胸腔穿刺减压',
    '气胸体积较小时建议密切观察，结合临床症状评估处理'
  ],
  'Pleural Effusion': [
    '已通知主管医生，建议结合超声评估积液量，必要时行胸腔穿刺引流',
    '积液量较少时建议结合症状随访，暂行保守处理'
  ],
  Pneumonia: [
    '已提示临床医生，建议结合血常规、体温和症状综合评估，必要时调整抗感染方案'
  ],
  Cardiomegaly: [
    '已提示心内科会诊，建议进一步行超声心动图评估'
  ]
}

const smartSuggestions = computed(() => {
  return SMART_RESPONSES[current.value.labelType] || [
    '已确认影像结果，已通知相关医生处理',
    '已核实预警信息，请结合患者症状综合评估'
  ]
})

const fetchList = async () => {
  loading.value = true
  try {
    const [listRes, countRes] = await Promise.all([listAlerts(query), getPendingCount()])
    alerts.value = listRes.data.list || []
    total.value = listRes.data.total || 0
    pendingCount.value = countRes.data || 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  Object.assign(query, { page: 1, alertStatus: '', labelType: '' })
  fetchList()
}

const goCase = (row) => router.push({ path: '/cases', query: { caseId: row.caseId } })
const openRespond = (row) => {
  current.value = row
  respondNote.value = ''
  respondAction.value = 'ACKNOWLEDGED'
  respondVisible.value = true
}
const formatDate = (val) => (val ? val.replace('T', ' ').substring(0, 16) : '-')

const handleRespond = async () => {
  responding.value = true
  try {
    await respondAlert(current.value.alertId, { action: respondAction.value, note: respondNote.value })
    current.value.alertStatus = respondAction.value
    current.value.responseNote = respondNote.value
    current.value.responderName = '当前用户'
    respondVisible.value = false
    ElMessage.success('处理成功')
    pendingCount.value = Math.max(0, pendingCount.value - 1)
    fetchList()
  } finally {
    responding.value = false
  }
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
  background: #0E1621;
}

.full-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #1A2535;
  border: 1px solid rgba(111, 134, 166, 0.28);
  box-shadow: none;
}

:deep(.full-card .el-card__header),
:deep(.full-card .el-card__body) {
  background: #1A2535;
  border-color: rgba(111, 134, 166, 0.24);
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

.suggest-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.suggest-chip {
  cursor: pointer;
  user-select: none;
  line-height: 1.4;
  height: auto;
  white-space: normal;
  padding: 4px 8px;
}

.suggest-chip:hover {
  background: rgba(74, 158, 255, 0.14);
  border-color: rgba(74, 158, 255, 0.34);
  color: #cfe2ff;
}

:deep(.el-form-item__label),
:deep(.el-text),
:deep(.el-dialog__title),
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
:deep(.el-table__empty-block),
:deep(.el-descriptions__body),
:deep(.el-descriptions__table),
:deep(.el-dialog),
:deep(.el-dialog__header),
:deep(.el-dialog__body),
:deep(.el-dialog__footer) {
  background: #1A2535;
  color: #D0DCF0;
  border-color: rgba(111, 134, 166, 0.24);
}

:deep(.el-table--border::before),
:deep(.el-table--border::after),
:deep(.el-table__inner-wrapper::before),
:deep(.el-table td.el-table__cell),
:deep(.el-table th.el-table__cell),
:deep(.el-descriptions__cell) {
  border-color: rgba(111, 134, 166, 0.24);
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: rgba(233, 238, 245, 0.04);
}

:deep(.el-table__body tr:hover > td.el-table__cell) {
  background: rgba(74, 158, 255, 0.09);
}

:deep(.el-button),
:deep(.el-button span),
:deep(.el-button .el-icon) {
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

:deep(.el-tag),
:deep(.el-badge__content) {
  background: rgba(233, 238, 245, 0.08);
  color: #EAF2FF;
  border-color: rgba(111, 134, 166, 0.24);
}

:deep(.el-pagination),
:deep(.el-pagination button),
:deep(.el-pager li) {
  background: transparent;
  color: #D0DCF0;
}

:deep(.el-progress-bar__outer) {
  background: rgba(233, 238, 245, 0.08);
}

/* ===== Final button cleanup ===== */
:deep(.el-button--default),
:deep(.el-button.is-link),
:deep(.suggest-chip),
.suggest-chip {
  background: rgba(233, 238, 245, 0.06) !important;
  border-color: rgba(111, 134, 166, 0.28) !important;
  color: #DCE7F7 !important;
}

:deep(.el-button:hover),
.suggest-chip:hover {
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




