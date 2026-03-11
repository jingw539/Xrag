<template>
  <div class="anno-list-panel" v-if="visibleAnnotations.length > 0">
    <div class="anno-list-header">
      <el-icon style="color:#faad14;font-size:13px"><Flag /></el-icon>
      <span>标注列表</span>
      <span class="anno-count">{{ totalCount }} 处</span>
      <span class="anno-hint">虚线=AI · 实线=医生</span>
    </div>
    <div class="anno-list-body">
      <div
        v-for="anno in visibleAnnotations"
        :key="anno.annotationId"
        :class="['anno-list-item', selectedAnnoId === anno.annotationId && 'anno-item-selected']"
        :title="formatAnnoMeasurement(anno)"
        @click="handleSelect(anno)"
      >
        <span class="anno-color-dot" :style="{ background: anno.color }"></span>
        <span :class="['anno-src-tag', anno.source === 'AI' ? 'tag-ai' : 'tag-dr']">
          {{ anno.source === 'AI' ? 'AI' : '医' }}
        </span>
        <span class="anno-lbl">{{ anno.label || '—' }}</span>
        <span class="anno-size">{{ formatAnnoMeasurement(anno) }}</span>
        <span v-if="anno.confidence != null" class="anno-conf">{{ Math.round(anno.confidence * 100) }}%</span>
        <button v-if="anno.source === 'DOCTOR'" class="anno-del-btn" @click.stop="handleDelete(anno.annotationId)">×</button>
      </div>
    </div>
  </div>
</template>

<script setup>
const emit = defineEmits(['select', 'delete'])

defineProps({
  visibleAnnotations: { type: Array, default: () => [] },
  totalCount: { type: Number, default: 0 },
  selectedAnnoId: { type: [Number, String], default: null },
  formatAnnoMeasurement: { type: Function, required: true }
})

const handleSelect = (anno) => emit('select', anno)
const handleDelete = (id) => emit('delete', id)
</script>

<style scoped>
.anno-list-panel {
  flex-shrink: 0;
  border-top: 1px solid rgba(255,255,255,0.07);
  background: rgba(0,0,0,0.3);
  max-height: 140px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.anno-list-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  font-size: 11px;
  font-weight: 600;
  color: rgba(255,255,255,0.7);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.anno-count { color: #40a9ff; margin-left: 2px; }
.anno-hint { font-size: 10px; font-weight: 400; color: rgba(255,255,255,0.35); margin-left: auto; }
.anno-list-body { overflow-y: auto; padding: 2px 0; }
.anno-list-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 11px;
  color: rgba(255,255,255,0.75);
  transition: background 0.15s;
}
.anno-list-item:hover { background: rgba(255,255,255,0.06); }
.anno-item-selected { background: rgba(24,144,255,0.18) !important; }
.anno-color-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.anno-src-tag {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 3px;
  flex-shrink: 0;
}
.tag-ai { background: rgba(24,144,255,0.25); color: #69c0ff; }
.tag-dr { background: rgba(82,196,26,0.25); color: #95de64; }
.anno-lbl { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.anno-size { font-size: 10px; color: rgba(255,255,255,0.42); flex-shrink: 0; }
.anno-conf { font-size: 10px; color: rgba(255,255,255,0.4); flex-shrink: 0; }
.anno-del-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.3);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 2px;
  flex-shrink: 0;
  transition: color 0.15s;
}
.anno-del-btn:hover { color: #ff7875; }
</style>
