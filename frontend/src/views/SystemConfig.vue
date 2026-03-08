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
  ElMessage.success('已保存')
}

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
  padding: 0 20px 20px;
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

:deep(.el-input__wrapper) {
  background: #111a27;
  box-shadow: 0 0 0 1px rgba(111, 134, 166, 0.22) inset;
}
</style>
