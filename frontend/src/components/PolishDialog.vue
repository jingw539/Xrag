<template>
  <el-dialog v-model="visibleModel" title="AI 报告润色" width="640px" align-center>
    <template v-if="polishResult">
      <div style="margin-bottom:12px">
        <el-alert :title="polishResult.changes_summary || 'AI 已完成报告润色'"
          type="success" show-icon :closable="false" />
      </div>
      <div class="polish-compare">
        <div class="polish-col">
          <div class="polish-col-title" style="color:#f56c6c">原始草稿</div>
          <div class="polish-field-label">影像所见</div>
          <div class="polish-text">{{ draftFindings }}</div>
          <div class="polish-field-label" style="margin-top:8px">影像印象</div>
          <div class="polish-text">{{ draftImpression }}</div>
        </div>
        <div class="polish-arrow">→</div>
        <div class="polish-col">
          <div class="polish-col-title" style="color:#52c41a">AI 润色后</div>
          <div class="polish-field-label">影像所见</div>
          <div class="polish-text polish-text-new">{{ polishResult.polished_findings }}</div>
          <div class="polish-field-label" style="margin-top:8px">影像印象</div>
          <div class="polish-text polish-text-new">{{ polishResult.polished_impression }}</div>
        </div>
      </div>
      <div v-if="polishResult.suggestions && polishResult.suggestions.length" style="margin-top:12px">
        <div style="font-size:12px;font-weight:600;color:#722ed1;margin-bottom:6px">AI 建议</div>
        <div v-for="(s, i) in polishResult.suggestions" :key="i"
          style="font-size:12px;color:var(--xrag-text-soft);padding:2px 0">
          {{ i + 1 }}. {{ s }}
        </div>
      </div>
    </template>
    <template #footer>
      <el-button @click="visibleModel = false">取消</el-button>
      <el-button type="primary" @click="handleApply">
        <el-icon><Check /></el-icon> 采纳润色结果
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  polishResult: { type: Object, default: null },
  draftFindings: { type: String, default: '' },
  draftImpression: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'apply'])

const visibleModel = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const handleApply = () => emit('apply')
</script>

<style scoped>
.polish-compare { display: flex; gap: 12px; align-items: stretch; }
.polish-col { flex: 1; background: rgba(255,255,255,0.03); border: 1px solid var(--xrag-border); border-radius: 6px; padding: 10px; }
.polish-col-title { font-size: 12px; font-weight: 700; margin-bottom: 8px; }
.polish-field-label { font-size: 10px; color: var(--xrag-text-faint); margin-bottom: 3px; }
.polish-text { font-size: 12px; color: var(--xrag-text); line-height: 1.6; white-space: pre-wrap; }
.polish-text-new { color: #52c41a; }
.polish-arrow { display: flex; align-items: center; font-size: 20px; color: #bbb; flex-shrink: 0; }
</style>
