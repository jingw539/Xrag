<template>
  <aside class="case-panel">
    <div class="panel-header">
      <span class="panel-title">待处理病例</span>
      <div class="panel-actions">
        <el-badge :value="caseTotal" :max="99" class="count-badge" type="danger" />
        <el-tooltip v-if="isAdmin" content="批量导入" placement="right">
          <el-upload :show-file-list="false" :before-upload="handleImport" accept=".xlsx,.xls,.csv">
            <button class="add-btn"><el-icon><Upload /></el-icon></button>
          </el-upload>
        </el-tooltip>
        <el-tooltip content="新建病例" placement="right">
          <button class="add-btn" @click="handleCreate"><el-icon><Plus /></el-icon></button>
        </el-tooltip>
      </div>
    </div>

    <div class="panel-search">
      <el-input
        :model-value="searchKeyword"
        placeholder="搜索病例号 / 患者ID"
        prefix-icon="Search"
        clearable
        size="small"
        @input="handleSearchInput"
      />
    </div>

    <div class="panel-filters">
      <button
        v-for="f in statusFilters"
        :key="f.value"
        :class="['filter-btn', activeFilter === f.value && 'active-' + f.color]"
        @click="handleSetFilter(f.value)"
      >
        {{ f.label }}
      </button>
    </div>

    <div v-if="batchGenerating" class="batch-progress-bar">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>批量生成中 {{ batchProgress.current }}/{{ batchProgress.total }}</span>
      <el-progress
        :percentage="Math.round((batchProgress.current / batchProgress.total) * 100)"
        :stroke-width="6"
        class="batch-progress"
      />
      <el-button size="small" type="danger" text @click="handleCancelBatch">取消</el-button>
    </div>
    <div v-else-if="noneCount > 0 && (activeFilter === 'NONE' || !activeFilter)" class="batch-bar">
      <el-button size="small" type="primary" @click="handleBatchGenerate">
        <el-icon><VideoPlay /></el-icon> 批量生成AI报告（{{ noneCount }}例）
      </el-button>
    </div>

    <div class="case-list" v-loading="listLoading" element-loading-background="rgba(13,20,32,0.72)">
      <div
        v-for="c in caseList"
        :key="c.caseId"
        :class="['case-card', selectedCaseId === c.caseId && 'selected']"
        @click="handleSelectCase(c)"
      >
        <div class="card-top">
          <span class="exam-no">{{ c.examNo }}</span>
          <span :class="['status-badge', 'badge-' + statusColor(c.reportStatus)]">
            {{ statusLabel(c.reportStatus) }}
          </span>
        </div>
        <div class="card-info">
          {{ genderLabel(c.gender) }} · {{ c.age }}岁 · {{ c.department || '—' }}
        </div>
        <div class="card-meta">
          <template v-if="!c.reportStatus || c.reportStatus === 'NONE'">
            <span class="meta-tag meta-tag-body">{{ c.bodyPart || '胸部' }}</span>
          </template>
          <template v-else-if="c.reportStatus === 'AI_DRAFT'">
            <span class="meta-tag meta-tag-ai">
              AI {{ c.modelConfidence ? Math.round(c.modelConfidence * 100) + '%' : '草稿' }}
            </span>
          </template>
          <template v-else-if="c.reportStatus === 'EDITING'">
            <span class="meta-tag meta-tag-edit">
              {{ c.lastEditTime ? '编辑于 ' + formatTime(c.lastEditTime) : '编辑中' }}
            </span>
          </template>
          <template v-else-if="c.reportStatus === 'SIGNED'">
            <span v-if="c.qualityGrade" :class="['meta-grade', 'grade-' + c.qualityGrade]">{{ c.qualityGrade }}</span>
            <span class="meta-text">{{ c.signTime ? formatTime(c.signTime) : '已签发' }}</span>
          </template>
        </div>
        <div class="card-time">{{ formatTime(c.examTime) }}</div>
      </div>
      <el-empty
        v-if="!listLoading && caseList.length === 0"
        description="暂无病例"
        :image-size="50"
        class="case-empty"
      />
      <div v-if="hasMore" class="load-more" @click="handleLoadMore">加载更多</div>
    </div>
  </aside>
</template>

<script setup>
const emit = defineEmits([
  'search-input',
  'set-filter',
  'select-case',
  'batch-generate',
  'load-more',
  'create',
  'import',
  'cancel-batch'
])

defineProps({
  caseTotal: { type: Number, default: 0 },
  isAdmin: { type: Boolean, default: false },
  searchKeyword: { type: String, default: '' },
  statusFilters: { type: Array, default: () => [] },
  activeFilter: { type: String, default: '' },
  batchGenerating: { type: Boolean, default: false },
  batchProgress: { type: Object, default: () => ({ current: 0, total: 0 }) },
  noneCount: { type: Number, default: 0 },
  caseList: { type: Array, default: () => [] },
  listLoading: { type: Boolean, default: false },
  selectedCaseId: { type: [Number, String], default: null },
  hasMore: { type: Boolean, default: false },
  statusColor: { type: Function, required: true },
  statusLabel: { type: Function, required: true },
  genderLabel: { type: Function, required: true },
  formatTime: { type: Function, required: true }
})

const handleSearchInput = (value) => emit('search-input', value)
const handleSetFilter = (value) => emit('set-filter', value)
const handleSelectCase = (value) => emit('select-case', value)
const handleBatchGenerate = () => emit('batch-generate')
const handleLoadMore = () => emit('load-more')
const handleCreate = () => emit('create')
const handleCancelBatch = () => emit('cancel-batch')
const handleImport = (file) => {
  emit('import', file)
  return false
}
</script>

<style scoped>
.case-panel {
  width: 240px;
  flex-shrink: 0;
  background: var(--xrag-panel);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--xrag-border);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  flex-shrink: 0;
}

.panel-title { font-size: 14px; font-weight: 600; color: var(--xrag-text); }
.panel-actions { display: flex; align-items: center; gap: 6px; }

.count-badge :deep(.el-badge__content) { font-size: 10px; height: 16px; line-height: 16px; padding: 0 5px; }

.panel-search { padding: 0 12px 10px; flex-shrink: 0; }
.panel-search :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--xrag-border);
  box-shadow: none;
}
.panel-search :deep(.el-input__inner) { color: var(--xrag-text); font-size: 12px; }
.panel-search :deep(.el-input__inner::placeholder) { color: var(--xrag-text-faint); }

.add-btn {
  width: 22px; height: 22px;
  border-radius: 4px;
  background: rgba(64,169,255,0.12);
  border: 1px solid rgba(64,169,255,0.25);
  color: #40a9ff;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; font-size: 13px;
  transition: all .15s; flex-shrink: 0;
}
.add-btn:hover { background: rgba(64,169,255,0.25); }

.panel-filters {
  display: flex;
  gap: 6px;
  padding: 0 12px 10px;
  flex-shrink: 0;
}
.filter-btn {
  flex: 1;
  padding: 3px 0;
  border: 1px solid var(--xrag-border-strong);
  background: transparent;
  border-radius: 4px;
  font-size: 11px;
  color: var(--xrag-text-soft);
  cursor: pointer;
  transition: all .2s;
}
.filter-btn:hover { color: #1890ff; border-color: #1890ff; }
.active-orange { background: rgba(250,140,22,0.2) !important; border-color: #fa8c16 !important; color: #fa8c16 !important; }
.active-blue   { background: rgba(24,144,255,0.2) !important; border-color: #1890ff !important; color: #1890ff !important; }
.active-green  { background: rgba(82,196,26,0.2)  !important; border-color: #52c41a !important; color: #52c41a  !important; }

.batch-bar {
  padding: 6px 12px;
  flex-shrink: 0;
}
.batch-bar .el-button { width: 100%; }
.batch-progress-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(24, 144, 255, 0.12);
  border-bottom: 1px solid rgba(64, 169, 255, 0.3);
  font-size: 12px;
  color: #69b1ff;
  flex-shrink: 0;
}
.batch-progress { flex: 1; margin: 0 8px; }
.case-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}
.case-list::-webkit-scrollbar { width: 4px; }
.case-list::-webkit-scrollbar-track { background: transparent; }
.case-list::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.12); border-radius: 2px; }

.case-card {
  padding: 10px 10px;
  border-radius: 6px;
  margin-bottom: 4px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all .15s;
}
.case-card:hover { background: rgba(255,255,255,0.04); border-color: var(--xrag-border-strong); }
.case-card.selected { background: rgba(64,169,255,0.12); border-color: #40a9ff; }

.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.exam-no { font-size: 13px; font-weight: 600; color: var(--xrag-text); }

.status-badge {
  font-size: 10px; padding: 1px 6px; border-radius: 3px; font-weight: 500;
}
.badge-orange { background: rgba(250,140,22,0.2); color: #fa8c16; }
.badge-blue   { background: rgba(24,144,255,0.2); color: #69b1ff; }
.badge-green  { background: rgba(82,196,26,0.2);  color: #73d13d; }
.badge-gray   { background: rgba(111,134,166,0.16); color: var(--xrag-text-faint); }

.card-info { font-size: 11px; color: var(--xrag-text-soft); margin-bottom: 3px; }
.card-meta { font-size: 11px; color: var(--xrag-text-soft); margin-bottom: 3px; display: flex; align-items: center; gap: 6px; }
.meta-text { color: var(--xrag-text-soft); }
.meta-tag { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 10px; line-height: 16px; }
.meta-tag-body { background: rgba(89, 126, 247, 0.16); color: #8fb0ff; }
.meta-tag-ai { background: rgba(24, 144, 255, 0.16); color: #69b1ff; font-weight: 600; }
.meta-tag-edit { background: rgba(250, 140, 22, 0.16); color: #ffb86b; }
.meta-grade { display: inline-block; width: 18px; height: 18px; border-radius: 4px; text-align: center; line-height: 18px; font-size: 10px; font-weight: 700; color: #fff; }
.grade-A { background: #52c41a; }
.grade-B { background: #73d13d; }
.grade-C { background: #faad14; }
.grade-D { background: #ff7875; }
.grade-F { background: #f5222d; }

.case-empty { padding: 30px 0; color: var(--xrag-text-faint); }

@media (max-width: 1200px) {
  .case-panel {
    width: 210px;
  }
}

@media (max-width: 1024px) {
  .case-panel {
    width: 100%;
    height: 260px;
    border-right: none;
    border-bottom: 1px solid var(--xrag-border);
  }
}

@media (max-width: 768px) {
  .panel-filters {
    flex-wrap: wrap;
  }

  .panel-filters .filter-btn {
    flex: 0 0 calc(33.33% - 6px);
  }
}
</style>
