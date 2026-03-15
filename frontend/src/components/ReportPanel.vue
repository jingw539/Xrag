<template>
  <div class="report-panel">
    <!-- 已签发只读视图 -->
    <div v-if="isSigned" class="signed-report-view">
      <div class="signed-banner">
        <div class="signed-banner-left">
          <div class="signed-icon-circle"><el-icon :size="18"><Check /></el-icon></div>
          <div>
            <div class="signed-title">报告已签发</div>
            <div class="signed-meta">
              签发医生：{{ currentReport?.doctorName || '—' }} · {{ formatDate(currentReport?.signTime) }}
            </div>
          </div>
        </div>
      </div>

      <div class="signed-content">
        <div class="signed-section">
          <div class="signed-section-label"><el-icon><Document /></el-icon> 影像所见</div>
          <div class="signed-text">{{ currentReport?.finalFindings || '—' }}</div>
        </div>
        <div class="signed-section">
          <div class="signed-section-label"><el-icon><Document /></el-icon> 影像印象</div>
          <div class="signed-text">{{ currentReport?.finalImpression || '—' }}</div>
        </div>

        <div v-if="currentReport?.aiFindings && currentReport?.aiFindings !== currentReport?.finalFindings"
             class="signed-ai-compare">
          <div class="compare-header" @click="showAiCompareLocal = !showAiCompareLocal">
            <el-icon><Cpu /></el-icon>
            <span>AI 原始草稿对比</span>
            <span class="compare-diff-hint">医生已修改</span>
            <el-icon class="compare-toggle-icon">
              <ArrowDown v-if="!showAiCompareLocal" />
              <ArrowUp v-else />
            </el-icon>
          </div>
          <div v-if="showAiCompareLocal" class="compare-body">
            <div class="compare-field">
              <div class="compare-label">AI 影像所见</div>
              <div class="compare-text">{{ currentReport?.aiFindings }}</div>
            </div>
            <div class="compare-field">
              <div class="compare-label">AI 影像印象</div>
              <div class="compare-text">{{ currentReport?.aiImpression }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 可编辑视图 -->
    <el-tabs v-else v-model="reportTabLocal" class="report-tabs">
      <el-tab-pane label="报告编辑" name="edit">
        <div v-if="!currentReport" class="no-report">
          <el-empty description="尚未生成报告">
            <el-button type="primary" :loading="generating"
                       :disabled="!hasCurrentImage"
                       @click="$emit('generate')">
              <el-icon><MagicStick /></el-icon> 生成AI报告
            </el-button>
          </el-empty>
        </div>

        <div v-else class="report-form" :class="{ 'form-readonly': isSigned }">
          <div class="status-notice" :class="currentReport.reportStatus === 'AI_DRAFT' ? 'status-ai-draft' : 'status-editing'">
            <el-icon class="status-icon">
              <Cpu v-if="currentReport.reportStatus === 'AI_DRAFT'" />
              <Edit v-else />
            </el-icon>
            <span class="status-text">{{ currentReport.reportStatus === 'AI_DRAFT' ? 'AI 草稿' : '编辑中' }}</span>
            <span class="status-owner">
              <el-icon class="status-user-icon"><User /></el-icon>
              {{ doctorName || '当前医生' }}
            </span>
          </div>

          <div class="field-block">
            <div class="field-label">
              影像所见
              <span class="ai-label"><el-icon><Cpu /></el-icon> AI 生成</span>
            </div>
            <el-input v-model="findingsLocal" type="textarea" :rows="6"
                      placeholder="请输入影像所见，建议描述部位、形态、密度和范围"
                      resize="none" :disabled="isSigned" />
          </div>

          <div class="field-block field-block-spaced">
            <div class="field-label">
              影像印象
              <span class="ai-label"><el-icon><Cpu /></el-icon> AI 生成</span>
            </div>
            <el-input v-model="impressionLocal" type="textarea" :rows="4"
                      placeholder="请输入影像印象，概括主要结论与诊疗建议"
                      resize="none" :disabled="isSigned" />
          </div>

          <div class="edit-toolbar">
            <el-button size="small" type="primary" plain :loading="polishing"
                       :disabled="isSigned" @click="$emit('polish')">
              <el-icon><MagicStick /></el-icon> AI 润色
            </el-button>
            <el-button size="small" :loading="termLoading"
                       :disabled="isSigned" @click="$emit('term-normalize')">
              <el-icon><Edit /></el-icon> 术语标准化
            </el-button>
            <span v-if="termLastCount > 0" class="term-count">已替换 {{ termLastCount }} 处术语</span>
          </div>

        </div>
      </el-tab-pane>

      <el-tab-pane label="基本信息" name="info">
        <div class="info-grid">
          <div class="info-item">
            <div class="info-label">检查号</div>
            <div class="info-value">{{ caseInfo?.examNo || '—' }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">患者信息</div>
            <div class="info-value">
              {{ caseInfo?.gender ? (caseInfo.gender === 'M' ? '男' : '女') : '—' }}
              <span v-if="caseInfo?.age != null"> · {{ caseInfo.age }} 岁</span>
            </div>
          </div>
          <div class="info-item">
            <div class="info-label">检查部位</div>
            <div class="info-value">{{ formatBodyPart(caseInfo?.bodyPart) }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">科室</div>
            <div class="info-value">{{ formatDepartment(caseInfo?.department) }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">检查时间</div>
            <div class="info-value">{{ formatDate(caseInfo?.examTime) || '—' }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">报告状态</div>
            <div class="info-value">{{ reportStatusLabel }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">报告ID</div>
            <div class="info-value">{{ currentReport?.reportId || '—' }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">签发时间</div>
            <div class="info-value">{{ currentReport?.signTime ? formatDate(currentReport.signTime) : '—' }}</div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowDown, ArrowUp, Check, Cpu, Document, Edit, MagicStick, User } from '@element-plus/icons-vue'

const props = defineProps({
  reportTab: { type: String, default: 'edit' },
  showAiCompare: { type: Boolean, default: false },
  findings: { type: String, default: '' },
  impression: { type: String, default: '' },
  caseInfo: { type: Object, default: null },
  currentReport: { type: Object, default: null },
  generating: { type: Boolean, default: false },
  hasCurrentImage: { type: Boolean, default: false },
  polishing: { type: Boolean, default: false },
  termLoading: { type: Boolean, default: false },
  termLastCount: { type: Number, default: 0 },
  doctorName: { type: String, default: '' },
  formatDate: { type: Function, default: (d) => d || '' }
})

const emit = defineEmits([
  'update:reportTab',
  'update:showAiCompare',
  'update:findings',
  'update:impression',
  'generate',
  'polish',
  'term-normalize'
])

const formatDepartment = (value) => {
  if (!value) return '—'
  const v = String(value).trim()
  if (!v || v === '???' || v === '??') return '—'
  return v
}
const formatBodyPart = (value) => {
  if (!value) return '胸部'
  const v = String(value).trim()
  if (!v || v === '???' || v === '??') return '胸部'
  return v
}

const reportTabLocal = computed({
  get: () => props.reportTab,
  set: (v) => emit('update:reportTab', v)
})
const showAiCompareLocal = computed({
  get: () => props.showAiCompare,
  set: (v) => emit('update:showAiCompare', v)
})
const findingsLocal = computed({
  get: () => props.findings,
  set: (v) => emit('update:findings', v)
})
const impressionLocal = computed({
  get: () => props.impression,
  set: (v) => emit('update:impression', v)
})

const isSigned = computed(() => props.currentReport?.reportStatus === 'SIGNED')
const reportStatusLabel = computed(() => {
  const status = props.currentReport?.reportStatus
  const map = { NONE: '未生成', AI_DRAFT: 'AI 草稿', EDITING: '编辑中', SIGNED: '已签发' }
  return map[status] || (status || '—')
})
</script>

<style scoped>
.report-panel {
  flex: 1 1 0;
  min-width: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  color: var(--xrag-text);
}
.no-report { padding: 24px 0; text-align: center; color: var(--xrag-text-soft); }
.report-form { display: flex; flex-direction: column; gap: 12px; padding: 12px 16px; }
.form-readonly { pointer-events: none; opacity: 0.7; }
.status-notice { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-radius: 8px; border: 1px solid var(--xrag-border); }
.status-ai-draft { background: rgba(64,169,255,0.08); }
.status-editing { background: rgba(255,193,7,0.08); }
.status-icon { color: #69c0ff; }
.status-text { font-weight: 700; }
.status-owner { margin-left: auto; display: flex; align-items: center; gap: 6px; color: var(--xrag-text-soft); }
.field-block { display: flex; flex-direction: column; gap: 6px; }
.field-block-spaced { margin-top: 6px; }
.field-label { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.ai-label { font-size: 12px; color: var(--xrag-text-soft); display: inline-flex; align-items: center; gap: 4px; }
.edit-toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.term-count { color: var(--xrag-text-soft); font-size: 12px; }
.toolbar-divider { margin: 8px 0; }
.full-width { width: 100%; }

.info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 16px; padding: 14px 16px; }
.info-item { border: 1px solid var(--xrag-border); border-radius: 8px; padding: 10px 12px; background: rgba(255,255,255,0.03); }
.info-label { color: var(--xrag-text-soft); font-size: 12px; margin-bottom: 4px; }
.info-value { color: var(--xrag-text); font-size: 13px; }

.signed-report-view { display: flex; flex-direction: column; gap: 12px; padding: 12px 14px; }
.signed-banner { display: flex; align-items: center; gap: 12px; padding: 12px 14px; background: var(--xrag-panel); border: 1px solid var(--xrag-border); border-radius: 10px; }
.signed-banner-left { display: flex; align-items: center; gap: 10px; }
.signed-icon-circle { width: 34px; height: 34px; border-radius: 50%; background: #52c41a; display: grid; place-items: center; color: #fff; }
.signed-title { font-size: 15px; font-weight: 700; }
.signed-meta { font-size: 12px; color: var(--xrag-text-soft); }
.signed-content { display: flex; flex-direction: column; gap: 10px; }
.signed-section { background: rgba(255,255,255,0.03); border: 1px solid var(--xrag-border); border-radius: 8px; padding: 10px 12px; }
.signed-section-label { display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 13px; margin-bottom: 6px; color: var(--xrag-text-soft); }
.signed-text { color: var(--xrag-text); line-height: 1.6; white-space: pre-wrap; }
.signed-ai-compare { border: 1px dashed var(--xrag-border); border-radius: 8px; overflow: hidden; }
.compare-header { display: flex; align-items: center; gap: 6px; padding: 8px 12px; background: rgba(255,255,255,0.03); cursor: pointer; color: var(--xrag-text-soft); }
.compare-diff-hint { font-size: 11px; padding: 1px 6px; background: rgba(250,140,22,0.12); color: #ffb86b; border-radius: 3px; border: 1px solid rgba(255,214,102,0.28); }
.compare-body { padding: 10px 12px; background: rgba(255,255,255,0.02); }
.compare-field { margin-bottom: 8px; }
.compare-label { font-weight: 600; font-size: 12px; color: var(--xrag-text-soft); margin-bottom: 2px; }
.compare-text { white-space: pre-wrap; color: var(--xrag-text); }

.report-tabs { height: 100%; display: flex; flex-direction: column; }
.report-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 16px; border-bottom: 1px solid var(--xrag-border); flex-shrink: 0; }
.report-tabs :deep(.el-tabs__content) { flex: 1; overflow-y: auto; }
</style>


