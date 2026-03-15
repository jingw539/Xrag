<template>
  <el-dialog v-model="visibleModel" title="AI 报告润色" width="640px" align-center>
    <template v-if="polishResult">
      <div class="polish-alert">
        <div class="changes-title">修改摘要</div>
        <ol class="changes-list">
          <li v-for="(c, idx) in changesList" :key="idx">{{ c }}</li>
        </ol>
      </div>

      <div class="polish-compare">
        <div class="polish-col">
          <div class="polish-col-title polish-col-title-old">原始草稿</div>
          <div class="polish-field-label">影像所见</div>
          <div class="polish-text" v-html="oldFindingsHtml"></div>
          <div class="polish-field-label field-gap">影像印象</div>
          <div class="polish-text" v-html="oldImpressionHtml"></div>
        </div>
        <div class="polish-arrow">→</div>
        <div class="polish-col">
          <div class="polish-col-title polish-col-title-new">AI 润色后</div>
          <div class="polish-field-label">影像所见</div>
          <div class="polish-text polish-text-new" v-html="newFindingsHtml"></div>
          <div class="polish-field-label field-gap">影像印象</div>
          <div class="polish-text polish-text-new" v-html="newImpressionHtml"></div>
        </div>
      </div>

      <div v-if="polishResult.suggestions && polishResult.suggestions.length" class="suggestions">
        <div class="suggestions-title">AI 建议</div>
        <div v-for="(s, i) in polishResult.suggestions" :key="i" class="suggestions-item">
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
import { Check } from '@element-plus/icons-vue'

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

const escapeHtml = (value) => {
  if (value === null || value === undefined) return ''
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const splitToUnits = (text) => {
  if (!text) return []
  return Array.from(text)
}

const diffUnits = (oldText, newText) => {
  const a = splitToUnits(oldText)
  const b = splitToUnits(newText)
  const n = a.length
  const m = b.length
  const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      dp[i][j] = a[i - 1] === b[j - 1]
        ? dp[i - 1][j - 1] + 1
        : Math.max(dp[i - 1][j], dp[i][j - 1])
    }
  }
  const ops = []
  let i = n
  let j = m
  while (i > 0 && j > 0) {
    if (a[i - 1] === b[j - 1]) {
      ops.push({ type: 'eq', value: a[i - 1] })
      i -= 1; j -= 1
    } else if (dp[i - 1][j] >= dp[i][j - 1]) {
      ops.push({ type: 'del', value: a[i - 1] }); i -= 1
    } else {
      ops.push({ type: 'add', value: b[j - 1] }); j -= 1
    }
  }
  while (i > 0) { ops.push({ type: 'del', value: a[i - 1] }); i -= 1 }
  while (j > 0) { ops.push({ type: 'add', value: b[j - 1] }); j -= 1 }
  return ops.reverse()
}

const buildDiffHtml = (oldText, newText, mode) => {
  if (!oldText && !newText) return ''
  const ops = diffUnits(oldText || '', newText || '')
  const parts = []
  for (const op of ops) {
    const safe = escapeHtml(op.value)
    if (op.type === 'eq') {
      parts.push(safe)
    } else if (op.type === 'add' && mode === 'new') {
      parts.push(`<span class="diff-add">${safe}</span>`)
    } else if (op.type === 'del' && mode === 'old') {
      parts.push(`<span class="diff-del">${safe}</span>`)
    }
  }
  return parts.join('')
}

const oldFindingsHtml = computed(() =>
  buildDiffHtml(props.draftFindings, props.polishResult?.polished_findings, 'old')
)
const newFindingsHtml = computed(() =>
  buildDiffHtml(props.draftFindings, props.polishResult?.polished_findings, 'new')
)
const oldImpressionHtml = computed(() =>
  buildDiffHtml(props.draftImpression, props.polishResult?.polished_impression, 'old')
)
const newImpressionHtml = computed(() =>
  buildDiffHtml(props.draftImpression, props.polishResult?.polished_impression, 'new')
)

const changesList = computed(() => {
  const summary = props.polishResult?.changes_summary || ''
  if (!summary) return []
  return summary
    .split(/[\n\r]+|；|;|。(?=\s*\d)/)
    .map(s => s.trim().replace(/^[0-9]+[\\.、)]\s*/, ''))
    .filter(Boolean)
})
</script>

<style scoped>
.changes-title { font-weight: 700; margin-bottom: 4px; color: var(--xrag-text); }
.changes-list { padding-left: 18px; margin: 0 0 6px; color: var(--xrag-text); }
.changes-list li { line-height: 1.5; }
.polish-compare { display: flex; gap: 12px; align-items: stretch; }
.polish-col { flex: 1; background: rgba(255,255,255,0.03); border: 1px solid var(--xrag-border); border-radius: 6px; padding: 10px; }
.polish-col-title { font-size: 12px; font-weight: 700; margin-bottom: 8px; }
.polish-field-label { font-size: 10px; color: var(--xrag-text-faint); margin-bottom: 3px; }
.polish-text { font-size: 12px; color: rgba(220,231,247,0.85); line-height: 1.6; white-space: pre-wrap; }
.polish-text-new { font-size: 12px; color: #e9f6d8; line-height: 1.6; white-space: pre-wrap; }
.polish-arrow { display: flex; align-items: center; font-size: 20px; color: #bbb; flex-shrink: 0; }
.polish-alert { margin-bottom: 12px; }
.polish-col-title-old { color: #f56c6c; }
.polish-col-title-new { color: #52c41a; }
.field-gap { margin-top: 8px; }
.suggestions { margin-top: 12px; }
.suggestions-title { font-size: 12px; font-weight: 600; color: #722ed1; margin-bottom: 6px; }
.suggestions-item { font-size: 12px; color: var(--xrag-text-soft); padding: 2px 0; }
.diff-add { background: rgba(82, 196, 26, 0.28); color: #d8ffbd; border-radius: 3px; padding: 0 3px; font-weight: 600; }
.diff-del { background: rgba(245, 108, 108, 0.28); color: #ffc7c7; border-radius: 3px; padding: 0 3px; text-decoration: line-through; }
.polish-alert :deep(.el-alert) { background: rgba(18, 34, 50, 0.85); border: 1px solid rgba(82, 196, 26, 0.35); color: var(--xrag-text); }
.polish-alert :deep(.el-alert__title), .polish-alert :deep(.el-alert__description) { color: var(--xrag-text) !important; }
</style>
