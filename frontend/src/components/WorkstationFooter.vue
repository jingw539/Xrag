<template>
  <div class="ws-footer">
    <div class="footer-nav">
      <el-button size="small" plain :disabled="!prevCaseId" @click="handleNavigate('prev')">
        ← 上一份
      </el-button>
      <el-button size="small" plain :disabled="!nextCaseId" @click="handleNavigate('next')">
        下一份 →
      </el-button>
    </div>

    <!-- 内联决策摘要：置信度 -->
    <div v-if="currentReport && currentReport.reportStatus !== 'SIGNED'" class="footer-eval-bar">
      <span v-if="currentReport.modelConfidence" class="feval-chip"
        :class="currentReport.modelConfidence >= 0.85 ? 'feval-high' : 'feval-mid'">
        AI {{ Math.round(currentReport.modelConfidence * 100) }}%
      </span>
    </div>

    <div class="footer-actions" v-if="currentReport">
      <el-button size="default" plain @click="handleSaveDraft"
        :loading="saving" :disabled="currentReport.reportStatus === 'SIGNED'">
        <el-icon><FolderOpened /></el-icon> 保存草稿
      </el-button>
      <el-button size="default" type="success" @click="handleSign"
        :loading="signing"
        v-if="currentReport.reportStatus !== 'SIGNED'">
        <el-icon><Check /></el-icon> 签发报告
      </el-button>
    </div>
    <div class="footer-actions" v-else-if="currentImage">
      <el-button size="default" type="primary" :loading="generating" @click="handleGenerate">
        <el-icon><MagicStick /></el-icon> 生成 AI 草稿
      </el-button>
    </div>
  </div>
</template>

<script setup>
const emit = defineEmits(['navigate', 'save-draft', 'sign', 'generate'])

defineProps({
  currentReport: { type: Object, default: null },
  currentImage: { type: Object, default: null },
  prevCaseId: { type: [Number, String], default: null },
  nextCaseId: { type: [Number, String], default: null },
  saving: { type: Boolean, default: false },
  signing: { type: Boolean, default: false },
  generating: { type: Boolean, default: false }
})

const handleNavigate = (dir) => emit('navigate', dir)
const handleSaveDraft = () => emit('save-draft')
const handleSign = () => emit('sign')
const handleGenerate = () => emit('generate')
</script>

<style scoped>
.ws-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--xrag-panel);
  border-top: 1px solid var(--xrag-border);
  flex-shrink: 0;
  margin-top: 10px;
}
.footer-nav { display: flex; gap: 8px; }
.footer-actions { display: flex; gap: 8px; }

.footer-eval-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  flex-wrap: wrap;
}
.feval-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}
.feval-high   { background: rgba(82, 196, 26, 0.12); color: #95de64; border: 1px solid rgba(149, 222, 100, 0.28); }
.feval-mid    { background: rgba(250, 140, 22, 0.12); color: #ffb86b; border: 1px solid rgba(255, 169, 64, 0.28); }
.feval-grade  { border: none; }
.feval-pending{ background: rgba(47, 84, 235, 0.14); color: #adc6ff; border: 1px solid rgba(133, 165, 255, 0.28); }
.feval-alert  { background: rgba(245, 34, 45, 0.14); color: #ff9c9c; border: 1px solid rgba(255, 120, 117, 0.28); }
.feval-safe   { background: rgba(82, 196, 26, 0.12); color: #95de64; border: 1px solid rgba(149, 222, 100, 0.28); }
</style>
