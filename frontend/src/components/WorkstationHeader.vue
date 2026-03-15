<template>
  <div class="ws-header">
    <div class="ws-title-block">
      <span class="ws-exam-no">{{ caseInfo.examNo }}</span>
      <span :class="['ws-status-tag', 'tag-' + statusColor(caseInfo.reportStatus)]">
        {{ statusLabel(caseInfo.reportStatus) }}
      </span>
      <span class="ws-patient-info">
        {{ genderLabel(caseInfo.gender) }} · {{ caseInfo.age }}岁 ·
        {{ formatDepartment(caseInfo.department) }} · {{ formatDate(caseInfo.examTime) }}
      </span>
    </div>
    <div class="ws-actions">
      <el-button size="small" plain @click="handleRegenerate" :loading="generating"
        :disabled="!hasReport">
        <el-icon><Refresh /></el-icon> 重新生成 AI 草稿
      </el-button>
      <el-button size="small" plain @click="handleMarkTypical">
        <el-icon><Star /></el-icon>
        {{ caseInfo.isTypical ? '取消典型标记' : '标记典型病例' }}
      </el-button>
      <el-button size="small" plain @click="handlePrint">
        <el-icon><Printer /></el-icon> 打印报告
      </el-button>
      <el-button size="small" plain type="danger" v-if="canDelete" @click="handleDeleteCase">
        <el-icon><Delete /></el-icon> 删除病例
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { Refresh, Star, Printer, Delete } from '@element-plus/icons-vue'
const emit = defineEmits(['regenerate', 'mark-typical', 'print', 'delete-case'])

defineProps({
  caseInfo: { type: Object, default: () => ({}) },
  hasReport: { type: Boolean, default: false },
  generating: { type: Boolean, default: false },
  canDelete: { type: Boolean, default: false },
  statusColor: { type: Function, required: true },
  statusLabel: { type: Function, required: true },
  genderLabel: { type: Function, required: true },
  formatDate: { type: Function, required: true }
})

const formatDepartment = (value) => {
  if (!value) return '—'
  const v = String(value).trim()
  if (!v || v === '???' || v === '??') return '—'
  return v
}

const handleRegenerate = () => emit('regenerate')
const handleMarkTypical = () => emit('mark-typical')
const handlePrint = () => emit('print')
const handleDeleteCase = () => emit('delete-case')
</script>

<style scoped>
/* 工作区顶部 */
.ws-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--xrag-panel);
  border-bottom: 1px solid var(--xrag-border);
  flex-shrink: 0;
  gap: 12px;
}
.ws-title-block { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ws-exam-no { font-size: 16px; font-weight: 700; color: var(--xrag-text); }
.ws-status-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 4px; font-weight: 500;
}
.tag-orange { background: rgba(250, 140, 22, 0.14); border: 1px solid rgba(250, 173, 20, 0.32); color: #ffb86b; }
.tag-blue   { background: rgba(24, 144, 255, 0.14); border: 1px solid rgba(64, 169, 255, 0.32); color: #69b1ff; }
.tag-green  { background: rgba(82, 196, 26, 0.14); border: 1px solid rgba(149, 222, 100, 0.32); color: #95de64; }
.ws-patient-info { font-size: 12px; color: var(--xrag-text-soft); }
.ws-actions { display: flex; gap: 6px; flex-shrink: 0; }

@media (max-width: 1200px) {
  .ws-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .ws-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .ws-actions {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
