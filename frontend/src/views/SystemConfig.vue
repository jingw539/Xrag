<template>
  <div class="page-wrap">
    <el-card class="full-card">
      <template #header>
        <span class="page-title"><el-icon><Setting /></el-icon> 系统配置</span>
      </template>

      <el-table class="admin-table" :data="configs" v-loading="loading" border>
        <el-table-column prop="configKey" label="配置键" width="240" />
        <el-table-column prop="configValue" label="配置值">
          <template #default="{ row }">
            <template v-if="row.editing">
              <el-input v-model="row._editVal" size="small" style="width: 300px" />
            </template>
            <template v-else>
              <span>{{ row.configValue }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <template v-if="row.editing">
              <el-button size="small" type="primary" @click="handleSave(row)">保存</el-button>
              <el-button size="small" @click="row.editing = false">取消</el-button>
            </template>
            <template v-else>
              <el-button size="small" @click="startEdit(row)">编辑</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listConfigs, updateConfig } from '@/api/config'

const loading = ref(false)
const configs = ref([])

const fetchList = async () => {
  loading.value = true
  try {
    const res = await listConfigs()
    configs.value = (res.data || []).map((item) => ({ ...item, editing: false, _editVal: item.configValue }))
  } finally {
    loading.value = false
  }
}

const startEdit = (row) => {
  row._editVal = row.configValue
  row.editing = true
}

const handleSave = async (row) => {
  await updateConfig(row.configKey, row._editVal)
  row.configValue = row._editVal
  row.editing = false
  ElMessage.success('保存成功')
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
  padding: 0 20px 20px;
  background: #0d1420;
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

:deep(.el-input__wrapper) {
  background: #111a27;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.22) inset;
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
