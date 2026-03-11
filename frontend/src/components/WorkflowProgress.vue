<template>
  <div class="progress-section">
    <div class="section-title">诊断流程进度</div>
    <div v-if="followupSummary || hasPixelSpacing || selectedDoctorAnnotation" class="progress-summary">
      <span v-if="followupSummary">{{ followupSummary }}</span>
      <span v-if="hasPixelSpacing" class="progress-summary-tag">像素间距 {{ pixelSpacingText }}</span>
      <span v-if="selectedDoctorAnnotation" class="progress-summary-tag">当前标注 {{ formatAnnoMeasurement(selectedDoctorAnnotation) }}</span>
    </div>
    <div class="workflow-steps">
      <div v-for="(step, i) in workflowSteps" :key="i"
        :class="['step-item', step.status]">
        <div class="step-circle">
          <el-icon v-if="step.status === 'done'"><Check /></el-icon>
          <span v-else>{{ i + 1 }}</span>
        </div>
        <div class="step-info">
          <div class="step-name">{{ step.name }}</div>
          <div class="step-time">{{ step.time || step.hint }}</div>
        </div>
        <div v-if="i < workflowSteps.length - 1" class="step-line"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  workflowSteps: { type: Array, default: () => [] },
  followupSummary: { type: String, default: '' },
  hasPixelSpacing: { type: Boolean, default: false },
  pixelSpacingText: { type: String, default: '' },
  selectedDoctorAnnotation: { type: Object, default: null },
  formatAnnoMeasurement: { type: Function, required: true }
})
</script>

<style scoped>
.progress-section {
  padding: 8px 12px;
  background: var(--xrag-panel);
  margin: 0;
  border-radius: 8px;
  border: 1px solid var(--xrag-border);
  flex-shrink: 0;
}
.progress-summary {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  font-size: 11px; color: var(--xrag-text-soft); margin: -2px 0 8px;
}
.progress-summary-tag {
  padding: 2px 6px; border-radius: 10px; background: rgba(74,158,255,0.12); color: #8ec5ff;
}
.workflow-steps {
  display: flex;
  align-items: center;
  padding: 2px 0;
}
.step-item {
  display: flex; align-items: center; flex: 1; position: relative;
}
.step-circle {
  width: 24px; height: 24px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600; flex-shrink: 0;
  background: rgba(111,134,166,0.16); color: var(--xrag-text-faint); border: 2px solid rgba(111,134,166,0.16);
}
.step-item.done .step-circle { background: #52c41a; border-color: #52c41a; color: #fff; }
.step-item.active .step-circle { background: #1890ff; border-color: #1890ff; color: #fff; }

.step-info { padding: 0 6px; flex-shrink: 0; }
.step-name { font-size: 10px; font-weight: 600; color: var(--xrag-text); white-space: nowrap; }
.step-time { font-size: 9px; color: var(--xrag-text-faint); white-space: nowrap; }
.step-item.done .step-name, .step-item.active .step-name { color: #f4f8ff; }

.step-line {
  flex: 1; height: 2px;
  background: rgba(111,134,166,0.16);
  margin: 0 4px;
}
.step-item.done + .step-item .step-line { background: #52c41a; }
</style>
