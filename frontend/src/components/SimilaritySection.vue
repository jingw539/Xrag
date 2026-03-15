<template>
  <div class="similar-section">
    <div class="section-title">
      <el-icon class="section-icon"><Search /></el-icon>
      相似病例检索
      <span class="section-sub" v-if="similarCases.length">Top-{{ similarCases.length }} 结果</span>
    </div>

    <div v-if="similarCases.length" class="similar-cards">
      <div
        v-for="(sc, i) in similarCases"
        :key="sc.caseId || i"
        :class="['similar-card', isReadOnly(sc) && 'is-readonly', isExternalRag(sc) && 'is-external']"
        @click="handleSelect(sc)"
      >
        <div class="similar-info">
          <span :class="['sim-score', i === 0 ? 'score-high' : i === 1 ? 'score-mid' : 'score-low']">
            相似度 {{ formatScore(sc) }}
          </span>
          <span class="sim-source" :class="sourceClass(sc)">{{ sourceText(sc) }}</span>
          <span v-if="isReadOnly(sc)" class="sim-readonly">只读</span>
          <span v-if="isExternalRag(sc)" class="sim-external">外部RAG</span>
          <div class="sim-exam">{{ sc.examNo || ('病例 ' + (sc.caseId || '')) }}</div>
          <div class="sim-findings">{{ sc.findings || sc.impression || sc.report || '暂无返回文本' }}</div>
        </div>
        <div v-if="resolveImageUrl(sc)" class="sim-media">
          <img
            class="sim-thumb"
            :src="resolveImageUrl(sc)"
            alt="RAG 影像"
            loading="lazy"
            referrerpolicy="no-referrer"
            @error="onThumbError"
          />
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无相似病例结果" :image-size="60" />
  </div>

  <el-dialog v-model="detailVisible" title="RAG 外部病例" width="560px">
    <div v-if="detailItem" class="rag-detail">
      <div class="rag-meta">来源ID：{{ detailItem.source_id || detailItem.sourceId || '未知' }}</div>
      <img
        v-if="resolveImageUrl(detailItem)"
        class="rag-preview"
        :src="resolveImageUrl(detailItem)"
        alt="RAG 影像"
        loading="lazy"
        referrerpolicy="no-referrer"
      />
      <div class="rag-block">
        <div class="rag-label">所见</div>
        <div class="rag-text">{{ detailItem.findings || '暂无' }}</div>
      </div>
      <div class="rag-block">
        <div class="rag-label">印象</div>
        <div class="rag-text">{{ detailItem.impression || '暂无' }}</div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
const emit = defineEmits(['select'])

const props = defineProps({
  similarCases: { type: Array, default: () => [] },
  currentDoctorId: { type: [String, Number], default: '' },
  allowAll: { type: Boolean, default: false }
})

const detailVisible = ref(false)
const detailItem = ref(null)

const minioBaseUrl = (import.meta.env.VITE_MINIO_PUBLIC_BASE || 'http://111.229.72.224:9000/cxr-images').replace(/\/$/, '')

const getCaseDoctorId = (sc) => (
  sc?.doctorId ?? sc?.reportDoctorId ?? sc?.ownerId ?? sc?.caseDoctorId ?? sc?.userId ?? null
)
const isReadOnly = (sc) => {
  if (props.allowAll) return false
  const caseDoctorId = getCaseDoctorId(sc)
  if (!props.currentDoctorId || !caseDoctorId) return false
  return String(caseDoctorId) !== String(props.currentDoctorId)
}

const sourceText = (sc) => {
  const s = (sc?.source || '').toUpperCase()
  if (!s) return '来源 未知'
  if (s === 'RAG') return '来源 RAG'
  if (s === 'IMAGE') return '来源 图像检索'
  if (s === 'TYPICAL') return '来源 典型库回退'
  if (s === 'MIXED') return '来源 混合'
  return '来源 ' + s
}
const sourceClass = (sc) => {
  const s = (sc?.source || '').toUpperCase()
  if (s === 'RAG') return 'src-rag'
  if (s === 'IMAGE') return 'src-image'
  if (s === 'TYPICAL') return 'src-typical'
  if (s === 'MIXED') return 'src-mixed'
  return 'src-unknown'
}

const resolveImageUrl = (sc) => {
  const raw = sc?.image_path || sc?.imagePath || sc?.image || ''
  if (raw) {
    if (/^https?:\/\//i.test(raw)) return raw
    if (!/^[a-zA-Z]:\\/.test(raw) && !raw.startsWith('/')) {
      return `${minioBaseUrl}/${raw.replace(/^\//, '')}`
    }
  }
  const sid = sc?.source_id || sc?.sourceId
  if (sid) return `${minioBaseUrl}/rag/${sid}.jpg`
  return ''
}

const isExternalRag = (sc) => {
  const source = (sc?.source || '').toUpperCase()
  return source === 'RAG'
}

const formatScore = (sc) => {
  const val = sc?.similarityScore ?? sc?.score
  if (val == null || isNaN(Number(val))) return '—'
  return `${Math.round(Number(val) * 100)}%`
}

const onThumbError = (e) => {
  if (e?.target) e.target.style.display = 'none'
}

const handleSelect = (sc) => {
  if (isExternalRag(sc)) {
    detailItem.value = sc
    detailVisible.value = true
    return
  }
  emit('select', sc?.caseId)
}

</script>

<style scoped>
.similar-section { flex-shrink: 0; }
.section-title { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: var(--xrag-text); margin-bottom: 8px; }
.section-icon { color: #1890ff; }
.section-sub { font-size: 11px; font-weight: 400; color: var(--xrag-text-faint); }
.similar-cards { display: flex; gap: 10px; }
.similar-card {
  flex: 1; background: var(--xrag-panel); border-radius: 6px;
  border: 1px solid var(--xrag-border); overflow: hidden;
  display: flex; flex-direction: column;
  transition: border-color .15s, box-shadow .15s;
  cursor: pointer;
}
.similar-card:hover { border-color: var(--xrag-primary); box-shadow: 0 6px 18px rgba(74,158,255,.16); }
.similar-card.is-readonly { border-style: dashed; }
.similar-info { padding: 10px; }
.sim-score { font-size: 11px; font-weight: 700; }
.score-high { color: #52c41a; }
.score-mid  { color: #fa8c16; }
.score-low  { color: #1890ff; }
.sim-source {
  margin-left: 6px;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  border: 1px solid var(--xrag-border);
  color: var(--xrag-text-faint);
}
.sim-source.src-rag { color: #69c0ff; border-color: rgba(105,192,255,0.45); }
.sim-source.src-image { color: #b7eb8f; border-color: rgba(183,235,143,0.45); }
.sim-source.src-typical { color: #ffd666; border-color: rgba(255,214,102,0.45); }
.sim-source.src-mixed { color: #d3adf7; border-color: rgba(211,173,247,0.45); }
.sim-source.src-unknown { color: var(--xrag-text-faint); }
.sim-readonly {
  margin-left: 6px;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  color: #8cc8ff;
  border: 1px dashed rgba(140, 200, 255, 0.45);
  background: rgba(140, 200, 255, 0.08);
}
.sim-external {
  margin-left: 6px;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  color: #ffd666;
  border: 1px dashed rgba(255, 214, 102, 0.45);
  background: rgba(255, 214, 102, 0.08);
}
.sim-exam { font-size: 11px; color: var(--xrag-text-soft); margin: 2px 0; }
.sim-findings { font-size: 10px; color: var(--xrag-text-faint); line-height: 1.4; }
.sim-media {
  border-top: 1px dashed var(--xrag-border);
  background: rgba(0, 0, 0, 0.18);
  padding: 6px;
}
.sim-thumb {
  width: 100%;
  height: 96px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--xrag-border);
  display: block;
}
.rag-detail { display: flex; flex-direction: column; gap: 10px; }
.rag-meta { font-size: 12px; color: var(--xrag-text-faint); }
.rag-preview {
  width: 100%;
  max-height: 260px;
  object-fit: contain;
  border-radius: 8px;
  border: 1px solid var(--xrag-border);
  background: rgba(0, 0, 0, 0.2);
}
.rag-block { display: flex; flex-direction: column; gap: 4px; }
.rag-label { font-size: 12px; color: var(--xrag-text-soft); }
.rag-text { font-size: 12px; color: var(--xrag-text); line-height: 1.5; }
</style>

