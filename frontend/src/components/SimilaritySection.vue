<template>
  <div class="similar-section" v-if="similarCases.length > 0">
    <div class="section-title">
      <el-icon style="color:#1890ff"><Search /></el-icon>
      相似病例检索 <span class="section-sub">Top-{{ similarCases.length }} 相似结果</span>
    </div>
    <div class="similar-cards">
      <div
        v-for="(sc, i) in similarCases"
        :key="sc.caseId"
        class="similar-card"
        style="cursor:pointer"
        @click="handleSelect(sc.caseId)"
      >
        <div class="similar-img-placeholder">
          <img v-if="sc.thumbnailUrl" :src="sc.thumbnailUrl"
            style="width:100%;height:100%;object-fit:cover;border-radius:4px" />
          <el-icon v-else :size="24" style="color:rgba(255,255,255,0.2)"><Picture /></el-icon>
        </div>
        <div class="similar-info">
          <span :class="['sim-score', i === 0 ? 'score-high' : i === 1 ? 'score-mid' : 'score-low']">
            相似度 {{ Math.round((sc.similarityScore || 0) * 100) }}%
          </span>
          <div class="sim-exam">{{ sc.examNo || ('病例 ' + sc.caseId) }}</div>
          <div class="sim-findings">{{ sc.findings || sc.impression || '—' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const emit = defineEmits(['select'])

defineProps({
  similarCases: { type: Array, default: () => [] }
})

const handleSelect = (caseId) => emit('select', caseId)
</script>

<style scoped>
.similar-section {
  flex-shrink: 0;
}
.section-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: var(--xrag-text);
  margin-bottom: 8px;
}
.section-sub { font-size: 11px; font-weight: 400; color: var(--xrag-text-faint); }

.similar-cards { display: flex; gap: 10px; }
.similar-card {
  flex: 1; background: var(--xrag-panel); border-radius: 6px;
  border: 1px solid var(--xrag-border); overflow: hidden;
  display: flex; flex-direction: column;
  transition: border-color .15s, box-shadow .15s;
}
.similar-card:hover {
  border-color: var(--xrag-primary);
  box-shadow: 0 6px 18px rgba(74,158,255,.16);
}
.similar-img-placeholder {
  height: 70px; background: #1a2030;
  display: flex; align-items: center; justify-content: center;
}
.similar-info { padding: 8px 10px; }
.sim-score { font-size: 11px; font-weight: 700; }
.score-high { color: #52c41a; }
.score-mid  { color: #fa8c16; }
.score-low  { color: #1890ff; }
.sim-exam { font-size: 11px; color: var(--xrag-text-soft); margin: 2px 0; }
.sim-findings { font-size: 10px; color: var(--xrag-text-faint); line-height: 1.4; }
</style>
