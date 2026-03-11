<template>
  <div class="report-panel">
    <!-- ═══ 已签发：不显示标签页，直接展示报告内容+评测结果 ═══ -->
    <div v-if="currentReport && currentReport.reportStatus === 'SIGNED'" class="signed-report-view">
      <div class="signed-banner">
        <div class="signed-banner-left">
          <div class="signed-icon-circle"><el-icon :size="18"><Check /></el-icon></div>
          <div>
            <div class="signed-title">报告已签发</div>
            <div class="signed-meta">
              签发医生：{{ currentReport.doctorName || '—' }} · {{ formatDate(currentReport.signTime) }}
            </div>
          </div>
        </div>
        <div style="display:flex;align-items:center;gap:8px;margin-left:auto"></div>
      </div>

      <div class="signed-content">
        <div class="signed-section">
          <div class="signed-section-label"><el-icon><Document /></el-icon> 影像所见</div>
          <div class="signed-text">{{ currentReport.finalFindings || '—' }}</div>
        </div>
        <div class="signed-section">
          <div class="signed-section-label"><el-icon><Document /></el-icon> 影像印象</div>
          <div class="signed-text">{{ currentReport.finalImpression || '—' }}</div>
        </div>

        <div v-if="currentReport.aiFindings && currentReport.aiFindings !== currentReport.finalFindings"
          class="signed-ai-compare">
          <div class="compare-header" @click="showAiCompareModel = !showAiCompareModel">
            <el-icon><Cpu /></el-icon>
            <span>AI 原始草稿对比</span>
            <span class="compare-diff-hint">医生已修改</span>
            <el-icon style="margin-left:auto"><ArrowDown v-if="!showAiCompareModel" /><ArrowUp v-else /></el-icon>
          </div>
          <div v-if="showAiCompareModel" class="compare-body">
            <div class="compare-field">
              <div class="compare-label">AI影像所见：</div>
              <div class="compare-text">{{ currentReport.aiFindings }}</div>
            </div>
            <div class="compare-field">
              <div class="compare-label">AI影像印象：</div>
              <div class="compare-text">{{ currentReport.aiImpression }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══ 非签发状态：标签页模式 ═══ -->
    <el-tabs v-else v-model="reportTabModel" class="report-tabs">
      <el-tab-pane label="报告编辑" name="edit">
        <div v-if="!currentReport" class="no-report">
          <el-empty description="尚未生成报告">
            <el-button type="primary" :loading="generating"
              :disabled="!hasCurrentImage" @click="handleGenerate">
              <el-icon><MagicStick /></el-icon> 生成AI报告
            </el-button>
          </el-empty>
        </div>
        
        <!-- AI草稿/编辑中：统一可编辑视图，无需额外步骤 -->
        <div v-else class="report-form">
          <div class="status-notice"
            :class="currentReport.reportStatus === 'AI_DRAFT' ? 'status-ai-draft' : 'status-editing'">
            <el-icon style="margin-right:4px">
              <Cpu v-if="currentReport.reportStatus === 'AI_DRAFT'" />
              <Edit v-else />
            </el-icon>
            <span :style="{ color: currentReport.reportStatus === 'AI_DRAFT' ? '#1890ff' : '#fa8c16', fontWeight: 600 }">
              {{ currentReport.reportStatus === 'AI_DRAFT' ? 'AI草稿' : '编辑中' }}
            </span>
            <span style="color:var(--xrag-text-faint);margin-left:8px;font-size:11px">
              {{ currentReport.reportStatus === 'AI_DRAFT' ? '可直接修改内容后签发' : '医生编辑中' }}
            </span>
            <span style="margin-left:auto;font-size:11px;color:#8c8c8c">
              <el-icon style="vertical-align:-2px"><User /></el-icon>
              {{ doctorName }} 负责
            </span>
          </div>

          <div class="field-block">
            <div class="field-label">
              影像所见
              <span class="ai-label"><el-icon><Cpu /></el-icon> AI生成</span>
            </div>
            <el-input v-model="findingsModel" type="textarea" :rows="6"
              placeholder="请输入影像所见，建议描述部位、形态、密度及范围" resize="none" />
          </div>
          <div class="field-block" style="margin-top:12px">
            <div class="field-label">
              影像印象
              <span class="ai-label"><el-icon><Cpu /></el-icon> AI生成</span>
            </div>
            <el-input v-model="impressionModel" type="textarea" :rows="4"
              placeholder="请输入影像印象，概括主要结论及诊断倾向" resize="none" />
          </div>

          <div class="edit-toolbar" style="margin-top:10px;display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <el-button size="small" type="primary" plain :loading="polishing" @click="handlePolish">
              <el-icon><MagicStick /></el-icon> AI 润色
            </el-button>
            <el-button size="small" :loading="termLoading" @click="handleTermNormalize">
              <el-icon><Edit /></el-icon> 术语纠正
            </el-button>
            <span v-if="termLastCount > 0" style="font-size:11px;color:#52c41a">
              已替换 {{ termLastCount }} 处术语
            </span>
          </div>

          <!-- AI审核建议 -->
          <template>
            <el-divider style="margin:10px 0" />
            <el-button size="small" type="warning" plain style="width:100%"
              :loading="aiAdviceLoading" @click="handleGetAiAdvice">
              <el-icon><MagicStick /></el-icon>
              {{ aiAdvice ? '重新获取AI审核建议' : '获取AI审核建议' }}
            </el-button>

            <div v-if="aiAdvice" class="ai-advice-panel" style="margin-top:8px">
              <div class="ai-advice-header">
                <el-icon style="color:#722ed1"><Cpu /></el-icon>
                <span>AI 复核建议</span>
                <span v-if="aiAdvice.priority === 'high'" class="advice-priority-high">⚠ 高优先级</span>
                <span v-else-if="aiAdvice.priority === 'medium'" class="advice-priority-mid">注意</span>
              </div>
              <div class="ai-advice-assessment">{{ aiAdvice.overall_assessment }}</div>
              <div v-if="aiAdvice.key_issues?.length" class="ai-advice-block">
                <div class="ai-advice-label">主要问题</div>
                <ul class="ai-advice-list">
                  <li v-for="(issue, i) in aiAdvice.key_issues" :key="i">{{ issue }}</li>
                </ul>
              </div>
              <div v-if="aiAdvice.check_points?.length" class="ai-advice-block">
                <div class="ai-advice-label">建议核查</div>
                <ul class="ai-advice-list">
                  <li v-for="(pt, i) in aiAdvice.check_points" :key="i">{{ pt }}</li>
                </ul>
              </div>
              <div v-if="aiAdvice.suggested_findings" class="ai-advice-block">
                <div class="ai-advice-label">参考所见</div>
                <div class="ai-advice-text">{{ aiAdvice.suggested_findings }}</div>
                <el-button size="small" link type="primary"
                  @click="handleApplyAdviceFindings">应用此内容</el-button>
              </div>
              <div v-if="aiAdvice.suggested_impression" class="ai-advice-block">
                <div class="ai-advice-label">参考印象</div>
                <div class="ai-advice-text">{{ aiAdvice.suggested_impression }}</div>
                <el-button size="small" link type="primary"
                  @click="handleApplyAdviceImpression">应用此内容</el-button>
              </div>
            </div>
          </template>

          <div v-if="currentReport.modelConfidence" class="confidence-bar">
            <span class="conf-label">AI生成置信度：</span>
            <el-progress
              :percentage="Math.round(currentReport.modelConfidence * 100)"
              :color="confColor(currentReport.modelConfidence)"
              :stroke-width="8" style="flex:1" />
            <span class="conf-value">{{ Math.round(currentReport.modelConfidence * 100) }}%</span>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="历史版本" name="history">
        <div class="history-list" v-loading="historyLoading">
          <div v-for="h in editHistory" :key="h.historyId" class="history-item">
            <div class="history-header">
              <span class="history-version">v{{ h.version || '1' }}</span>
              <span class="history-editor">{{ h.editorName || '—' }}</span>
              <span class="history-time">{{ formatDate(h.editTime) }}</span>
              <el-button size="small" link type="primary"
                v-if="currentReport && currentReport.reportStatus !== 'SIGNED'"
                @click="handleRestoreHistory(h)"
                style="margin-left:auto;font-size:11px">恢复此版本</el-button>
            </div>
            <div class="history-content">
              <div class="history-field">
                <span class="field-label">影像所见：</span>
                <span class="field-text">{{ h.findings || '—' }}</span>
              </div>
              <div class="history-field">
                <span class="field-label">影像印象：</span>
                <span class="field-text">{{ h.impression || '—' }}</span>
              </div>
            </div>
          </div>
          <el-empty v-if="!historyLoading && editHistory.length === 0"
            description="暂无历史版本" :image-size="40" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentReport: { type: Object, default: null },
  reportTab: { type: String, default: 'edit' },
  showAiCompare: { type: Boolean, default: false },
  draftFindings: { type: String, default: '' },
  draftImpression: { type: String, default: '' },
  generating: { type: Boolean, default: false },
  hasCurrentImage: { type: Boolean, default: false },
  polishing: { type: Boolean, default: false },
  termLoading: { type: Boolean, default: false },
  termLastCount: { type: Number, default: 0 },
  aiAdviceLoading: { type: Boolean, default: false },
  aiAdvice: { type: Object, default: null },
  historyLoading: { type: Boolean, default: false },
  editHistory: { type: Array, default: () => [] },
  doctorName: { type: String, default: '当前医生' },
  formatDate: { type: Function, required: true },
  confColor: { type: Function, required: true }
})

const emit = defineEmits([
  'update:reportTab',
  'update:showAiCompare',
  'update:findings',
  'update:impression',
  'generate',
  'polish',
  'term-normalize',
  'get-ai-advice',
  'apply-advice-findings',
  'apply-advice-impression',
  'restore-history'
])

const reportTabModel = computed({
  get: () => props.reportTab,
  set: (val) => emit('update:reportTab', val)
})

const showAiCompareModel = computed({
  get: () => props.showAiCompare,
  set: (val) => emit('update:showAiCompare', val)
})

const findingsModel = computed({
  get: () => props.draftFindings,
  set: (val) => emit('update:findings', val)
})

const impressionModel = computed({
  get: () => props.draftImpression,
  set: (val) => emit('update:impression', val)
})

const handleGenerate = () => emit('generate')
const handlePolish = () => emit('polish')
const handleTermNormalize = () => emit('term-normalize')
const handleGetAiAdvice = () => emit('get-ai-advice')
const handleApplyAdviceFindings = () => emit('apply-advice-findings')
const handleApplyAdviceImpression = () => emit('apply-advice-impression')
const handleRestoreHistory = (h) => emit('restore-history', h)
</script>

<style scoped>
/* ─── 报告编辑器 ─── */
.report-panel {
  flex: 1;
  background: var(--xrag-panel);
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.report-tabs { height: 100%; display: flex; flex-direction: column; }
.report-tabs :deep(.el-tabs__header) { margin: 0; padding: 0 16px; border-bottom: 1px solid var(--xrag-border); flex-shrink: 0; }
.report-tabs :deep(.el-tabs__content) { flex: 1; overflow-y: auto; }
.report-tabs :deep(.el-tab-pane) { height: 100%; }

.no-report {
  display: flex; align-items: center; justify-content: center;
  min-height: 200px;
  padding: 24px 16px;
}
.report-form { padding: 16px; }

/* ─── 状态提示 ─── */
.status-notice {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 12px;
}
.status-ai-draft {
  background: rgba(24, 144, 255, 0.12);
  border: 1px solid rgba(64, 169, 255, 0.28);
  color: #91caff;
}
.status-editing {
  background: rgba(250, 140, 22, 0.12);
  border: 1px solid rgba(255, 169, 64, 0.28);
  color: #ffb86b;
}
.status-signed {
  background: rgba(82, 196, 26, 0.12);
  border: 1px solid rgba(149, 222, 100, 0.28);
  color: #95de64;
}

/* ─── 最终报告标签 ─── */
.final-label {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 400;
  color: #95de64;
  background: rgba(82, 196, 26, 0.12);
  padding: 1px 6px;
  border-radius: 3px;
}

/* ─── AI质量评测快览条 ─── */
.eval-quick-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-top: 10px; padding: 8px 12px;
  background: rgba(47, 84, 235, 0.1); border: 1px solid rgba(133, 165, 255, 0.22); border-radius: 6px;
  font-size: 12px;
}
.eval-quick-bar-relaxed { margin-top: 0; min-height: 42px; }
.eval-grade-badge {
  width: 26px; height: 26px; border-radius: 5px;
  color: #fff; font-size: 13px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.eval-scores-mini { display: flex; gap: 10px; color: var(--xrag-text-soft); }
.eval-scores-mini b { color: var(--xrag-text); }
.eval-advice-text { font-size: 12px; font-weight: 500; }
.advice-A { color: #389e0d; }
.advice-B { color: #52c41a; }
.advice-C { color: #d46b08; }
.advice-D, .advice-F { color: #cf1322; }

.field-block {}
.field-label {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: var(--xrag-text);
  margin-bottom: 6px;
}
.ai-label {
  display: flex; align-items: center; gap: 3px;
  font-size: 11px; font-weight: 400; color: #69b1ff;
  background: rgba(24, 144, 255, 0.14); padding: 1px 6px; border-radius: 3px;
}
.confidence-bar {
  display: flex; align-items: center; gap: 10px;
  margin-top: 14px; padding: 10px 12px;
  background: rgba(255,255,255,0.04); border-radius: 6px; border: 1px solid var(--xrag-border);
}
.conf-label { font-size: 12px; color: var(--xrag-text-soft); white-space: nowrap; }
.conf-value { font-size: 13px; font-weight: 700; color: #52c41a; white-space: nowrap; }

.history-list { padding: 12px 16px; }
.history-item {
  padding: 10px 12px;
  background: rgba(255,255,255,0.03);
  border-radius: 6px;
  border: 1px solid var(--xrag-border);
  margin-bottom: 8px;
}
.history-meta { display: flex; justify-content: space-between; margin-bottom: 4px; }
.history-editor { font-size: 12px; font-weight: 600; color: var(--xrag-text); }
.history-time { font-size: 11px; color: var(--xrag-text-faint); }
.history-note { font-size: 11px; color: var(--xrag-text-soft); margin-bottom: 6px; }
.diff-row { display: flex; gap: 6px; margin-bottom: 3px; }
.diff-label { font-size: 11px; color: var(--xrag-text-faint); white-space: nowrap; }
.diff-text { font-size: 11px; color: var(--xrag-text); line-height: 1.5; }

.dicom-meta { padding: 12px 16px; }
.meta-row {
  display: flex; padding: 8px 0;
  border-bottom: 1px solid var(--xrag-border);
  font-size: 12px;
}
.meta-key { width: 90px; color: var(--xrag-text-faint); flex-shrink: 0; }
.meta-val { color: var(--xrag-text); }

/* ═══════════════════════════════════════════
   AI 评测
═══════════════════════════════════════════ */
.eval-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}
.eval-grade {
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
  text-align: center;
  min-width: 48px;
}
.label-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.label-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  border: 1px solid transparent;
}
.label-positive { background: rgba(245, 34, 45, 0.14); border-color: rgba(255, 120, 117, 0.28); color: #ff9c9c; }
.label-negative { background: rgba(82, 196, 26, 0.12); border-color: rgba(149, 222, 100, 0.28); color: #95de64; }
.label-name { font-weight: 500; }
.label-prob { opacity: 0.75; }

/* ═══════════════════════════════════════════
   术语建议（内嵌报告编辑）
═══════════════════════════════════════════ */
.term-suggestions-panel {
  margin-top: 12px;
  border: 1px solid rgba(255, 214, 102, 0.28);
  border-radius: 6px;
  background: rgba(250, 173, 20, 0.12);
  padding: 8px 10px;
}
.term-suggestions-panel-relaxed {
  margin-top: 0;
}
.term-panel-header {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}
.term-panel-title {
  font-size: 12px;
  font-weight: 600;
  color: #d48806;
  margin-left: 4px;
}
.term-inline-item {
  padding: 5px 0;
  border-bottom: 1px dashed #ffd591;
}
.term-inline-item:last-child { border-bottom: none; }
.term-inline-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 3px;
  font-size: 12px;
}
.term-orig { color: #cf1322; text-decoration: line-through; }
.term-corr { color: #389e0d; font-weight: 600; }
.term-inline-actions { display: flex; gap: 6px; margin-top: 4px; }

/* ═══ 已签发报告视图 ═══ */
.signed-report-view {
  display: flex; flex-direction: column; height: 100%; overflow-y: auto; padding: 0;
  background: var(--xrag-panel);
  border: 1px solid var(--xrag-border);
  border-radius: 8px;
}
.signed-report-view::-webkit-scrollbar { width: 5px; }
.signed-report-view::-webkit-scrollbar-thumb { background: rgba(111,134,166,0.36); border-radius: 3px; }

.signed-banner {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px; background: linear-gradient(135deg, #f6ffed 0%, #e8fce8 100%);
  border-bottom: 2px solid #b7eb8f; flex-shrink: 0;
}
.signed-banner-left { display: flex; align-items: center; gap: 12px; }
.signed-icon-circle {
  width: 36px; height: 36px; border-radius: 50%; background: #52c41a;
  display: flex; align-items: center; justify-content: center; color: #fff;
}
.signed-title { font-size: 15px; font-weight: 700; color: var(--xrag-text); }
.signed-meta { font-size: 11px; color: var(--xrag-text-faint); margin-top: 2px; }
.signed-grade-badge {
  width: 40px; height: 40px; border-radius: 8px; color: #fff; font-size: 22px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
}

.signed-content { padding: 16px 18px; flex-shrink: 0; }
.signed-section { margin-bottom: 14px; }
.signed-section-label {
  font-size: 12px; font-weight: 600; color: var(--xrag-text-soft); margin-bottom: 6px;
  display: flex; align-items: center; gap: 4px;
}
.signed-text {
  font-size: 13px; line-height: 1.7; color: var(--xrag-text); padding: 10px 14px;
  background: rgba(255,255,255,0.03); border: 1px solid var(--xrag-border); border-radius: 6px; white-space: pre-wrap;
}

.signed-ai-compare {
  margin-top: 8px; border: 1px dashed var(--xrag-border-strong); border-radius: 6px; overflow: hidden;
}
.compare-header {
  display: flex; align-items: center; gap: 6px; padding: 8px 14px;
  background: rgba(255,255,255,0.03); cursor: pointer; font-size: 12px; color: var(--xrag-text-faint);
}
.compare-header:hover { background: rgba(74,158,255,0.08); }
.compare-diff-hint {
  font-size: 10px; padding: 1px 6px; background: rgba(250, 140, 22, 0.12); color: #ffb86b;
  border-radius: 3px; border: 1px solid rgba(255, 214, 102, 0.28);
}
.compare-body { padding: 10px 14px; }
.compare-field { margin-bottom: 8px; }
.compare-label { font-size: 11px; color: var(--xrag-text-faint); margin-bottom: 3px; }
.compare-text {
  font-size: 12px; color: var(--xrag-text-soft); line-height: 1.6; padding: 6px 10px;
  background: rgba(255,255,255,0.04); border-radius: 4px; font-style: italic; white-space: pre-wrap;
}

.signed-eval-section {
  padding: 14px 18px; border-top: 1px solid var(--xrag-border); flex-shrink: 0;
}
.signed-eval-header {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: var(--xrag-text); margin-bottom: 12px;
}
.signed-eval-metrics {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 10px;
}
.metric-card {
  text-align: center; padding: 10px 4px; background: rgba(255,255,255,0.03);
  border: 1px solid var(--xrag-border); border-radius: 6px;
}
.metric-value { font-size: 18px; font-weight: 700; line-height: 1; }
.metric-label { font-size: 10px; color: var(--xrag-text-faint); margin-top: 4px; }
.signed-eval-advice {
  font-size: 12px; padding: 6px 12px; border-radius: 5px; margin-bottom: 8px;
}
.signed-eval-advice.advice-A { background: rgba(82, 196, 26, 0.12); color: #95de64; }
.signed-eval-advice.advice-B { background: rgba(24, 144, 255, 0.12); color: #91caff; }
.signed-eval-advice.advice-C { background: rgba(250, 173, 20, 0.12); color: #ffd666; }
.signed-eval-advice.advice-D { background: rgba(245, 34, 45, 0.14); color: #ff9c9c; }

/* ─── AI 审核建议面板 ─── */
.ai-advice-panel {
  background: rgba(114, 46, 209, 0.1);
  border: 1px solid rgba(211, 173, 247, 0.28);
  border-radius: 8px;
  padding: 12px 14px;
  margin-top: 10px;
  font-size: 12px;
}
.ai-advice-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #d3adf7;
  margin-bottom: 8px;
}
.advice-priority-high {
  margin-left: auto;
  background: rgba(245, 34, 45, 0.14);
  color: #ff9c9c;
  border: 1px solid rgba(255, 120, 117, 0.28);
  border-radius: 10px;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 500;
}
.advice-priority-mid {
  margin-left: auto;
  background: rgba(250, 173, 20, 0.12);
  color: #ffd666;
  border: 1px solid rgba(255, 214, 102, 0.28);
  border-radius: 10px;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 500;
}
.ai-advice-assessment {
  color: var(--xrag-text-soft);
  margin-bottom: 8px;
  line-height: 1.6;
}
.ai-advice-block { margin-bottom: 8px; }
.ai-advice-label {
  font-weight: 600;
  color: #d3adf7;
  margin-bottom: 4px;
}
.ai-advice-list {
  margin: 0 0 4px 14px;
  padding: 0;
  color: var(--xrag-text-soft);
  line-height: 1.7;
}
.ai-advice-text {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(211,173,247,0.28);
  border-radius: 5px;
  padding: 8px 10px;
  color: var(--xrag-text);
  line-height: 1.7;
  margin-bottom: 4px;
  white-space: pre-wrap;
}

.signed-label-row {
  display: flex; align-items: center; flex-wrap: wrap; gap: 2px;
  margin-bottom: 4px; font-size: 12px;
}
.label-row-title { font-weight: 600; margin-right: 4px; }
.label-row-title.danger { color: #f56c6c; }
.label-row-title.warning { color: #e6a23c; }

/* ─── 危急值预警行 ─── */
.alert-item-row {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px; border-radius: 5px; margin-bottom: 5px;
  font-size: 12px;
}
.alert-pending  { background: rgba(245, 34, 45, 0.14); border: 1px solid rgba(255, 120, 117, 0.24); }
.alert-resolved { background: rgba(82, 196, 26, 0.12); border: 1px solid rgba(149, 222, 100, 0.24); }
.alert-label { font-weight: 600; flex: 1; }
.alert-prob  { color: var(--xrag-text-soft); }
.alert-note  { color: var(--xrag-text-faint); font-size: 11px; }
</style>
